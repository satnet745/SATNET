package org.servalproject.voucher;

import android.content.Context;
import android.content.SharedPreferences;

public final class VoucherIssuerRotationPolicy {
    private static final String PREFS_NAME = "satnet_voucher_issuer";
    private static final String KEY_ALGORITHM = "algorithm";
    private static final String KEY_KEYSTORE_ALIAS = "keystore_alias";
    private static final String KEY_ROTATION_EPOCH = "rotation_epoch";
    private static final String KEY_ACTIVE_ALIAS_CREATED_AT = "active_alias_created_at";
    private static final String KEY_PREVIOUS_KEYSTORE_ALIAS = "previous_keystore_alias";
    private static final String KEY_LAST_ROTATION_REASON = "last_rotation_reason";
    private static final String DEFAULT_ALIAS = "satnet_voucher_issuer_default";
    static final long DEFAULT_MAX_KEY_AGE_MILLIS = 90L * 24L * 60L * 60L * 1000L;
    static final int DEFAULT_MAX_ISSUANCES_PER_ALIAS = 250;

    private VoucherIssuerRotationPolicy() {
    }

    public static ActiveIssuerState resolve(Context context, VoucherLedger ledger) throws Exception {
        return resolve(context, ledger, System.currentTimeMillis(), DEFAULT_MAX_KEY_AGE_MILLIS, DEFAULT_MAX_ISSUANCES_PER_ALIAS);
    }

