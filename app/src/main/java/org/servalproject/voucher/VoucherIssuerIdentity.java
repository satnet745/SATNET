package org.servalproject.voucher;

import android.content.Context;
import android.content.SharedPreferences;

import org.servalproject.util.Base64Compat;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public final class VoucherIssuerIdentity {
    private static final String PREFS_NAME = "satnet_voucher_issuer";
    private static final String KEY_PUBLIC = "public_key";
    private static final String KEY_PRIVATE = "private_key";
    private static final String KEY_ALGORITHM = "algorithm";
    private static final String KEY_KEYSTORE_ALIAS = "keystore_alias";
    private static final String DEFAULT_KEYSTORE_ALIAS = "satnet_voucher_issuer_default";

    private final KeyPair keyPair;
    private final String issuerKeyId;
    private final String algorithm;
    private final String keystoreAlias;

    private VoucherIssuerIdentity(KeyPair keyPair, String algorithm, String keystoreAlias) throws Exception {
        this.keyPair = keyPair;
        this.algorithm = VoucherSignatureAlgorithms.normalize(algorithm);
        this.keystoreAlias = keystoreAlias;
        this.issuerKeyId = fingerprint(keyPair.getPublic());
    }

    public static VoucherIssuerIdentity loadOrCreate(Context context) throws Exception {
        if (context == null) {
            throw new IllegalArgumentException("Context is required for persistent voucher issuer identity");
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String publicKeyEncoded = prefs.getString(KEY_PUBLIC, null);
        String privateKeyEncoded = prefs.getString(KEY_PRIVATE, null);
        String algorithm = prefs.getString(KEY_ALGORITHM, VoucherSignatureAlgorithms.ALG_RSA_SHA256);
        String keystoreAlias = prefs.getString(KEY_KEYSTORE_ALIAS, DEFAULT_KEYSTORE_ALIAS);

        if (keystoreAlias != null && !keystoreAlias.trim().isEmpty()) {
            KeyPair keyPair = VoucherIssuerKeyStore.loadOrCreate(context, keystoreAlias, algorithm);
            VoucherIssuerIdentity identity = new VoucherIssuerIdentity(keyPair, algorithm, keystoreAlias);
            persistMetadata(prefs, identity);
            if (privateKeyEncoded != null) {
                prefs.edit().remove(KEY_PRIVATE).apply();
            }
            return identity;
        }
        if (publicKeyEncoded != null && privateKeyEncoded != null) {
            return migrateLegacyPreferenceKeyPair(context, prefs, algorithm, publicKeyEncoded, privateKeyEncoded);
        }
        VoucherIssuerIdentity created = createEphemeral();
        persistMetadata(prefs, created);
        return created;
    }

    public static VoucherIssuerIdentity createEphemeral() throws Exception {
        return createEphemeral(VoucherSignatureAlgorithms.ALG_RSA_SHA256);
    }

    public static VoucherIssuerIdentity createEphemeral(String algorithm) throws Exception {
        return new VoucherIssuerIdentity(
                VoucherSignatureAlgorithms.generateKeyPair(algorithm),
                algorithm,
                VoucherSignatureAlgorithms.supportsSigning(algorithm) ? "ephemeral:" + VoucherSignatureAlgorithms.normalize(algorithm) : null);
    }

    public static VoucherIssuerIdentity loadOrCreatePersistent(Context context) throws Exception {
        if (context == null) {
            throw new IllegalArgumentException("Context is required for persistent voucher issuer identity");
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String algorithm = prefs.getString(KEY_ALGORITHM, VoucherSignatureAlgorithms.ALG_RSA_SHA256);
        String keystoreAlias = prefs.getString(KEY_KEYSTORE_ALIAS, DEFAULT_KEYSTORE_ALIAS);
        return loadOrCreatePersistent(context, keystoreAlias, algorithm);
    }

    public static VoucherIssuerIdentity loadOrCreatePersistent(Context context, String keystoreAlias, String algorithm) throws Exception {
        if (context == null) {
            throw new IllegalArgumentException("Context is required for persistent voucher issuer identity");
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String normalizedAlgorithm = VoucherSignatureAlgorithms.normalize(
                algorithm == null ? VoucherSignatureAlgorithms.ALG_RSA_SHA256 : algorithm);
        String resolvedAlias = keystoreAlias == null || keystoreAlias.trim().isEmpty()
                ? DEFAULT_KEYSTORE_ALIAS
                : keystoreAlias.trim();
        KeyPair keyPair = VoucherIssuerKeyStore.loadOrCreate(context, resolvedAlias, normalizedAlgorithm);
        VoucherIssuerIdentity identity = new VoucherIssuerIdentity(keyPair, normalizedAlgorithm, resolvedAlias);
        persistMetadata(prefs, identity);
        prefs.edit().remove(KEY_PRIVATE).apply();
        return identity;
    }

    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }

    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }

    public String getIssuerKeyId() {
        return issuerKeyId;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getKeystoreAlias() {
        return keystoreAlias;
    }

    public String getEncodedPublicKey() {
        return Base64Compat.encode(keyPair.getPublic().getEncoded());
    }

    public VoucherSignatureBundle.SignatureEntry createPrimarySignatureEntry(String payload) throws Exception {
        return new VoucherSignatureBundle.SignatureEntry(
                algorithm,
                issuerKeyId,
                getEncodedPublicKey(),
                VoucherSignatureAlgorithms.sign(algorithm, payload, keyPair.getPrivate()),
                "primary");
    }

    public VoucherSignatureBundle.SignatureEntry createSecondaryDetachedEntry(VoucherSecondSignatureManifest manifest) {
        VoucherSecondSignatureManifest resolvedManifest = manifest == null
                ? VoucherSecondSignatureManifest.fromLegacyPlaceholder(
                        VoucherSignatureAlgorithms.ALG_HYBRID_PLACEHOLDER,
                        issuerKeyId + ":hybrid",
                        "secondary-hybrid-placeholder")
                : manifest;
        return new VoucherSignatureBundle.SignatureEntry(
                resolvedManifest.getAlgorithm(),
                resolvedManifest.getSecondaryKeyId(),
                resolvedManifest.getDetachedPublicKey(),
                "",
                resolvedManifest.getPurpose(),
                resolvedManifest);
    }

    public VoucherSignatureBundle.SignatureEntry createSecondaryPlaceholderEntry() {
        return createSecondaryDetachedEntry(null);
    }

    private static PublicKey decodePublicKey(String algorithm, String encoded) throws Exception {
        return VoucherSignatureAlgorithms.decodePublicKey(algorithm, encoded);
    }

    private static PrivateKey decodePrivateKey(String algorithm, String encoded) throws Exception {
        return VoucherSignatureAlgorithms.decodePrivateKey(algorithm, encoded);
    }

    public static String fingerprint(PublicKey publicKey) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(publicKey.getEncoded());
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 8 && i < hash.length; i++) {
            builder.append(String.format(java.util.Locale.US, "%02X", hash[i]));
        }
        return builder.toString();
    }

    private static VoucherIssuerIdentity migrateLegacyPreferenceKeyPair(Context context, SharedPreferences prefs,
            String algorithm, String publicKeyEncoded, String privateKeyEncoded) throws Exception {
        VoucherIssuerIdentity rotatedIdentity = loadOrCreatePersistent(context);
        prefs.edit()
                .remove(KEY_PRIVATE)
                .putString(KEY_PUBLIC, rotatedIdentity.getEncodedPublicKey())
                .putString(KEY_ALGORITHM, rotatedIdentity.getAlgorithm())
                .putString(KEY_KEYSTORE_ALIAS, rotatedIdentity.getKeystoreAlias())
                .apply();
        return rotatedIdentity;
    }

    private static void persistMetadata(SharedPreferences prefs, VoucherIssuerIdentity identity) {
        prefs.edit()
                .putString(KEY_PUBLIC, identity.getEncodedPublicKey())
                .putString(KEY_ALGORITHM, identity.getAlgorithm())
                .putString(KEY_KEYSTORE_ALIAS, identity.getKeystoreAlias())
                .apply();
    }
}


