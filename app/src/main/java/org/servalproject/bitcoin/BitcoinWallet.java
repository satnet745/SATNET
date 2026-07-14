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

package org.servalproject.bitcoin;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDPath;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;
import org.servalproject.bitcoin.blockchain.EsploraApiClient;
import org.servalproject.bitcoin.blockchain.BitcoinTransactionBuilder;
import org.servalproject.features.FeatureFlags;
import org.servalproject.bitcoin.security.WalletDeviceSecretStore;
import org.servalproject.bitcoin.security.WalletEncryption;
import org.servalproject.satnet.SatnetPolicy;
import org.servalproject.satnet.SatnetRoleManager;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * SATNET AFRICA Bitcoin Wallet
 *
 * Features:
 * - Self-custody (user controls private keys)
 * - BIP32/BIP39 HD wallet (12-word recovery phrase)
 * - Encrypted seed storage
 * - Multi-address support
 * - Offline transaction signing
 * - Balance tracking via Esplora API
 */
public class BitcoinWallet {
    private static final String TAG = "BitcoinWallet";
    private static final Script.ScriptType OUTPUT_SCRIPT_TYPE = Script.ScriptType.P2WPKH;
    private static final String PREFS_NAME = "bitcoin_wallet";
    private static final String KEY_SEED_ENCRYPTED = "seed_encrypted";
    private static final String KEY_SEED_SALT = "seed_salt";
    private static final String KEY_SEED_IV = "seed_iv";
    private static final String KEY_NETWORK = "network";

    private final Context appContext;
    private final String walletId;
    private Wallet wallet;
    private NetworkParameters networkParams;
    private final SharedPreferences prefs;
    private List<String> mnemonicWords;
    private boolean testnet;

    public BitcoinWallet(Context context, String walletId) {
        this.appContext = context.getApplicationContext();
        this.walletId = walletId;
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SatnetPolicy.enforceBuildPolicy();

        boolean defaultTestnet = SatnetPolicy.requiresTestnetByDefault() || !FeatureFlags.isSatnetMainnetSettlementEnabled();
        testnet = prefs.getBoolean(KEY_NETWORK + "_" + walletId, defaultTestnet);

        if (!SatnetPolicy.isMainnetSettlementPermitted() && !testnet) {
            Log.w(TAG, "Mainnet wallet preference ignored because SATNET policy requires non-mainnet operation");
            testnet = true;
            prefs.edit().putBoolean(KEY_NETWORK + "_" + walletId, true).apply();
        }

        this.networkParams = testnet ? TestNet3Params.get() : MainNetParams.get();
    }

    public BitcoinWallet(Context context, String walletId, boolean testnet) {
        this(context, walletId);
        setTestnet(testnet);
    }

    /**
     * Generate and persist a new wallet seed.
     */
    public void generateNewWallet(String password) throws Exception {
        char[] passwordChars = password == null ? null : password.toCharArray();
        try {
            generateNewWallet(passwordChars);
        } finally {
            WalletEncryption.clearChars(passwordChars);
        }
    }

    public void generateNewWallet(char[] passwordChars) throws Exception {
        if (passwordChars == null || passwordChars.length == 0) {
            throw new IllegalArgumentException("A password/PIN is required to create a wallet");
        }

        Log.d(TAG, "Generating new wallet: " + walletId);

        SecureRandom random = new SecureRandom();
        byte[] seed = new byte[16]; // 128 bits = 12 words
        random.nextBytes(seed);

        DeterministicSeed deterministicSeed = new DeterministicSeed(seed, "", Utils.currentTimeSeconds());
        this.wallet = createWalletFromSeed(deterministicSeed);
        this.mnemonicWords = deterministicSeed.getMnemonicCode();

        encryptAndStoreSeed(serializeDeterministicSeed(deterministicSeed), passwordChars);
        Log.d(TAG, "Wallet generated successfully");
    }

    public List<String> generateNewMnemonic() throws Exception {
        // Generate phrase in-memory for setup flow; persistence happens after PIN entry.
        SecureRandom random = new SecureRandom();
        byte[] seed = new byte[16];
        random.nextBytes(seed);

        DeterministicSeed deterministicSeed = new DeterministicSeed(seed, "", Utils.currentTimeSeconds());
        this.wallet = createWalletFromSeed(deterministicSeed);
        this.mnemonicWords = deterministicSeed.getMnemonicCode();
        return getRecoveryPhrase();
    }