    static ActiveIssuerState resolve(Context context,
            VoucherLedger ledger,
            long nowMs,
            long maxKeyAgeMillis,
            int maxIssuancesPerAlias) throws Exception {
        if (context == null) {
            throw new IllegalArgumentException("Context is required for voucher issuer rotation policy");
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String algorithm = prefs.getString(KEY_ALGORITHM, VoucherSignatureAlgorithms.ALG_RSA_SHA256);
        String alias = prefs.getString(KEY_KEYSTORE_ALIAS, DEFAULT_ALIAS);
        if (alias == null || alias.trim().isEmpty()) {
            alias = DEFAULT_ALIAS;
        }
        long rotationEpoch = Math.max(0L, prefs.getLong(KEY_ROTATION_EPOCH, 0L));
        long activeAliasCreatedAt = Math.max(0L, prefs.getLong(KEY_ACTIVE_ALIAS_CREATED_AT, 0L));
        if (activeAliasCreatedAt == 0L) {
            activeAliasCreatedAt = nowMs;
            prefs.edit().putLong(KEY_ACTIVE_ALIAS_CREATED_AT, activeAliasCreatedAt).apply();
        }
        int issuedCount = ledger == null ? 0 : ledger.getIssuedVoucherCountForIssuerAlias(alias);
        RotationDecision decision = evaluateRotation(nowMs,
                alias,
                rotationEpoch,
                activeAliasCreatedAt,
                issuedCount,
                maxKeyAgeMillis,
                maxIssuancesPerAlias);
        if (decision.rotate) {
            alias = buildAlias(decision.rotationEpoch, nowMs);
            rotationEpoch = decision.rotationEpoch;
            activeAliasCreatedAt = nowMs;
            prefs.edit()
                    .putString(KEY_PREVIOUS_KEYSTORE_ALIAS, decision.previousAlias)
                    .putString(KEY_LAST_ROTATION_REASON, decision.reason)
                    .putString(KEY_KEYSTORE_ALIAS, alias)
                    .putLong(KEY_ROTATION_EPOCH, rotationEpoch)
                    .putLong(KEY_ACTIVE_ALIAS_CREATED_AT, activeAliasCreatedAt)
                    .apply();
            issuedCount = 0;
        } else {
            prefs.edit()
                    .putString(KEY_KEYSTORE_ALIAS, alias)
                    .putLong(KEY_ROTATION_EPOCH, rotationEpoch)
                    .putLong(KEY_ACTIVE_ALIAS_CREATED_AT, activeAliasCreatedAt)
                    .apply();
        }
        VoucherIssuerIdentity identity = VoucherIssuerIdentity.loadOrCreatePersistent(context, alias, algorithm);
        return new ActiveIssuerState(identity,
                rotationEpoch,
                activeAliasCreatedAt,
                issuedCount,
                decision.rotate,
                decision.reason,
                decision.previousAlias,
                maxKeyAgeMillis,
                maxIssuancesPerAlias);
    }

    private static RotationDecision evaluateRotation(long nowMs,
            String alias,
            long rotationEpoch,
            long activeAliasCreatedAt,
            int issuedCount,
            long maxKeyAgeMillis,
            int maxIssuancesPerAlias) {
        if (maxIssuancesPerAlias > 0 && issuedCount >= maxIssuancesPerAlias) {
            return new RotationDecision(true, rotationEpoch + 1L, alias, "issuance-threshold");
        }
        if (maxKeyAgeMillis > 0L && activeAliasCreatedAt > 0L && nowMs - activeAliasCreatedAt >= maxKeyAgeMillis) {
            return new RotationDecision(true, rotationEpoch + 1L, alias, "age-threshold");
        }
        return new RotationDecision(false, rotationEpoch, alias, "active");
    }

    private static String buildAlias(long rotationEpoch, long nowMs) {
        return "satnet_voucher_issuer_r" + rotationEpoch + "_" + nowMs;
    }

    public static final class ActiveIssuerState {
        private final VoucherIssuerIdentity identity;
        private final long rotationEpoch;
        private final long activatedAt;
        private final int issuedVoucherCount;
        private final boolean rotated;
        private final String rotationReason;
        private final String previousAlias;
        private final long maxKeyAgeMillis;
        private final int maxIssuancesPerAlias;

        private ActiveIssuerState(VoucherIssuerIdentity identity,
                long rotationEpoch,
                long activatedAt,
                int issuedVoucherCount,
                boolean rotated,
                String rotationReason,
                String previousAlias,
                long maxKeyAgeMillis,
                int maxIssuancesPerAlias) {
            this.identity = identity;
            this.rotationEpoch = rotationEpoch;
            this.activatedAt = activatedAt;
            this.issuedVoucherCount = issuedVoucherCount;
            this.rotated = rotated;
            this.rotationReason = rotationReason;
            this.previousAlias = previousAlias;
            this.maxKeyAgeMillis = maxKeyAgeMillis;
            this.maxIssuancesPerAlias = maxIssuancesPerAlias;
        }

        public VoucherIssuerIdentity getIdentity() {
            return identity;
        }

        public long getRotationEpoch() {
            return rotationEpoch;
        }

        public long getActivatedAt() {
            return activatedAt;
        }

        public int getIssuedVoucherCount() {
            return issuedVoucherCount;
        }

        public boolean isRotated() {
            return rotated;
        }

        public String getRotationReason() {
            return rotationReason;
        }

        public String getPreviousAlias() {
            return previousAlias;
        }

        public long getMaxKeyAgeMillis() {
            return maxKeyAgeMillis;
        }

        public int getMaxIssuancesPerAlias() {
            return maxIssuancesPerAlias;
        }

        public VoucherSecondSignatureManifest createSecondSignatureManifest(String payload) throws Exception {
            String alias = identity.getKeystoreAlias();
            String referenceToken = sanitizeReferenceToken(alias.length() > 0 ? alias : identity.getIssuerKeyId());
            String detachedPublicKeyReference = "keystore://" + referenceToken + "#pq-public";
            String metadataReference = "ledger://voucher-issuer/" + referenceToken + "/rotation/" + rotationEpoch + "/pq-metadata";
            String detachedSignatureReference = "ledger://voucher-issuer/" + referenceToken + "/rotation/" + rotationEpoch + "/pq-signature";
            String metadataDigest = VoucherSecondSignatureManifest.shortDigest(
                    metadataReference + "|" + detachedSignatureReference + "|" + activatedAt + "|" + rotationReason);
            return VoucherSecondSignatureManifest.createDetachedManifest(
                    VoucherSignatureAlgorithms.ALG_ML_DSA_87,
                    payload,
                    identity.getIssuerKeyId(),
                    "",
                    detachedPublicKeyReference,
                    detachedSignatureReference,
                    metadataReference,
                    metadataDigest,
                    alias,
                    previousAlias,
                    rotationReason,
                    rotationEpoch,
                    activatedAt);
        }

        private static String sanitizeReferenceToken(String token) {
            return (token == null ? "issuer" : token.trim()).replaceAll("[^A-Za-z0-9._-]", "_");
        }
    }

    private static final class RotationDecision {
        final boolean rotate;
        final long rotationEpoch;
        final String previousAlias;
        final String reason;

        RotationDecision(boolean rotate, long rotationEpoch, String previousAlias, String reason) {
            this.rotate = rotate;
            this.rotationEpoch = rotationEpoch;
            this.previousAlias = previousAlias;
            this.reason = reason;
        }
    }
}

