package org.servalproject.voucher;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
public final class VoucherSignatureBundle {
    public static final int CURRENT_VERSION = 2;
    private static final String HASH_ALGORITHM = "SHA-256";
    private final int version;
    private final String payloadHashAlgorithm;
    private final String payloadHash;
    private final ArrayList<SignatureEntry> signatures;
    public VoucherSignatureBundle(int version, String payloadHashAlgorithm, String payloadHash,
            List<SignatureEntry> signatures) {
        this.version = version;
        this.payloadHashAlgorithm = (payloadHashAlgorithm == null || payloadHashAlgorithm.trim().isEmpty())
                ? HASH_ALGORITHM
                : payloadHashAlgorithm;
        this.payloadHash = payloadHash == null ? "" : payloadHash;
        this.signatures = new ArrayList<SignatureEntry>(signatures == null ? Collections.<SignatureEntry>emptyList() : signatures);
    }
    public static VoucherSignatureBundle singleSignature(String payload, SignatureEntry signatureEntry) throws Exception {
        ArrayList<SignatureEntry> entries = new ArrayList<SignatureEntry>();
        entries.add(signatureEntry);
        return new VoucherSignatureBundle(CURRENT_VERSION, HASH_ALGORITHM, sha256Hex(payload), entries);
    }

    public static VoucherSignatureBundle forPayload(String payload, List<SignatureEntry> signatureEntries) throws Exception {
        return new VoucherSignatureBundle(CURRENT_VERSION, HASH_ALGORITHM, sha256Hex(payload), signatureEntries);
    }
    public int getVersion() {
        return version;
    }
    public String getPayloadHashAlgorithm() {
        return payloadHashAlgorithm;
    }
    public String getPayloadHash() {
        return payloadHash;
    }
    public List<SignatureEntry> getSignatures() {
        return new ArrayList<SignatureEntry>(signatures);
    }
    public SignatureEntry getPrimarySignature() {
        return signatures.isEmpty() ? null : signatures.get(0);
    }

    public VoucherSecondSignatureManifest getSecondSignatureManifest() {
        for (SignatureEntry signatureEntry : signatures) {
            if (signatureEntry.secondSignatureManifest != null) {
                return signatureEntry.secondSignatureManifest;
            }
        }
        return null;
    }

