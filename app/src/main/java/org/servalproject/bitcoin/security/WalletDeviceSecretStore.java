package org.servalproject.bitcoin.security;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import org.servalproject.util.Base64Compat;

import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class WalletDeviceSecretStore {
    private static final String TAG = "WalletDeviceSecret";
    private static final String PREFS_NAME = "bitcoin_wallet_device_binding";
    private static final String KEY_MODE_PREFIX = "binding_mode_";
    private static final String KEY_SECRET_PREFIX = "binding_secret_";
    private static final String MODE_KEYSTORE = "keystore_v1";
    private static final String MODE_LOCAL = "local_v1";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String MASTER_ALIAS = "satnet_wallet_binding_master";
    private static final int SECRET_LENGTH_BYTES = 32;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private WalletDeviceSecretStore() {
    }

    public static byte[] getOrCreateBindingSecret(Context context, String walletId) throws Exception {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }
        if (walletId == null || walletId.trim().isEmpty()) {
            throw new IllegalArgumentException("Wallet ID is required");
        }
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String modeKey = KEY_MODE_PREFIX + walletId;
        String secretKey = KEY_SECRET_PREFIX + walletId;
        String storedMode = prefs.getString(modeKey, null);

        if (MODE_KEYSTORE.equals(storedMode)) {
            if (!isAndroidKeystoreSupported()) {
                throw new IllegalStateException("Wallet binding secret requires Android Keystore support on this device");
            }
            return getOrCreateKeystoreBoundSecret(prefs, secretKey);
        }
        if (MODE_LOCAL.equals(storedMode)) {
            return getOrCreateLocalSecret(prefs, secretKey);
        }

        if (isAndroidKeystoreSupported()) {
            try {
                byte[] secret = getOrCreateKeystoreBoundSecret(prefs, secretKey);
                prefs.edit().putString(modeKey, MODE_KEYSTORE).apply();
                return secret;
            } catch (Exception e) {
                Log.w(TAG, "Android Keystore unavailable for wallet binding, falling back to local device secret", e);
            }
        }

        byte[] secret = getOrCreateLocalSecret(prefs, secretKey);
        prefs.edit().putString(modeKey, MODE_LOCAL).apply();
        return secret;
    }

    private static boolean isAndroidKeystoreSupported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private static byte[] getOrCreateKeystoreBoundSecret(SharedPreferences prefs, String secretKey) throws Exception {
        SecretKey masterKey = getOrCreateMasterKey();
        String storedBlob = prefs.getString(secretKey, null);
        if (storedBlob != null && !storedBlob.trim().isEmpty()) {
            return decryptSecretBlob(storedBlob, masterKey);
        }
        byte[] generatedSecret = generateRandomSecret();
        prefs.edit().putString(secretKey, encryptSecretBlob(generatedSecret, masterKey)).apply();
        return generatedSecret;
    }

    private static byte[] getOrCreateLocalSecret(SharedPreferences prefs, String secretKey) {
        String storedSecret = prefs.getString(secretKey, null);
        if (storedSecret != null && !storedSecret.trim().isEmpty()) {
            return Base64Compat.decode(storedSecret);
        }
        byte[] generatedSecret = generateRandomSecret();
        prefs.edit().putString(secretKey, Base64Compat.encode(generatedSecret)).apply();
        return generatedSecret;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private static SecretKey getOrCreateMasterKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(MASTER_ALIAS)) {
            KeyStore.Entry existingEntry = keyStore.getEntry(MASTER_ALIAS, null);
            if (existingEntry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) existingEntry).getSecretKey();
            }
        }

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                MASTER_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }

    private static byte[] generateRandomSecret() {
        byte[] secret = new byte[SECRET_LENGTH_BYTES];
        new SecureRandom().nextBytes(secret);
        return secret;
    }

    private static String encryptSecretBlob(byte[] secret, SecretKey secretKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] ciphertext = cipher.doFinal(secret);
        byte[] iv = cipher.getIV();
        return Base64Compat.encode(iv) + ":" + Base64Compat.encode(ciphertext);
    }

    private static byte[] decryptSecretBlob(String encodedBlob, SecretKey secretKey) throws Exception {
        String[] parts = encodedBlob.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Malformed wallet binding blob");
        }
        byte[] iv = Base64Compat.decode(parts[0]);
        byte[] ciphertext = Base64Compat.decode(parts[1]);
        if (iv.length != GCM_IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("Malformed wallet binding IV");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        return cipher.doFinal(ciphertext);
    }
}
