package org.servalproject.voucher;

import org.json.JSONObject;
import org.json.JSONException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class VoucherSecondSignatureManifest {
    public static final int CURRENT_VERSION = 1;
    public static final String TYPE_SECOND_SIGNATURE = "second-signature";
    public static final String PURPOSE_SECONDARY_DETACHED = "secondary-detached-manifest";
    public static final String STATUS_ANNOUNCED = "announced";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_LEGACY_PLACEHOLDER = "legacy-placeholder";
    public static final String BINDING_DETACHED_METADATA = "detached-metadata";
    public static final String BINDING_DETACHED_PUBLIC_KEY = "detached-public-key";
    public static final String BINDING_DETACHED_PUBLIC_KEY_REFERENCE = "detached-public-key-reference";
    public static final String HASH_ALGORITHM_SHA256 = "SHA-256";

    private final int version;
    private final String type;
    private final String algorithm;
    private final String purpose;
    private final String status;
    private final String bindingMode;
    private final String payloadHashAlgorithm;
    private final String payloadHash;
    private final String primaryIssuerKeyId;
    private final String secondaryKeyId;
    private final String detachedPublicKey;
    private final String detachedPublicKeyReference;
    private final String detachedSignatureReference;
    private final String metadataReference;
    private final String metadataDigest;
    private final String issuerKeystoreAlias;
    private final String previousIssuerKeystoreAlias;
    private final String rotationReason;
    private final long rotationEpoch;
    private final long createdAt;

    public VoucherSecondSignatureManifest(int version,
            String type,
            String algorithm,
            String purpose,
            String status,
            String bindingMode,
            String payloadHashAlgorithm,
            String payloadHash,
            String primaryIssuerKeyId,
            String secondaryKeyId,
            String detachedPublicKey,
            String detachedPublicKeyReference,
            String detachedSignatureReference,
            String metadataReference,
            String metadataDigest,
            String issuerKeystoreAlias,
            String previousIssuerKeystoreAlias,
            String rotationReason,
            long rotationEpoch,
            long createdAt) {
        this.version = version <= 0 ? CURRENT_VERSION : version;
        this.type = normalizeOrDefault(type, TYPE_SECOND_SIGNATURE);
        this.algorithm = VoucherSignatureAlgorithms.normalize(algorithm);
        this.purpose = normalizeOrDefault(purpose, PURPOSE_SECONDARY_DETACHED);
        this.status = normalizeOrDefault(status, STATUS_ANNOUNCED);
        this.bindingMode = normalizeBindingMode(bindingMode, detachedPublicKey, detachedPublicKeyReference);
        this.payloadHashAlgorithm = normalizeOrDefault(payloadHashAlgorithm, HASH_ALGORITHM_SHA256);
        this.payloadHash = normalizeOrDefault(payloadHash, "");
        this.primaryIssuerKeyId = normalizeOrDefault(primaryIssuerKeyId, "");
        this.secondaryKeyId = normalizeOrDefault(resolveSecondaryKeyId(secondaryKeyId,
                detachedPublicKeyReference,
                detachedPublicKey,
                primaryIssuerKeyId), "");
        this.detachedPublicKey = normalizeOrDefault(detachedPublicKey, "");
        this.detachedPublicKeyReference = normalizeOrDefault(detachedPublicKeyReference, "");
        this.detachedSignatureReference = normalizeOrDefault(detachedSignatureReference, "");
        this.metadataReference = normalizeOrDefault(metadataReference, "");
        this.metadataDigest = normalizeOrDefault(metadataDigest, "");
        this.issuerKeystoreAlias = normalizeOrDefault(issuerKeystoreAlias, "");
        this.previousIssuerKeystoreAlias = normalizeOrDefault(previousIssuerKeystoreAlias, "");
        this.rotationReason = normalizeOrDefault(rotationReason, "active");
        this.rotationEpoch = Math.max(0L, rotationEpoch);
        this.createdAt = Math.max(0L, createdAt);
    }

    public static VoucherSecondSignatureManifest createDetachedManifest(String algorithm,
            String payload,
            String primaryIssuerKeyId,
            String detachedPublicKey,
            String detachedPublicKeyReference,
            String detachedSignatureReference,
            String metadataReference,
            String metadataDigest,
            String issuerKeystoreAlias,
            long rotationEpoch,
            long createdAt) throws Exception {
        return createDetachedManifest(
                algorithm,
                payload,
                primaryIssuerKeyId,
                detachedPublicKey,
                detachedPublicKeyReference,
                detachedSignatureReference,
                metadataReference,
                metadataDigest,
                issuerKeystoreAlias,
                "",
                "active",
                rotationEpoch,
                createdAt);
    }

    public static VoucherSecondSignatureManifest createDetachedManifest(String algorithm,
            String payload,
            String primaryIssuerKeyId,
            String detachedPublicKey,
            String detachedPublicKeyReference,
            String detachedSignatureReference,
            String metadataReference,
            String metadataDigest,
            String issuerKeystoreAlias,
            String previousIssuerKeystoreAlias,
            String rotationReason,
            long rotationEpoch,
            long createdAt) throws Exception {
        return new VoucherSecondSignatureManifest(
                CURRENT_VERSION,
                TYPE_SECOND_SIGNATURE,
                algorithm,
                PURPOSE_SECONDARY_DETACHED,
                STATUS_ANNOUNCED,
                chooseBindingMode(detachedPublicKey, detachedPublicKeyReference),
                HASH_ALGORITHM_SHA256,
                sha256Hex(payload),
                primaryIssuerKeyId,
                null,
                detachedPublicKey,
                detachedPublicKeyReference,
                detachedSignatureReference,
                metadataReference,
                metadataDigest,
                issuerKeystoreAlias,
                previousIssuerKeystoreAlias,
                rotationReason,
                rotationEpoch,
                createdAt);
    }

    public static VoucherSecondSignatureManifest fromLegacyPlaceholder(String algorithm, String keyId, String purpose) {
        return new VoucherSecondSignatureManifest(
                CURRENT_VERSION,
                TYPE_SECOND_SIGNATURE,
                algorithm,
                purpose,
                STATUS_LEGACY_PLACEHOLDER,
                BINDING_DETACHED_METADATA,
                HASH_ALGORITHM_SHA256,
                "",
                "",
                keyId,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                STATUS_LEGACY_PLACEHOLDER,
                0L,
                0L);
    }

    public int getVersion() {
        return version;
    }

    public String getType() {
        return type;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getStatus() {
        return status;
    }

    public String getBindingMode() {
        return bindingMode;
    }

    public String getPayloadHashAlgorithm() {
        return payloadHashAlgorithm;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public String getPrimaryIssuerKeyId() {
        return primaryIssuerKeyId;
    }

    public String getSecondaryKeyId() {
        return secondaryKeyId;
    }

    public String getDetachedPublicKey() {
        return detachedPublicKey;
    }

    public String getDetachedPublicKeyReference() {
        return detachedPublicKeyReference;
    }

    public String getDetachedSignatureReference() {
        return detachedSignatureReference;
    }

    public String getMetadataReference() {
        return metadataReference;
    }

    public String getMetadataDigest() {
        return metadataDigest;
    }

    public String getIssuerKeystoreAlias() {
        return issuerKeystoreAlias;
    }

    public String getPreviousIssuerKeystoreAlias() {
        return previousIssuerKeystoreAlias;
    }

    public String getRotationReason() {
        return rotationReason;
    }

    public long getRotationEpoch() {
        return rotationEpoch;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean matchesPayloadHash(String expectedAlgorithm, String expectedHash) {
        return normalizeOrDefault(expectedAlgorithm, HASH_ALGORITHM_SHA256).equalsIgnoreCase(payloadHashAlgorithm)
                && normalizeOrDefault(expectedHash, "").equalsIgnoreCase(payloadHash);
    }

    public ValidationResult validateStructure() {
        if (version <= 0) {
            return ValidationResult.failure("Voucher second-signature manifest version invalid");
        }
        if (!TYPE_SECOND_SIGNATURE.equals(type)) {
            return ValidationResult.failure("Voucher second-signature manifest type invalid");
        }
        if (STATUS_LEGACY_PLACEHOLDER.equals(status)) {
            return ValidationResult.success("Voucher second-signature manifest is legacy placeholder metadata");
        }
        if (!VoucherSignatureAlgorithms.isKnown(algorithm)
                || VoucherSignatureAlgorithms.isPlaceholder(algorithm)) {
            return ValidationResult.failure("Voucher second-signature manifest algorithm invalid");
        }
        if (!PURPOSE_SECONDARY_DETACHED.equals(purpose)) {
            return ValidationResult.failure("Voucher second-signature manifest purpose invalid");
        }
        if (!STATUS_ANNOUNCED.equals(status) && !STATUS_ACTIVE.equals(status)) {
            return ValidationResult.failure("Voucher second-signature manifest status invalid");
        }
        if (!HASH_ALGORITHM_SHA256.equalsIgnoreCase(payloadHashAlgorithm) || payloadHash.isEmpty()) {
            return ValidationResult.failure("Voucher second-signature manifest payload hash invalid");
        }
        if (primaryIssuerKeyId.isEmpty()) {
            return ValidationResult.failure("Voucher second-signature manifest primary issuer key missing");
        }
        if (secondaryKeyId.isEmpty()) {
            return ValidationResult.failure("Voucher second-signature manifest secondary key reference missing");
        }
        if (issuerKeystoreAlias.isEmpty()) {
            return ValidationResult.failure("Voucher second-signature manifest issuer alias missing");
        }
        if (metadataDigest.isEmpty()) {
            return ValidationResult.failure("Voucher second-signature manifest metadata digest missing");
        }
        if (detachedSignatureReference.isEmpty() || !looksLikeReference(detachedSignatureReference)) {
            return ValidationResult.failure("Voucher second-signature manifest detached signature reference invalid");
        }
        if (metadataReference.isEmpty() || !looksLikeReference(metadataReference)) {
            return ValidationResult.failure("Voucher second-signature manifest metadata reference invalid");
        }
        if (createdAt <= 0L) {
            return ValidationResult.failure("Voucher second-signature manifest creation time missing");
        }
        if (rotationReason.isEmpty()) {
            return ValidationResult.failure("Voucher second-signature manifest rotation reason missing");
        }
        if (BINDING_DETACHED_PUBLIC_KEY.equals(bindingMode)) {
            if (detachedPublicKey.isEmpty()) {
                return ValidationResult.failure("Voucher second-signature manifest detached public key missing");
            }
        } else if (BINDING_DETACHED_PUBLIC_KEY_REFERENCE.equals(bindingMode)) {
            if (detachedPublicKeyReference.isEmpty() || !looksLikeReference(detachedPublicKeyReference)) {
                return ValidationResult.failure("Voucher second-signature manifest detached public key reference invalid");
            }
        } else if (BINDING_DETACHED_METADATA.equals(bindingMode)) {
            if (metadataReference.isEmpty()) {
                return ValidationResult.failure("Voucher second-signature manifest metadata binding missing");
            }
        } else {
            return ValidationResult.failure("Voucher second-signature manifest binding mode invalid");
        }
        return ValidationResult.success("Voucher second-signature manifest structure valid");
    }

    public JSONObject toJsonObject() {
        try {
            JSONObject json = new JSONObject();
            json.put("version", version);
            json.put("type", type);
            json.put("algorithm", algorithm);
            json.put("purpose", purpose);
            json.put("status", status);
            json.put("bindingMode", bindingMode);
            json.put("payloadHashAlgorithm", payloadHashAlgorithm);
            json.put("payloadHash", payloadHash);
            json.put("primaryIssuerKeyId", primaryIssuerKeyId);
            json.put("secondaryKeyId", secondaryKeyId);
            json.put("detachedPublicKey", detachedPublicKey);
            json.put("detachedPublicKeyReference", detachedPublicKeyReference);
            json.put("detachedSignatureReference", detachedSignatureReference);
            json.put("metadataReference", metadataReference);
            json.put("metadataDigest", metadataDigest);
            json.put("issuerKeystoreAlias", issuerKeystoreAlias);
            json.put("previousIssuerKeystoreAlias", previousIssuerKeystoreAlias);
            json.put("rotationReason", rotationReason);
            json.put("rotationEpoch", rotationEpoch);
            json.put("createdAt", createdAt);
            return json;
        } catch (JSONException e) {
            throw new IllegalStateException("Unable to serialize voucher second-signature manifest", e);
        }
    }

    public String toJson() {
        return new StringBuilder()
                .append('{')
                .append("\"version\":").append(version)
                .append(",\"type\":\"").append(jsonEscape(type)).append('"')
                .append(",\"algorithm\":\"").append(jsonEscape(algorithm)).append('"')
                .append(",\"purpose\":\"").append(jsonEscape(purpose)).append('"')
                .append(",\"status\":\"").append(jsonEscape(status)).append('"')
                .append(",\"bindingMode\":\"").append(jsonEscape(bindingMode)).append('"')
                .append(",\"payloadHashAlgorithm\":\"").append(jsonEscape(payloadHashAlgorithm)).append('"')
                .append(",\"payloadHash\":\"").append(jsonEscape(payloadHash)).append('"')
                .append(",\"primaryIssuerKeyId\":\"").append(jsonEscape(primaryIssuerKeyId)).append('"')
                .append(",\"secondaryKeyId\":\"").append(jsonEscape(secondaryKeyId)).append('"')
                .append(",\"detachedPublicKey\":\"").append(jsonEscape(detachedPublicKey)).append('"')
                .append(",\"detachedPublicKeyReference\":\"").append(jsonEscape(detachedPublicKeyReference)).append('"')
                .append(",\"detachedSignatureReference\":\"").append(jsonEscape(detachedSignatureReference)).append('"')
                .append(",\"metadataReference\":\"").append(jsonEscape(metadataReference)).append('"')
                .append(",\"metadataDigest\":\"").append(jsonEscape(metadataDigest)).append('"')
                .append(",\"issuerKeystoreAlias\":\"").append(jsonEscape(issuerKeystoreAlias)).append('"')
                .append(",\"previousIssuerKeystoreAlias\":\"").append(jsonEscape(previousIssuerKeystoreAlias)).append('"')
                .append(",\"rotationReason\":\"").append(jsonEscape(rotationReason)).append('"')
                .append(",\"rotationEpoch\":").append(rotationEpoch)
                .append(",\"createdAt\":").append(createdAt)
                .append('}')
                .toString();
    }

    public static VoucherSecondSignatureManifest fromJson(String jsonString) {
        try {
            return fromJsonObject(new JSONObject(jsonString));
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid voucher second-signature manifest JSON", e);
        }
    }

    public static VoucherSecondSignatureManifest fromJsonObject(JSONObject json) {
        if (json == null) {
            return null;
        }
        return new VoucherSecondSignatureManifest(
                json.optInt("version", CURRENT_VERSION),
                json.optString("type", TYPE_SECOND_SIGNATURE),
                json.optString("algorithm", ""),
                json.optString("purpose", PURPOSE_SECONDARY_DETACHED),
                json.optString("status", STATUS_ANNOUNCED),
                json.optString("bindingMode", BINDING_DETACHED_METADATA),
                json.optString("payloadHashAlgorithm", HASH_ALGORITHM_SHA256),
                json.optString("payloadHash", ""),
                json.optString("primaryIssuerKeyId", ""),
                json.optString("secondaryKeyId", ""),
                json.optString("detachedPublicKey", ""),
                json.optString("detachedPublicKeyReference", ""),
                json.optString("detachedSignatureReference", ""),
                json.optString("metadataReference", ""),
                json.optString("metadataDigest", ""),
                json.optString("issuerKeystoreAlias", ""),
                json.optString("previousIssuerKeystoreAlias", ""),
                json.optString("rotationReason", "active"),
                json.optLong("rotationEpoch", 0L),
                json.optLong("createdAt", 0L));
    }

    public static String shortDigest(String value) {
        try {
            return sha256Hex(value == null ? "" : value).substring(0, 16).toUpperCase(Locale.US);
        } catch (Exception e) {
            return "0000000000000000";
        }
    }

    private static String chooseBindingMode(String detachedPublicKey, String detachedPublicKeyReference) {
        return detachedPublicKey != null && detachedPublicKey.trim().length() > 0
                ? BINDING_DETACHED_PUBLIC_KEY
                : BINDING_DETACHED_PUBLIC_KEY_REFERENCE;
    }

    private static String normalizeBindingMode(String bindingMode, String detachedPublicKey, String detachedPublicKeyReference) {
        String normalized = normalizeOrDefault(bindingMode, "").toLowerCase(Locale.US);
        if (normalized.length() > 0) {
            return normalized;
        }
        return chooseBindingMode(detachedPublicKey, detachedPublicKeyReference);
    }

    private static String resolveSecondaryKeyId(String secondaryKeyId,
            String detachedPublicKeyReference,
            String detachedPublicKey,
            String primaryIssuerKeyId) {
        String normalized = normalizeOrDefault(secondaryKeyId, "");
        if (normalized.length() > 0) {
            return normalized;
        }
        if (detachedPublicKeyReference != null && detachedPublicKeyReference.trim().length() > 0) {
            return "PQREF-" + shortDigest(detachedPublicKeyReference);
        }
        if (detachedPublicKey != null && detachedPublicKey.trim().length() > 0) {
            return "PQPUB-" + shortDigest(detachedPublicKey);
        }
        if (primaryIssuerKeyId != null && primaryIssuerKeyId.trim().length() > 0) {
            return primaryIssuerKeyId + ":PQ";
        }
        return "PQ-UNBOUND";
    }

    private static String normalizeOrDefault(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() > 0 ? normalized : fallback;
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

    private static boolean looksLikeReference(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.contains("://") || normalized.startsWith("issuer://") || normalized.startsWith("keystore://");
    }

    public static final class ValidationResult {
        public final boolean isValid;
        public final String message;

        private ValidationResult(boolean isValid, String message) {
            this.isValid = isValid;
            this.message = message;
        }

        public static ValidationResult success(String message) {
            return new ValidationResult(true, message);
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message);
        }
    }

    private static String sha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM_SHA256);
        byte[] hash = digest.digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) {
            builder.append(String.format(Locale.US, "%02x", b));
        }
        return builder.toString();
    }
}


