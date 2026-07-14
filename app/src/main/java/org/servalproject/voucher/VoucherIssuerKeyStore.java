package org.servalproject.voucher;

import android.content.Context;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.security.auth.x500.X500Principal;

final class VoucherIssuerKeyStore {
    static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final Map<String, KeyPair> FALLBACK_KEYS = new ConcurrentHashMap<String, KeyPair>();

    private VoucherIssuerKeyStore() {
    }

    static KeyPair loadOrCreate(Context context, String alias, String algorithm) throws Exception {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }
        if (!VoucherSignatureAlgorithms.ALG_RSA_SHA256.equals(VoucherSignatureAlgorithms.normalize(algorithm))) {
            throw new UnsupportedOperationException("Keystore-backed issuer keys currently support only RSA_SHA256");
        }
        if (alias == null || alias.trim().isEmpty()) {
            throw new IllegalArgumentException("Keystore alias is required");
        }

        if (isAndroidKeyStoreAvailable()) {
            KeyPair existing = getAndroidKeyStoreKeyPair(alias);
            if (existing != null) {
                return existing;
            }
            return generateAndroidKeyStoreKeyPair(context, alias);
        }
        return getOrCreateFallbackKeyPair(alias, algorithm);
    }

    static boolean isAndroidKeyStoreAvailable() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static KeyPair getAndroidKeyStoreKeyPair(String alias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        KeyStore.Entry entry = keyStore.getEntry(alias, null);
        if (!(entry instanceof KeyStore.PrivateKeyEntry)) {
            return null;
        }
        KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) entry;
        PrivateKey privateKey = privateKeyEntry.getPrivateKey();
        PublicKey publicKey = privateKeyEntry.getCertificate() == null ? null : privateKeyEntry.getCertificate().getPublicKey();
        if (privateKey == null || publicKey == null) {
            return null;
        }
        return new KeyPair(publicKey, privateKey);
    }

    private static KeyPair generateAndroidKeyStoreKeyPair(Context context, String alias) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(alias,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setKeySize(2048)
                    .build();
            keyPairGenerator.initialize(spec);
        } else {
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();
            end.add(Calendar.YEAR, 25);
            KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context)
                    .setAlias(alias)
                    .setSubject(new X500Principal("CN=" + alias))
                    .setSerialNumber(BigInteger.ONE)
                    .setStartDate(start.getTime())
                    .setEndDate(end.getTime())
                    .build();
            keyPairGenerator.initialize(spec);
        }
        return keyPairGenerator.generateKeyPair();
    }

    private static KeyPair getOrCreateFallbackKeyPair(String alias, String algorithm) throws Exception {
        KeyPair existing = FALLBACK_KEYS.get(alias);
        if (existing != null) {
            return existing;
        }
        KeyPair created = VoucherSignatureAlgorithms.generateKeyPair(algorithm);
        FALLBACK_KEYS.put(alias, created);
        return created;
    }
}

