package org.servalproject.bitcoin.blockchain;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class BitcoinTransactionBuilderTest {

    @Test
    public void signsEachInputWithMatchingDerivedKey() throws Exception {
        NetworkParameters params = TestNet3Params.get();
        BitcoinTransactionBuilder builder = new BitcoinTransactionBuilder(params, null);

        ECKey keyA = new ECKey();
        ECKey keyB = new ECKey();

        EsploraApiClient.Utxo utxoA = utxoForKey(params, keyA, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 0, 50_000L);
        EsploraApiClient.Utxo utxoB = utxoForKey(params, keyB, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", 1, 60_000L);

        Transaction tx = new Transaction(params);
        tx.addInput(new TransactionInput(params, tx, new byte[0], utxoA.getOutPoint()));
        tx.addInput(new TransactionInput(params, tx, new byte[0], utxoB.getOutPoint()));
        tx.addOutput(Coin.valueOf(10_000L), Address.fromKey(params, keyA, Script.ScriptType.P2WPKH));

        BitcoinTransactionBuilder.TransactionResult result =
                new BitcoinTransactionBuilder.TransactionResult(tx, 1_000L, 0L, Arrays.asList(utxoA, utxoB));

        builder.signTransaction(result, Arrays.asList(keyB, keyA));

        assertNotNull(tx.getInput(0).getWitness());
        assertNotNull(tx.getInput(1).getWitness());
        assertArrayEquals(keyA.getPubKey(), tx.getInput(0).getWitness().getPush(1));
        assertArrayEquals(keyB.getPubKey(), tx.getInput(1).getWitness().getPush(1));
        assertTrue(result.getHex().matches("^[0-9a-f]+$"));
    }

    @Test
    public void transactionHexIsSerializedHexNotObjectString() throws Exception {
        NetworkParameters params = TestNet3Params.get();
        Transaction tx = new Transaction(params);
        tx.addOutput(Coin.valueOf(1_000L), Address.fromKey(params, new ECKey(), Script.ScriptType.P2WPKH));

        BitcoinTransactionBuilder.TransactionResult result =
                new BitcoinTransactionBuilder.TransactionResult(tx, 0L, 0L, Collections.emptyList());

        String hex = result.getHex();
        assertNotNull(hex);
        assertTrue(hex.matches("^[0-9a-f]+$"));
    }

    private static EsploraApiClient.Utxo utxoForKey(NetworkParameters params, ECKey key, String txid, int vout, long value) {
        Script script = ScriptBuilder.createP2WPKHOutputScript(key);
        return new EsploraApiClient.Utxo(txid, vout, value, bytesToHex(script.getProgram()), 1, params);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }
}
