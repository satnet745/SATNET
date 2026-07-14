/**
 * Copyright (C) 2025 SATNET AFRICA
 *
 * This file is part of SATNET AFRICA (http://satnetafrica.org)
 *
 * SATNET AFRICA is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.servalproject.bitcoin.blockchain;

import android.util.Log;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bitcoin Transaction Builder for SATNET peer-to-peer transfers
 *
 * Features:
 * - UTXO selection and transaction creation
 * - Fee calculation and estimation
 * - Transaction signing
 * - Change output handling
 */
public class BitcoinTransactionBuilder {
    private static final String TAG = "BitcoinTxBuilder";

    private final NetworkParameters networkParams;
    private final EsploraApiClient apiClient;

    public BitcoinTransactionBuilder(NetworkParameters networkParams, EsploraApiClient apiClient) {
        this.networkParams = networkParams;
        this.apiClient = apiClient;
    }

    /**
     * Transaction creation result
     */
    public static class TransactionResult {
        public final Transaction transaction;
        public final long fee;
        public final long changeAmount;
        public final List<EsploraApiClient.Utxo> selectedUtxos;

        public TransactionResult(Transaction transaction, long fee, long changeAmount, List<EsploraApiClient.Utxo> selectedUtxos) {
            this.transaction = transaction;
            this.fee = fee;
            this.changeAmount = changeAmount;
            this.selectedUtxos = selectedUtxos;
        }

        public String getHex() {
            return bytesToHex(transaction.bitcoinSerialize());
        }
    }

    /**
     * Create a Bitcoin transaction
     */
    public TransactionResult createTransaction(
            List<EsploraApiClient.Utxo> availableUtxos,
            String recipientAddress,
            long amountSats,
            String changeAddress,
            long feeRateSatPerVbyte) throws Exception {

        if (availableUtxos.isEmpty()) {
            throw new IllegalArgumentException("No UTXOs available for transaction");
        }

        Address recipient = Address.fromString(networkParams, recipientAddress);
        Address change = Address.fromString(networkParams, changeAddress);

        // Select UTXOs using largest-first strategy for privacy
        List<EsploraApiClient.Utxo> selectedUtxos = selectUtxos(availableUtxos, amountSats, feeRateSatPerVbyte);

        if (selectedUtxos.isEmpty()) {
            throw new IllegalArgumentException("Insufficient funds for transaction");
        }

        // Calculate total input amount
        long totalInput = 0L;
        for (EsploraApiClient.Utxo utxo : selectedUtxos) {
            totalInput += utxo.value;
        }

        // Estimate transaction size for fee calculation
        int estimatedVsize = estimateTransactionVsize(selectedUtxos.size(), 2); // 2 outputs: recipient + change
        long estimatedFee = estimatedVsize * feeRateSatPerVbyte;

        // Check if we have enough for amount + fee
        if (totalInput < amountSats + estimatedFee) {
            throw new IllegalArgumentException("Insufficient funds including fees");
        }

        // Calculate change amount
        long changeAmount = totalInput - amountSats - estimatedFee;
        if (changeAmount < 0) {
            // Need to adjust fee if change would be dust
            changeAmount = 0;
            estimatedFee = totalInput - amountSats;
        }

        // Create transaction
        Transaction tx = new Transaction(networkParams);

        // Add inputs
        for (EsploraApiClient.Utxo utxo : selectedUtxos) {
            TransactionInput input = new TransactionInput(networkParams, tx, new byte[0], utxo.getOutPoint());
            tx.addInput(input);
        }

        // Add recipient output
        TransactionOutput recipientOutput = new TransactionOutput(networkParams, tx, Coin.valueOf(amountSats),
                recipient.getHash());
        tx.addOutput(recipientOutput);

        // Add change output if needed
        if (changeAmount > 0) {
            TransactionOutput changeOutput = new TransactionOutput(networkParams, tx, Coin.valueOf(changeAmount),
                    change.getHash());
            tx.addOutput(changeOutput);
        }

        Log.d(TAG, String.format("Created transaction: inputs=%d, outputs=%d, amount=%d sats, fee=%d sats",
                selectedUtxos.size(), tx.getOutputs().size(), amountSats, estimatedFee));

        return new TransactionResult(tx, estimatedFee, changeAmount, selectedUtxos);
    }