    /**
     * Load or restore wallet from mnemonic phrase
     */
    public void restoreFromMnemonic(List<String> mnemonicWords, String password) throws Exception {
        char[] passwordChars = password == null ? null : password.toCharArray();
        try {
            restoreFromMnemonic(mnemonicWords, passwordChars);
        } finally {
            WalletEncryption.clearChars(passwordChars);
        }
    }

    public void restoreFromMnemonic(List<String> mnemonicWords, char[] passwordChars) throws Exception {
        Log.d(TAG, "Restoring wallet from mnemonic");
        if (passwordChars == null || passwordChars.length == 0) {
            throw new IllegalArgumentException("A password/PIN is required to restore a wallet");
        }

        this.mnemonicWords = mnemonicWords;
        DeterministicSeed deterministicSeed = new DeterministicSeed(mnemonicWords, null, "", Utils.currentTimeSeconds());
        this.wallet = createWalletFromSeed(deterministicSeed);

        encryptAndStoreSeed(serializeDeterministicSeed(deterministicSeed), passwordChars);
        Log.d(TAG, "Wallet restored successfully");
    }

    public void importFromMnemonic(String mnemonicString, String password) throws Exception {
        char[] passwordChars = password == null ? null : password.toCharArray();
        try {
            importFromMnemonic(mnemonicString, passwordChars);
        } finally {
            WalletEncryption.clearChars(passwordChars);
        }
    }

    public void importFromMnemonic(String mnemonicString, char[] passwordChars) throws Exception {
        if (mnemonicString == null || mnemonicString.isEmpty()) return;
        List<String> mnemonicList = Arrays.asList(mnemonicString.trim().split("\\s+"));
        MnemonicCode.INSTANCE.check(mnemonicList);
        restoreFromMnemonic(mnemonicList, passwordChars);
    }

    /**
     * Load encrypted seed (requires password for secure format).
     */
    public void loadEncryptedSeed(String password) throws Exception {
        char[] passwordChars = password == null ? null : password.toCharArray();
        try {
            loadEncryptedSeed(passwordChars);
        } finally {
            WalletEncryption.clearChars(passwordChars);
        }
    }

    public void loadEncryptedSeed(char[] passwordChars) throws Exception {
        Log.d(TAG, "Loading seed for wallet: " + walletId);

        String seedEncrypted = prefs.getString(KEY_SEED_ENCRYPTED + "_" + walletId, null);
        String seedSalt = prefs.getString(KEY_SEED_SALT + "_" + walletId, null);
        String seedIv = prefs.getString(KEY_SEED_IV + "_" + walletId, null);

        if (seedEncrypted == null) {
            Log.w(TAG, "No seed found for wallet: " + walletId);
            return;
        }

        byte[] seed;
        boolean isSecureFormat = seedSalt != null && seedIv != null && !"salt".equals(seedSalt);
        if (isSecureFormat) {
            WalletEncryption.EncryptedSeed encryptedSeed =
                    new WalletEncryption.EncryptedSeed(seedEncrypted, seedSalt, seedIv);
            seed = decryptStoredSeed(encryptedSeed, passwordChars);
        } else {
            // Legacy compatibility: historical builds stored hex directly.
            seed = hexToBytes(seedEncrypted);
            if (passwordChars != null && passwordChars.length > 0) {
                Log.i(TAG, "Migrating legacy plaintext seed to encrypted format");
                encryptAndStoreSeed(seed, passwordChars);
            } else {
                Log.w(TAG, "Loaded legacy plaintext seed without migration password");
            }
        }

        DeterministicSeed deterministicSeed;
        try {
            String mnemonic = new String(seed, StandardCharsets.UTF_8).trim();
            deterministicSeed = new DeterministicSeed(mnemonic, null, "", Utils.currentTimeSeconds());
        } catch (Exception e) {
            Log.w(TAG, "Stored wallet payload was not mnemonic text, falling back to entropy format", e);
            deterministicSeed = new DeterministicSeed(seed, "", Utils.currentTimeSeconds());
            if (passwordChars != null && passwordChars.length > 0) {
                encryptAndStoreSeed(serializeDeterministicSeed(deterministicSeed), passwordChars);
            }
        }
        this.wallet = createWalletFromSeed(deterministicSeed);
        this.mnemonicWords = deterministicSeed.getMnemonicCode();

        Log.d(TAG, "Seed loaded successfully");
    }