    public String toJson() throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append('{')
                .append("\"version\":").append(version)
                .append(",\"payloadHashAlgorithm\":\"").append(jsonEscape(payloadHashAlgorithm)).append('\"')
                .append(",\"payloadHash\":\"").append(jsonEscape(payloadHash)).append('\"')
                .append(",\"signatures\":[");
        for (int i = 0; i < signatures.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(signatures.get(i).toJsonString());
        }
        builder.append("]}");
        return builder.toString();
    }
    public static VoucherSignatureBundle fromJson(String jsonString) throws Exception {
        JSONObject json = new JSONObject(jsonString);
        JSONArray signaturesArray = json.optJSONArray("signatures");
        ArrayList<SignatureEntry> parsedEntries = new ArrayList<SignatureEntry>();
        if (signaturesArray != null) {
            for (int i = 0; i < signaturesArray.length(); i++) {
                parsedEntries.add(SignatureEntry.fromJsonObject(signaturesArray.getJSONObject(i)));
            }
        }
        return new VoucherSignatureBundle(
                json.optInt("version", CURRENT_VERSION),
                json.optString("payloadHashAlgorithm", HASH_ALGORITHM),
                json.optString("payloadHash", ""),
                parsedEntries);
    }
    public VerificationResult verify(String payload) {
        try {
            if (!HASH_ALGORITHM.equalsIgnoreCase(payloadHashAlgorithm)) {
                return VerificationResult.failure("Voucher payload hash algorithm unsupported: " + payloadHashAlgorithm);
            }
            String calculatedHash = sha256Hex(payload);
            boolean payloadHashMatches = calculatedHash.equalsIgnoreCase(payloadHash);
            if (signatures.isEmpty()) {
                return VerificationResult.failure("Voucher signature bundle is empty");
            }
            for (SignatureEntry signatureEntry : signatures) {
                if (signatureEntry.secondSignatureManifest != null) {
                    VoucherSecondSignatureManifest.ValidationResult manifestValidation =
                            signatureEntry.secondSignatureManifest.validateStructure();
                    if (!manifestValidation.isValid) {
                        return VerificationResult.failure(manifestValidation.message);
                    }
                    if (!signatureEntry.secondSignatureManifest.matchesPayloadHash(payloadHashAlgorithm, payloadHash)) {
                        return VerificationResult.failure("Voucher second-signature manifest hash mismatch");
                    }
                }
            }
            boolean sawSupportedAlgorithm = false;
            for (SignatureEntry signatureEntry : signatures) {
                if (!VoucherSignatureAlgorithms.isSupported(signatureEntry.algorithm)) {
                    continue;
                }
                sawSupportedAlgorithm = true;
                if (signatureEntry.publicKey == null || signatureEntry.publicKey.trim().isEmpty()
                        || signatureEntry.signature == null || signatureEntry.signature.trim().isEmpty()) {
                    continue;
                }
                java.security.PublicKey publicKey = VoucherSignatureAlgorithms.decodePublicKey(signatureEntry.algorithm,
                        signatureEntry.publicKey);
                String calculatedKeyId = VoucherIssuerIdentity.fingerprint(publicKey);
                if (signatureEntry.keyId != null && !signatureEntry.keyId.trim().isEmpty()
                        && !calculatedKeyId.equalsIgnoreCase(signatureEntry.keyId)) {
                    continue;
                }
                if (VoucherSignatureAlgorithms.verify(signatureEntry.algorithm, payload, signatureEntry.signature, publicKey)) {
                    return VerificationResult.success(signatureEntry, calculatedKeyId);
                }
            }
            if (!sawSupportedAlgorithm) {
                return VerificationResult.failure("Voucher signature uses unsupported algorithm");
            }
            if (!payloadHashMatches) {
                return VerificationResult.failure("Voucher payload hash mismatch");
            }
            return VerificationResult.failure("Voucher signature verification failed");
        } catch (Exception e) {
            return VerificationResult.failure("Voucher signature verification failed: " + e.getMessage());
        }
    }
    private static String sha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) {
            builder.append(String.format(java.util.Locale.US, "%02x", b));
        }
        return builder.toString();
    }
    public static final class SignatureEntry {
        public final String algorithm;
        public final String keyId;
        public final String publicKey;
        public final String signature;
        public final String purpose;
        public final VoucherSecondSignatureManifest secondSignatureManifest;
        public SignatureEntry(String algorithm, String keyId, String publicKey, String signature, String purpose) {
            this(algorithm, keyId, publicKey, signature, purpose, null);
        }
        public SignatureEntry(String algorithm, String keyId, String publicKey, String signature,
                String purpose, VoucherSecondSignatureManifest secondSignatureManifest) {
            this.algorithm = VoucherSignatureAlgorithms.normalize(algorithm);
            this.keyId = keyId;
            this.publicKey = publicKey;
            this.signature = signature;
            this.purpose = purpose;
            this.secondSignatureManifest = secondSignatureManifest;
        }
        private String toJsonString() {
            StringBuilder builder = new StringBuilder()
                    .append('{')
                    .append("\"algorithm\":\"").append(jsonEscape(algorithm)).append('\"')
                    .append(",\"keyId\":\"").append(jsonEscape(keyId)).append('\"')
                    .append(",\"publicKey\":\"").append(jsonEscape(publicKey)).append('\"')
                    .append(",\"signature\":\"").append(jsonEscape(signature)).append('\"')
                    .append(",\"purpose\":\"").append(jsonEscape(purpose)).append('\"');
            if (secondSignatureManifest != null) {
                builder.append(",\"manifest\":").append(secondSignatureManifest.toJson());
            }
            return builder.append('}').toString();
        }
        private static SignatureEntry fromJsonObject(JSONObject json) {
            VoucherSecondSignatureManifest manifest = null;
            JSONObject manifestJson = json.optJSONObject("manifest");
            if (manifestJson != null) {
                manifest = VoucherSecondSignatureManifest.fromJsonObject(manifestJson);
            } else if (VoucherSignatureAlgorithms.isPlaceholder(json.optString("algorithm", ""))
                    || json.optString("purpose", "").toLowerCase(java.util.Locale.US).contains("secondary")) {
                manifest = VoucherSecondSignatureManifest.fromLegacyPlaceholder(
                        json.optString("algorithm", ""),
                        json.optString("keyId", ""),
                        json.optString("purpose", "secondary"));
            }
            return new SignatureEntry(
                    json.optString("algorithm", ""),
                    json.optString("keyId", ""),
                    json.optString("publicKey", ""),
                    json.optString("signature", ""),
                    json.optString("purpose", "primary"),
                    manifest);
        }
    }
    public static final class VerificationResult {
        public final boolean isValid;
        public final String message;
        public final SignatureEntry verifiedSignature;
        public final String resolvedKeyId;
        private VerificationResult(boolean isValid, String message, SignatureEntry verifiedSignature, String resolvedKeyId) {
            this.isValid = isValid;
            this.message = message;
            this.verifiedSignature = verifiedSignature;
            this.resolvedKeyId = resolvedKeyId;
        }
        public static VerificationResult success(SignatureEntry signatureEntry, String resolvedKeyId) {
            return new VerificationResult(true, "Voucher signature verified", signatureEntry, resolvedKeyId);
        }
        public static VerificationResult failure(String message) {
            return new VerificationResult(false, message, null, null);
        }
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