    /**
     * Sign transaction with private keys
     */
    public void signTransaction(TransactionResult txResult, List<ECKey> privateKeys) throws Exception {
        Transaction tx = txResult.transaction;
        Map<String, ECKey> keyByAddress = buildKeyIndex(privateKeys);

        for (int i = 0; i < tx.getInputs().size(); i++) {
            TransactionInput input = tx.getInput(i);
            EsploraApiClient.Utxo utxo = txResult.selectedUtxos.get(i);

            Script scriptPubKey = new Script(hexToBytes(utxo.scriptPubKey));
            Address utxoAddress;
            try {
                utxoAddress = scriptPubKey.getToAddress(networkParams, true);
            } catch (ScriptException scriptError) {
                throw new IllegalArgumentException("Unsupported UTXO script type for " + utxo.txid + ":" + utxo.vout, scriptError);
            }

            ECKey privateKey = keyByAddress.get(utxoAddress.toString());
            if (privateKey == null) {
                throw new IllegalArgumentException("Private key not found for UTXO: " + utxo.txid + ":" + utxo.vout);
            }

            if (utxoAddress.getOutputScriptType() == Script.ScriptType.P2WPKH) {
                Script scriptCode = ScriptBuilder.createP2PKHOutputScript(privateKey);
                TransactionSignature sig = tx.calculateWitnessSignature(
                        i,
                        privateKey,
                        null,
                        scriptCode.getProgram(),
                        Coin.valueOf(utxo.value),
                        Transaction.SigHash.ALL,
                        false);
                TransactionWitness witness = new TransactionWitness(2);
                witness.setPush(0, sig.encodeToBitcoin());
                witness.setPush(1, privateKey.getPubKey());
                input.setWitness(witness);
                input.setScriptSig(new ScriptBuilder().build());
            } else {
                TransactionSignature sig = tx.calculateSignature(
                        i,
                        privateKey,
                        ScriptBuilder.createP2PKHOutputScript(privateKey).getProgram(),
                        Transaction.SigHash.ALL,
                        false);
                Script inputScript = new ScriptBuilder()
                        .data(sig.encodeToBitcoin())
                        .data(privateKey.getPubKey())
                        .build();
                input.setScriptSig(inputScript);
            }
        }

        Log.d(TAG, "Transaction signed successfully");
    }

    /**
     * Select UTXOs for transaction (largest first for privacy)
     */
    private List<EsploraApiClient.Utxo> selectUtxos(List<EsploraApiClient.Utxo> availableUtxos,
                                                   long targetAmount, long feeRateSatPerVbyte) {
        // Sort by value descending (largest first)
        List<EsploraApiClient.Utxo> sortedUtxos = new ArrayList<>(availableUtxos);
        Collections.sort(sortedUtxos, new Comparator<EsploraApiClient.Utxo>() {
            @Override
            public int compare(EsploraApiClient.Utxo left, EsploraApiClient.Utxo right) {
                return Long.compare(right.value, left.value);
            }
        });

        List<EsploraApiClient.Utxo> selected = new ArrayList<>();
        long totalSelected = 0;

        // Keep adding UTXOs until we have enough (including estimated fees)
        for (EsploraApiClient.Utxo utxo : sortedUtxos) {
            selected.add(utxo);
            totalSelected += utxo.value;

            // Estimate fee for current selection
            int estimatedVsize = estimateTransactionVsize(selected.size(), 2);
            long estimatedFee = estimatedVsize * feeRateSatPerVbyte;

            if (totalSelected >= targetAmount + estimatedFee) {
                break;
            }
        }

        // Verify final selection has enough funds
        int finalVsize = estimateTransactionVsize(selected.size(), 2);
        long finalFee = finalVsize * feeRateSatPerVbyte;

        if (totalSelected < targetAmount + finalFee) {
            return Collections.emptyList(); // Insufficient funds
        }

        return selected;
    }

    /**
     * Estimate transaction virtual size
     */
    private int estimateTransactionVsize(int numInputs, int numOutputs) {
        // Rough estimation:
        // - Each input: ~148 vbytes (compressed pubkey)
        // - Each output: ~34 vbytes (P2WPKH)
        // - Overhead: ~10 vbytes
        return numInputs * 148 + numOutputs * 34 + 10;
    }

    private Map<String, ECKey> buildKeyIndex(List<ECKey> privateKeys) {
        Map<String, ECKey> keyByAddress = new HashMap<>();
        for (ECKey privateKey : privateKeys) {
            ECKey signingKey = ECKey.fromPrivate(privateKey.getPrivKey(), true);
            Address segwitAddress = Address.fromKey(networkParams, signingKey, Script.ScriptType.P2WPKH);
            keyByAddress.put(segwitAddress.toString(), privateKey);

            Address legacyAddress = Address.fromKey(networkParams, signingKey, Script.ScriptType.P2PKH);
            keyByAddress.put(legacyAddress.toString(), privateKey);
        }
        return keyByAddress;
    }

    /**
     * Utility method to convert hex string to byte array
     */
    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