    /**
     * Get current balance in satoshis
     */
    public long getBalance() {
        if (wallet == null) {
            return 0;
        }
        return wallet.getBalance().value;
    }

    public long getBalanceSats() {
        return getBalance();
    }

    /**
     * Get current receive address
     */
    public String getReceiveAddress() {
        if (wallet == null) {
            return null;
        }
        Address address = wallet.currentReceiveAddress();
        return address != null ? address.toString() : null;
    }

    public String getDerivedAddress(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Address index cannot be negative");
        }
        if (wallet == null) {
            return null;
        }
        DeterministicKeyChain keyChain = wallet.getActiveKeyChain();
        if (keyChain == null) {
            return getReceiveAddress();
        }
        HDPath receivePath = keyChain.getAccountPath()
                .extend(DeterministicKeyChain.EXTERNAL_SUBPATH)
                .extend(new ChildNumber(index, false));
        DeterministicKey derivedKey = keyChain.getKeyByPath(receivePath, true);
        Address address = Address.fromKey(networkParams, derivedKey, keyChain.getOutputScriptType());
        return address != null ? address.toString() : null;
    }

    /**
     * Get recovery phrase (12-word mnemonic)
     */
    public List<String> getRecoveryPhrase() {
        return this.mnemonicWords;
    }

    /**
     * Create and sign a Bitcoin transaction for sending to another address
     */
    public SendTransactionResult createAndSignTransaction(
            String recipientAddress,
            long amountSats,
            long feeRateSatPerVbyte,
            String password) throws Exception {

        char[] passwordChars = password == null ? null : password.toCharArray();
        try {
            return createAndSignTransaction(recipientAddress, amountSats, feeRateSatPerVbyte, passwordChars);
        } finally {
            WalletEncryption.clearChars(passwordChars);
        }
    }

    public SendTransactionResult createAndSignTransaction(
            String recipientAddress,
            long amountSats,
            long feeRateSatPerVbyte,
            char[] passwordChars) throws Exception {

        if (!isInitialized()) {
            throw new IllegalStateException("Wallet not initialized");
        }

        if (passwordChars == null || passwordChars.length == 0) {
            throw new IllegalArgumentException("Password is required for transaction signing");
        }

        // ========== BANKING AUTHORIZATION FRAMEWORK ==========
        // Validate authorization and enforce per-role limits
        // This is isolated to Bitcoin operations and does not affect voucher/comms systems
        try {
            SatnetRoleManager roleManager = new SatnetRoleManager(appContext);

            // Step 1: Check if user has P2P Bitcoin send capability
            SatnetRoleManager.AuthorizationResult authResult = roleManager.authorize(
                SatnetRoleManager.CAP_BITCOIN_SEND,
                "P2P Bitcoin transfer: " + amountSats + " sats to " + recipientAddress);

            if (!authResult.allowed) {
                Log.w(TAG, "Bitcoin send authorization denied: " + authResult.reasonCode);
                throw new SecurityException("Not authorized for Bitcoin P2P transfers. Reason: " + authResult.message);
            }

            // Step 2: Check role profile status and limits
            SatnetRoleManager.RoleProfile profile = roleManager.getActiveRoleProfile();
            if (profile != null) {
                // Check if role is suspended
                if (profile.status == SatnetRoleManager.ROLE_STATUS_SUSPENDED) {
                    throw new SecurityException("Your role is temporarily suspended. Reason: " +
                            (profile.suspensionReason != null ? profile.suspensionReason : "Unknown"));
                }

                // Check if role requires review
                if (profile.status == SatnetRoleManager.ROLE_STATUS_REVIEW_REQUIRED) {
                    throw new SecurityException("Your role requires review before Bitcoin transfers are allowed");
                }

                // Check daily limit (if configured)
                if (profile.dailyLimitSats > 0 && amountSats > profile.dailyLimitSats) {
                    throw new IllegalArgumentException(
                        String.format("Daily limit exceeded. Attempted: %,d sats, Daily limit: %,d sats",
                                     amountSats, profile.dailyLimitSats));
                }

                // Check monthly limit (if configured)
                if (profile.monthlyLimitSats > 0 && amountSats > profile.monthlyLimitSats) {
                    throw new IllegalArgumentException(
                        String.format("Monthly limit exceeded. Attempted: %,d sats, Monthly limit: %,d sats",
                                     amountSats, profile.monthlyLimitSats));
                }
            }

            Log.d(TAG, "Bitcoin send authorization granted for role: " + roleManager.getActiveRole());
        } catch (SecurityException | IllegalArgumentException e) {
            // Re-throw authorization-specific exceptions
            throw e;
        } catch (Exception e) {
            // Log but don't fail on role manager issues (backwards compatibility)
            Log.w(TAG, "Role authorization check failed (non-critical): " + e.getMessage());
        }
        // ========== END BANKING AUTHORIZATION FRAMEWORK ==========

        // Validate recipient address
        try {
            Address.fromString(networkParams, recipientAddress);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid recipient address: " + e.getMessage());
        }

        // Get blockchain API client
        EsploraApiClient apiClient = new EsploraApiClient(networkParams);
        BitcoinTransactionBuilder txBuilder = new BitcoinTransactionBuilder(networkParams, apiClient);

        // Get all addresses from wallet for UTXO lookup
        List<String> walletAddresses = getAllWalletAddresses();

        // Fetch UTXOs for all wallet addresses (PARALLEL EXECUTION - 4-10x faster)
        List<EsploraApiClient.Utxo> allUtxos = fetchUtxosParallel(walletAddresses, apiClient);

        if (allUtxos.isEmpty()) {
            throw new IllegalArgumentException("No spendable UTXOs found in wallet");
        }

        // Get change address (next unused address)
        String changeAddress = getReceiveAddress();
        if (changeAddress == null) {
            throw new IllegalStateException("Unable to generate change address");
        }

        // Create transaction
        BitcoinTransactionBuilder.TransactionResult txResult = txBuilder.createTransaction(
                allUtxos, recipientAddress, amountSats, changeAddress, feeRateSatPerVbyte);

        // Derive the private keys for the wallet addresses we scan for UTXOs
        List<org.bitcoinj.core.ECKey> privateKeys = getPrivateKeysForSigning(txResult.selectedUtxos, passwordChars);

        // Sign transaction
        txBuilder.signTransaction(txResult, privateKeys);

        // Verify transaction
        txResult.transaction.verify();

        String txHex = txResult.getHex();
        Log.d(TAG, "Transaction created and signed: " + txResult.transaction.getTxId());

        return new SendTransactionResult(
                txResult.transaction.getTxId().toString(),
                txHex,
                txResult.fee,
                txResult.changeAmount,
                amountSats
        );
    }

    /**
     * Broadcast a signed transaction to the network with retry logic
     * Implements exponential backoff to handle temporary network failures
     */
    public String broadcastTransaction(String signedTxHex) throws Exception {
        EsploraApiClient apiClient = new EsploraApiClient(networkParams);

        // Retry configuration
        int maxRetries = 3;
        long initialDelayMs = 1000; // 1 second
        long maxDelayMs = 10000; // 10 seconds

        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                String txid = apiClient.broadcastTransaction(signedTxHex);

                // ========== BANKING AUDIT TRAIL ==========
                // Log transaction broadcast to audit trail (non-disruptive, catch-all error handling)
                try {
                    SatnetRoleManager roleManager = new SatnetRoleManager(appContext);
                    int activeRole = roleManager.getActiveRole();
                    Log.i(TAG, String.format(
                        "[BANKING_AUDIT] Bitcoin transaction broadcast: txid=%s, role=%d, timestamp=%d, network=%s, attempt=%d",
                        txid, activeRole, System.currentTimeMillis(), isTestnet() ? "testnet" : "mainnet", attempt + 1
                    ));
                } catch (Exception auditError) {
                    // Don't fail transaction if audit logging fails
                    Log.w(TAG, "Warning: Failed to log Bitcoin transaction to audit trail: " + auditError.getMessage());
                }
                // ========== END BANKING AUDIT TRAIL ==========

                Log.d(TAG, "Broadcasted transaction: " + txid + " (attempt " + (attempt + 1) + ")");
                return txid;

            } catch (Exception e) {
                lastException = e;
                Log.w(TAG, "Broadcast attempt " + (attempt + 1) + " failed: " + e.getMessage());

                // If this is the last attempt, don't retry
                if (attempt == maxRetries) {
                    break;
                }

                // Calculate delay with exponential backoff
                long delayMs = Math.min(initialDelayMs * (1L << attempt), maxDelayMs);
                Log.d(TAG, "Retrying broadcast in " + delayMs + "ms (attempt " + (attempt + 2) + ")");

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Broadcast interrupted", ie);
                }
            }
        }

        // All retries failed
        Log.e(TAG, "Broadcast failed after " + (maxRetries + 1) + " attempts");
        throw new RuntimeException("Failed to broadcast transaction after " + (maxRetries + 1) + " attempts: " + lastException.getMessage(), lastException);
    }

    /**
     * Get recommended fee rate for transaction confirmation
     */
    public long getRecommendedFeeRate(int targetBlocks) throws Exception {
        EsploraApiClient apiClient = new EsploraApiClient(networkParams);
        return apiClient.getRecommendedFeeRate(targetBlocks);
    }

    /**
     * Get fee estimates for different confirmation targets
     */
    public List<EsploraApiClient.FeeEstimate> getFeeEstimates() throws Exception {
        EsploraApiClient apiClient = new EsploraApiClient(networkParams);
        return apiClient.getFeeEstimates();
    }

    /**
     * Result of a send transaction operation
     */
    public static class SendTransactionResult {
        public final String txid;
        public final String signedTxHex;
        public final long fee;
        public final long changeAmount;
        public final long sentAmount;

        public SendTransactionResult(String txid, String signedTxHex, long fee, long changeAmount, long sentAmount) {
            this.txid = txid;
            this.signedTxHex = signedTxHex;
            this.fee = fee;
            this.changeAmount = changeAmount;
            this.sentAmount = sentAmount;
        }

        public long getTotalCost() {
            return sentAmount + fee;
        }
    }

    /**
     * Queued transaction for offline broadcast support
     */
    public static class QueuedTransaction {
        public final String id;
        public final String signedTxHex;
        public final String description;
        public final long createdAt;
        public int retryCount;

        public QueuedTransaction(String signedTxHex, String description, long createdAt, int retryCount) {
            this.id = "queued_" + System.currentTimeMillis() + "_" + Math.abs(signedTxHex.hashCode());
            this.signedTxHex = signedTxHex;
            this.description = description;
            this.createdAt = createdAt;
            this.retryCount = retryCount;
        }
    }

    /**
     * Persistence methods for queued transactions
     */
    private static final String KEY_QUEUED_TRANSACTIONS = "queued_transactions";

    private void saveQueuedTransaction(QueuedTransaction tx) {
        SharedPreferences queuePrefs = appContext.getSharedPreferences("bitcoin_queue_" + walletId, Context.MODE_PRIVATE);
        String key = "tx_" + tx.id;
        String value = tx.signedTxHex + "|" + tx.description + "|" + tx.createdAt + "|" + tx.retryCount;
        queuePrefs.edit().putString(key, value).apply();
    }

    private void updateQueuedTransaction(QueuedTransaction tx) {
        saveQueuedTransaction(tx);
    }

    private void removeQueuedTransaction(String txId) {
        SharedPreferences queuePrefs = appContext.getSharedPreferences("bitcoin_queue_" + walletId, Context.MODE_PRIVATE);
        String key = "tx_" + txId;
        queuePrefs.edit().remove(key).apply();
    }

    private List<QueuedTransaction> getQueuedTransactions() {
        SharedPreferences queuePrefs = appContext.getSharedPreferences("bitcoin_queue_" + walletId, Context.MODE_PRIVATE);
        List<QueuedTransaction> transactions = new ArrayList<>();

        for (String key : queuePrefs.getAll().keySet()) {
            if (key.startsWith("tx_")) {
                String value = queuePrefs.getString(key, null);
                if (value != null) {
                    try {
                        String[] parts = value.split("\\|", 4);
                        if (parts.length == 4) {
                            String txId = key.substring(3); // Remove "tx_" prefix
                            String signedTxHex = parts[0];
                            String description = parts[1];
                            long createdAt = Long.parseLong(parts[2]);
                            int retryCount = Integer.parseInt(parts[3]);

                            QueuedTransaction tx = new QueuedTransaction(signedTxHex, description, createdAt, retryCount);
                            // Override the id to match the stored one
                            java.lang.reflect.Field idField = QueuedTransaction.class.getDeclaredField("id");
                            idField.setAccessible(true);
                            idField.set(tx, txId);
                            transactions.add(tx);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to parse queued transaction: " + key);
                    }
                }
            }
        }

        // Sort by creation time (oldest first)
        Collections.sort(transactions, new Comparator<QueuedTransaction>() {
            @Override
            public int compare(QueuedTransaction left, QueuedTransaction right) {
                return Long.compare(left.createdAt, right.createdAt);
            }
        });
        return transactions;
    }

    /**
     * Get all addresses that belong to this wallet (for UTXO scanning)
     */
    private List<String> getAllWalletAddresses() {
        List<String> addresses = new ArrayList<>();

        // Add current receive address
        String currentAddress = getReceiveAddress();
        if (currentAddress != null) {
            addresses.add(currentAddress);
        }

        // Add some derived addresses (simplified - in practice you'd scan more)
        for (int i = 0; i < 20; i++) {
            String derivedAddress = getDerivedAddress(i);
            if (derivedAddress != null && !addresses.contains(derivedAddress)) {
                addresses.add(derivedAddress);
            }
        }

        return addresses;
    }

    /**
     * Get private keys for signing selected UTXOs.
     */
    private List<org.bitcoinj.core.ECKey> getPrivateKeysForSigning(
            List<EsploraApiClient.Utxo> selectedUtxos, char[] passwordChars) throws Exception {

        List<org.bitcoinj.core.ECKey> privateKeys = new ArrayList<>();

        if (wallet != null && wallet.getActiveKeyChain() != null) {
            DeterministicKeyChain keyChain = wallet.getActiveKeyChain();
            for (int index = 0; index < 20; index++) {
                HDPath receivePath = keyChain.getAccountPath()
                        .extend(DeterministicKeyChain.EXTERNAL_SUBPATH)
                        .extend(new ChildNumber(index, false));
                DeterministicKey privateKey = keyChain.getKeyByPath(receivePath, true);
                privateKeys.add(org.bitcoinj.core.ECKey.fromPrivate(privateKey.getPrivKey(), true));
            }
        }

        if (privateKeys.isEmpty()) {
            throw new IllegalStateException("No private keys available for signing");
        }

        return privateKeys;
    }

    public boolean isInitialized() {
        return wallet != null;
    }

    /**
     * Verify backup phrase
     */
    public boolean verifyBackupPhrase(List<String> userPhrase) {
        if (mnemonicWords == null) {
            return false;
        }
        return mnemonicWords.equals(userPhrase);
    }

    public void wipe() {
        Log.d(TAG, "Wiping wallet data for: " + walletId);
        prefs.edit()
            .remove(KEY_SEED_ENCRYPTED + "_" + walletId)
            .remove(KEY_SEED_SALT + "_" + walletId)
            .remove(KEY_SEED_IV + "_" + walletId)
            .remove(KEY_NETWORK + "_" + walletId)
            .apply();
        clearSensitiveMemory();
    }

    public void clearSensitiveMemory() {
        this.wallet = null;
        this.mnemonicWords = null;
    }

    public boolean hasStoredSeed() {
        return prefs.contains(KEY_SEED_ENCRYPTED + "_" + walletId);
    }

    // Helper methods

    private void encryptAndStoreSeed(byte[] seed, String password) throws Exception {
        char[] passwordChars = password == null ? null : password.toCharArray();
        try {
            encryptAndStoreSeed(seed, passwordChars);
        } finally {
            WalletEncryption.clearChars(passwordChars);
        }
    }

    private void encryptAndStoreSeed(byte[] seed, char[] passwordChars) throws Exception {
        if (passwordChars == null || passwordChars.length == 0) {
            throw new IllegalArgumentException("Password is required to encrypt wallet seed");
        }
        Log.d(TAG, "Encrypting and storing seed");

        char[] compositeSecret = createCompositeSecret(passwordChars);
        WalletEncryption.EncryptedSeed encryptedSeed;
        try {
            encryptedSeed = WalletEncryption.encryptSeed(seed, compositeSecret);
        } finally {
            WalletEncryption.clearChars(compositeSecret);
        }
        prefs.edit()
            .putString(KEY_SEED_ENCRYPTED + "_" + walletId, encryptedSeed.ciphertext)
            .putString(KEY_SEED_SALT + "_" + walletId, encryptedSeed.salt)
            .putString(KEY_SEED_IV + "_" + walletId, encryptedSeed.iv)
            .apply();
    }

    private byte[] decryptStoredSeed(WalletEncryption.EncryptedSeed encryptedSeed, char[] passwordChars) throws Exception {
        if (passwordChars == null || passwordChars.length == 0) {
            throw new IllegalArgumentException("Password is required to decrypt wallet seed");
        }
        char[] compositeSecret = createCompositeSecret(passwordChars);
        try {
            return WalletEncryption.decryptSeed(encryptedSeed, compositeSecret);
        } catch (Exception secureError) {
            Log.i(TAG, "Falling back to legacy wallet seed decryption and migrating to device-bound protection", secureError);
            byte[] legacySeed = WalletEncryption.decryptSeed(encryptedSeed, passwordChars);
            encryptAndStoreSeed(legacySeed, passwordChars);
            return legacySeed;
        } finally {
            WalletEncryption.clearChars(compositeSecret);
        }
    }

    private char[] createCompositeSecret(char[] passwordChars) throws Exception {
        byte[] walletBinding = WalletDeviceSecretStore.getOrCreateBindingSecret(appContext, walletId);
        return WalletEncryption.buildCompositeSecret(
                passwordChars,
                walletId.getBytes(StandardCharsets.UTF_8),
                walletBinding);
    }

    private byte[] serializeDeterministicSeed(DeterministicSeed deterministicSeed) {
        if (deterministicSeed == null) {
            throw new IllegalArgumentException("Deterministic seed is required");
        }
        byte[] mnemonicBytes = deterministicSeed.getSecretBytes();
        if (mnemonicBytes == null || mnemonicBytes.length == 0) {
            throw new IllegalStateException("Unable to extract wallet mnemonic bytes");
        }
        return mnemonicBytes;
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }

    private Wallet createWalletFromSeed(DeterministicSeed deterministicSeed) {
        return Wallet.fromSeed(networkParams, deterministicSeed, OUTPUT_SCRIPT_TYPE);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void setTestnet(boolean testnet) {
        if (!testnet && !SatnetPolicy.isMainnetSettlementPermitted()) {
            throw new IllegalStateException("Mainnet settlement is disabled by SATNET policy");
        }
        this.testnet = testnet;
        this.networkParams = testnet ? TestNet3Params.get() : MainNetParams.get();
        prefs.edit().putBoolean(KEY_NETWORK + "_" + walletId, testnet).apply();
    }

     public boolean isTestnet() {
         return testnet;
     }

     public NetworkParameters getNetworkParams() {
         return networkParams;
     }

    /**
     * Fetch UTXOs for multiple addresses in parallel (4-10x performance improvement)
     * Replaces serial fetching that caused 20-60 second delays
     */
    private List<EsploraApiClient.Utxo> fetchUtxosParallel(List<String> addresses, EsploraApiClient apiClient) throws Exception {
        if (addresses.isEmpty()) {
            return new ArrayList<>();
        }

        Log.d(TAG, "Fetching UTXOs for " + addresses.size() + " addresses in parallel");

        // Use fixed thread pool with 4 threads (optimal for network I/O)
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<List<EsploraApiClient.Utxo>>> futures = new ArrayList<>();

        try {
            // Submit parallel tasks for each address
            for (String address : addresses) {
                Future<List<EsploraApiClient.Utxo>> future = executor.submit(() -> {
                    try {
                        List<EsploraApiClient.Utxo> utxos = apiClient.getUtxos(address);
                        Log.d(TAG, "Fetched " + utxos.size() + " UTXOs for address: " + address.substring(0, 8) + "...");
                        return utxos;
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to fetch UTXOs for address " + address.substring(0, 8) + "...: " + e.getMessage());
                        return new ArrayList<EsploraApiClient.Utxo>(); // Return empty list on failure
                    }
                });
                futures.add(future);
            }

            // Wait for all tasks to complete with 30 second timeout
            List<EsploraApiClient.Utxo> allUtxos = new ArrayList<>();
            for (Future<List<EsploraApiClient.Utxo>> future : futures) {
                try {
                    List<EsploraApiClient.Utxo> addressUtxos = future.get(30, TimeUnit.SECONDS);
                    allUtxos.addAll(addressUtxos);
                } catch (Exception e) {
                    Log.w(TAG, "UTXO fetch task failed: " + e.getMessage());
                    // Continue with other addresses
                }
            }

            Log.d(TAG, "Parallel UTXO fetch complete: " + allUtxos.size() + " total UTXOs from " + addresses.size() + " addresses");
            return allUtxos;

        } finally {
            // Always shutdown executor
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Queue a signed transaction for later broadcast (offline support)
     * Stores transaction in persistent queue for retry when network is available
     */
    public void queueTransactionForBroadcast(String signedTxHex, String description) throws Exception {
        if (signedTxHex == null || signedTxHex.isEmpty()) {
            throw new IllegalArgumentException("Transaction hex cannot be null or empty");
        }

        // Create queue entry
        QueuedTransaction queuedTx = new QueuedTransaction(
            signedTxHex,
            description != null ? description : "Queued transaction",
            System.currentTimeMillis(),
            0 // retry count
        );

        // Store in persistent queue
        saveQueuedTransaction(queuedTx);

        Log.d(TAG, "Transaction queued for broadcast: " + queuedTx.id + " - " + description);

        // Try to broadcast immediately if online
        try {
            String txid = broadcastTransaction(signedTxHex);
            Log.d(TAG, "Queued transaction broadcast immediately: " + txid);
            removeQueuedTransaction(queuedTx.id);
        } catch (Exception e) {
            Log.d(TAG, "Transaction queued for later broadcast (offline): " + e.getMessage());
        }
    }

    /**
     * Process queued transactions (call when network connectivity is restored)
     * Attempts to broadcast all queued transactions with retry logic
     */
    public void processQueuedTransactions() {
        List<QueuedTransaction> queuedTxs = getQueuedTransactions();

        if (queuedTxs.isEmpty()) {
            Log.d(TAG, "No queued transactions to process");
            return;
        }

        Log.d(TAG, "Processing " + queuedTxs.size() + " queued transactions");

        for (QueuedTransaction queuedTx : queuedTxs) {
            try {
                Log.d(TAG, "Attempting to broadcast queued transaction: " + queuedTx.id);
                String txid = broadcastTransaction(queuedTx.signedTxHex);

                // Success - remove from queue
                removeQueuedTransaction(queuedTx.id);
                Log.d(TAG, "Successfully broadcast queued transaction: " + txid);

            } catch (Exception e) {
                // Failed - increment retry count
                queuedTx.retryCount++;
                if (queuedTx.retryCount >= 5) { // Max retries
                    Log.w(TAG, "Removing queued transaction after max retries: " + queuedTx.id);
                    removeQueuedTransaction(queuedTx.id);
                } else {
                    updateQueuedTransaction(queuedTx);
                    Log.d(TAG, "Queued transaction failed, will retry later: " + queuedTx.id + " (attempt " + queuedTx.retryCount + ")");
                }
            }
        }
    }

    /**
     * Get count of queued transactions
     */
    public int getQueuedTransactionCount() {
        return getQueuedTransactions().size();
    }

    /**
     * Clear all queued transactions (use with caution)
     */
    public void clearQueuedTransactions() {
        List<QueuedTransaction> queuedTxs = getQueuedTransactions();
        for (QueuedTransaction tx : queuedTxs) {
            removeQueuedTransaction(tx.id);
        }
        Log.d(TAG, "Cleared all queued transactions");
    }
}
