package org.servalproject.voucher;

import org.servalproject.util.Base64Compat;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class VoucherSignatureAlgorithms {
    public static final String ALG_RSA_SHA256 = "RSA_SHA256";
    public static final String ALG_ML_DSA_87 = "ML_DSA_87";
    public static final String ALG_HYBRID_PLACEHOLDER = "HYBRID_PLACEHOLDER";

    private static final Map<String, AlgorithmProfile> REGISTRY = buildRegistry();

    private VoucherSignatureAlgorithms() {
    }

    public static String normalize(String algorithm) {
        return algorithm == null ? "" : algorithm.trim().toUpperCase(java.util.Locale.US);
    }

    public static boolean isSupported(String algorithm) {
        AlgorithmProfile profile = REGISTRY.get(normalize(algorithm));
        return profile != null && profile.supportsVerification;
    }

    public static boolean isKnown(String algorithm) {
        return REGISTRY.containsKey(normalize(algorithm));
    }

    public static boolean supportsSigning(String algorithm) {
        AlgorithmProfile profile = REGISTRY.get(normalize(algorithm));
        return profile != null && profile.supportsSigning;
    }

    public static boolean isPlaceholder(String algorithm) {
        AlgorithmProfile profile = REGISTRY.get(normalize(algorithm));
        return profile != null && profile.placeholder;
    }

    public static KeyPair generateKeyPair(String algorithm) throws Exception {
        String normalizedAlgorithm = normalize(algorithm);
        if (ALG_RSA_SHA256.equals(normalizedAlgorithm)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }
        throw new UnsupportedOperationException("Unsupported voucher signature algorithm: " + algorithm);
    }

    public static String sign(String algorithm, String payload, PrivateKey privateKey) throws Exception {
        if (!supportsSigning(algorithm)) {
            throw new UnsupportedOperationException("Unsupported voucher signing algorithm: " + algorithm);
        }
        Signature signer = Signature.getInstance(getSignatureInstance(algorithm));
        signer.initSign(privateKey);
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64Compat.encode(signer.sign());
    }

    public static boolean verify(String algorithm, String payload, String encodedSignature, PublicKey publicKey) throws Exception {
        if (!isSupported(algorithm)) {
            throw new UnsupportedOperationException("Unsupported voucher verification algorithm: " + algorithm);
        }
        Signature verifier = Signature.getInstance(getSignatureInstance(algorithm));
        verifier.initVerify(publicKey);
        verifier.update(payload.getBytes(StandardCharsets.UTF_8));
        return verifier.verify(Base64Compat.decode(encodedSignature));
    }

    public static VoucherSignatureBundle.SignatureEntry createPlaceholderEntry(String keyId, String purpose) {
        return new VoucherSignatureBundle.SignatureEntry(
                ALG_HYBRID_PLACEHOLDER,
                keyId,
                "",
                "",
                purpose == null || purpose.trim().isEmpty() ? "hybrid-placeholder" : purpose);
    }

    public static String encodePublicKey(PublicKey publicKey) {
        return Base64Compat.encode(publicKey.getEncoded());
    }

    public static PublicKey decodePublicKey(String algorithm, String encodedPublicKey) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance(getKeyFactoryAlgorithm(algorithm));
        return keyFactory.generatePublic(new X509EncodedKeySpec(Base64Compat.decode(encodedPublicKey)));
    }

    public static PrivateKey decodePrivateKey(String algorithm, String encodedPrivateKey) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance(getKeyFactoryAlgorithm(algorithm));
        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(Base64Compat.decode(encodedPrivateKey)));
    }

    private static String getSignatureInstance(String algorithm) {
        String normalizedAlgorithm = normalize(algorithm);
        if (ALG_RSA_SHA256.equals(normalizedAlgorithm)) {
            return "SHA256withRSA";
        }
        throw new UnsupportedOperationException("Unsupported voucher signature algorithm: " + algorithm);
    }

    private static String getKeyFactoryAlgorithm(String algorithm) {
        String normalizedAlgorithm = normalize(algorithm);
        if (ALG_RSA_SHA256.equals(normalizedAlgorithm)) {
            return "RSA";
        }
        throw new UnsupportedOperationException("Unsupported voucher signature algorithm: " + algorithm);
    }

    private static Map<String, AlgorithmProfile> buildRegistry() {
        HashMap<String, AlgorithmProfile> registry = new HashMap<String, AlgorithmProfile>();
        registry.put(ALG_RSA_SHA256, new AlgorithmProfile(ALG_RSA_SHA256, true, true, false));
        registry.put(ALG_ML_DSA_87, new AlgorithmProfile(ALG_ML_DSA_87, false, false, false));
        registry.put(ALG_HYBRID_PLACEHOLDER, new AlgorithmProfile(ALG_HYBRID_PLACEHOLDER, false, false, true));
        return Collections.unmodifiableMap(registry);
    }

    private static final class AlgorithmProfile {
        final String name;
        final boolean supportsSigning;
        final boolean supportsVerification;
        final boolean placeholder;

        AlgorithmProfile(String name, boolean supportsSigning, boolean supportsVerification, boolean placeholder) {
            this.name = name;
            this.supportsSigning = supportsSigning;
            this.supportsVerification = supportsVerification;
            this.placeholder = placeholder;
        }
    }
}

