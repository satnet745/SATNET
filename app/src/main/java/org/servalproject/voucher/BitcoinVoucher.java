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

package org.servalproject.voucher;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONObject;
import org.servalproject.satnet.SatnetRuntimeConfig;
import org.servalproject.util.Base64Compat;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.ArrayList;
import java.util.Locale;

/**
 * SATNET AFRICA Bitcoin Voucher System
 *
 * Implements cryptographically secure, time-limited, single-use vouchers for bidirectional
 * cash-to-Bitcoin conversion by agents in the field.
 *
 * Features:
 * - Bidirectional: BUY (cash→Bitcoin) & SELL (Bitcoin→cash)
 * - QR code encoding voucher secret
 * - Cryptographic validation (SHA256)
 * - Exchange rate locking (30-minute windows)
 * - Settlement verification (stage-aware window for SELL)
 * - Time-limited expiry
 * - Single-use enforcement via ledger
 * - Works offline (QR scanning, later sync)
 */
public class BitcoinVoucher {
    private static final String TAG = "BitcoinVoucher";
    private static final String QR_PREFIX = "satnet_voucher";
    private static final String QR_VERSION_V2 = "v2";
    private static final String BODY_PREFIX_V2 = "voucher_body_v2";
    private static final int PAYLOAD_VERSION_LEGACY = 1;
    private static final int PAYLOAD_VERSION_V2 = 2;

    // Voucher denominations (in satoshis)
    public static final long DENOM_1000_SAT = 1000L;
    public static final long DENOM_5000_SAT = 5000L;
    public static final long DENOM_10000_SAT = 10000L;
    public static final long DENOM_50000_SAT = 50000L;

    // Voucher directions
    public static final int DIRECTION_BUY = 1;    // User buys Bitcoin with cash
    public static final int DIRECTION_SELL = 2;   // User sells Bitcoin for cash

    // Voucher state constants
    public static final int STATE_ISSUED = 1;
    public static final int STATE_REDEEMED = 2;
    public static final int STATE_EXPIRED = 3;
    public static final int STATE_INVALID = 4;
    public static final int STATE_SETTLEMENT_PENDING = 5;  // SELL vouchers awaiting verification
    public static final int STATE_SETTLEMENT_VERIFIED = 6; // SELL verified, ready for release

    private String voucherId;
    private long denomination;
    private String agentId;
    private long issuedTime;
    private long expiryTime;
    private String secret;
    private String secretHash;
    private int state;
    private long redeemedTime;
    private String redeemedByWallet;

    // Bidirectional voucher fields
    private int direction = DIRECTION_BUY;  // Default: BUY
    private double exchangeRate;            // BTC/USD or BTC/local currency
    private long rateLockTime;              // 30-minute window from issuance
    private String currencyCode;            // USD, UGX, KES, TZS, ETB, etc.
    private long settlementVerifiedTime;    // When Verifier confirms SELL redemption
    private boolean settlementVerified = false; // Flag for SELL voucher verification
    private String issuerPublicKey;
    private String issuerKeyId;
    private String signature;
    private String legacyInteroperabilitySignature;
    private String primarySignatureAlgorithm;
    private int payloadVersion = PAYLOAD_VERSION_V2;
    private String canonicalPayload;
    private VoucherSignatureBundle signatureBundle;
    private VoucherSecondSignatureManifest secondSignatureManifest;
    private String issuerKeystoreAlias;
    private long issuerRotationEpoch;
    private long issuerActivatedAt;
    private String issuerPreviousKeystoreAlias;
    private String issuerRotationReason;

    /**
     * Generate new voucher for issuance by agent
     * @param agentId Agent's SATNET ID or wallet address
     * @param denomination Amount in satoshis
     * @param expiryHours Expiry time in hours from now
     */
    public static BitcoinVoucher generateNew(String agentId, long denomination, int expiryHours)
            throws Exception {
        return generateNew(agentId, denomination, expiryHours, DIRECTION_BUY, 0.0, "USD");
    }

    public static BitcoinVoucher generateNew(Context context, String agentId, long denomination, int expiryHours,
                                             int direction, double exchangeRate, String currencyCode)
            throws Exception {
        return generateSignedVoucher(agentId, denomination, expiryHours, direction, exchangeRate, currencyCode,
                VoucherIssuerIdentity.loadOrCreate(context));
    }

    public static BitcoinVoucher generateNew(String agentId, long denomination, int expiryHours,
                                             int direction, double exchangeRate, String currencyCode,
                                             VoucherIssuerRotationPolicy.ActiveIssuerState activeIssuerState)
            throws Exception {
        if (activeIssuerState == null) {
            throw new IllegalArgumentException("Active issuer state is required");
        }
        return generateSignedVoucher(agentId, denomination, expiryHours, direction, exchangeRate, currencyCode,
                activeIssuerState.getIdentity(), activeIssuerState);
    }

    /**
     * Generate new bidirectional voucher
     * @param agentId Agent's SATNET ID or wallet address
     * @param denomination Amount in satoshis
     * @param expiryHours Expiry time in hours from now
     * @param direction DIRECTION_BUY or DIRECTION_SELL
     * @param exchangeRate Current BTC/currency exchange rate
     * @param currencyCode Local currency code (USD, UGX, KES, TZS, etc.)
     */
    public static BitcoinVoucher generateNew(String agentId, long denomination, int expiryHours,
                                             int direction, double exchangeRate, String currencyCode)
            throws Exception {
        return generateSignedVoucher(agentId, denomination, expiryHours, direction, exchangeRate, currencyCode,
                VoucherIssuerIdentity.createEphemeral());
    }

    private static BitcoinVoucher generateSignedVoucher(String agentId, long denomination, int expiryHours,
                                                        int direction, double exchangeRate, String currencyCode,
                                                        VoucherIssuerIdentity issuerIdentity)
            throws Exception {
        return generateSignedVoucher(agentId, denomination, expiryHours, direction, exchangeRate, currencyCode,
                issuerIdentity, null);
    }

    private static BitcoinVoucher generateSignedVoucher(String agentId, long denomination, int expiryHours,
                                                        int direction, double exchangeRate, String currencyCode,
                                                        VoucherIssuerIdentity issuerIdentity,
                                                        VoucherIssuerRotationPolicy.ActiveIssuerState activeIssuerState)
            throws Exception {
        BitcoinVoucher voucher = new BitcoinVoucher();
        String normalizedAgentId = agentId == null ? "agent" : agentId.trim();
        if (normalizedAgentId.isEmpty()) {
            normalizedAgentId = "agent";
        }
        if (denomination <= 0) {
            throw new IllegalArgumentException("Voucher denomination must be greater than zero");
        }
        if (expiryHours < 0) {
            throw new IllegalArgumentException("Voucher expiry cannot be negative");
        }
        if (direction != DIRECTION_BUY && direction != DIRECTION_SELL) {
            throw new IllegalArgumentException("Unsupported voucher direction: " + direction);
        }
        if (exchangeRate < 0) {
            throw new IllegalArgumentException("Exchange rate cannot be negative");
        }
        String normalizedCurrencyCode = currencyCode == null ? "USD" : currencyCode.trim().toUpperCase(Locale.US);
        if (normalizedCurrencyCode.isEmpty()) {
            normalizedCurrencyCode = "USD";
        }

        // Generate unique secret (128 bits = 16 bytes)
        byte[] secretBytes = new byte[16];
        SecureRandom random = new SecureRandom();
        random.nextBytes(secretBytes);
        voucher.secret = bytesToHex(secretBytes);

        // Generate voucher ID (agent_id + timestamp + random)
        long timestamp = System.currentTimeMillis();
        String safeAgentPrefix = normalizedAgentId.replaceAll("[^A-Za-z0-9]", "");
        if (safeAgentPrefix.isEmpty()) {
            safeAgentPrefix = "agent";
        }
        String agentPrefix = safeAgentPrefix.length() > 8
                ? safeAgentPrefix.substring(0, 8)
                : safeAgentPrefix;
        String randomSuffix = String.format(Locale.US, "%04x", random.nextInt(0x10000));
        voucher.voucherId = agentPrefix + "_" + timestamp + "_" + randomSuffix;

        voucher.denomination = denomination;
        voucher.agentId = normalizedAgentId;
        voucher.issuedTime = timestamp;
        voucher.expiryTime = timestamp + (expiryHours * 3600000L);

        // Bidirectional fields
        voucher.direction = direction;
        voucher.exchangeRate = exchangeRate;
        voucher.currencyCode = normalizedCurrencyCode;
        voucher.rateLockTime = timestamp + (30 * 60000L); // 30-minute rate lock window

        // SELL vouchers start in SETTLEMENT_PENDING state and must clear the stage-aware verifier window.
        if (direction == DIRECTION_SELL) {
            voucher.state = STATE_SETTLEMENT_PENDING;
        } else {
            voucher.state = STATE_ISSUED;
        }

        // Calculate SHA256 of secret
        voucher.secretHash = sha256(voucher.secret);
        voucher.payloadVersion = PAYLOAD_VERSION_V2;
        voucher.canonicalPayload = voucher.buildCanonicalVoucherBody();
        voucher.secondSignatureManifest = activeIssuerState == null
                ? createDefaultSecondSignatureManifest(issuerIdentity, voucher.canonicalPayload)
                : activeIssuerState.createSecondSignatureManifest(voucher.canonicalPayload);
        voucher.issuerKeystoreAlias = voucher.secondSignatureManifest == null ? issuerIdentity.getKeystoreAlias()
                : voucher.secondSignatureManifest.getIssuerKeystoreAlias();
        voucher.issuerRotationEpoch = voucher.secondSignatureManifest == null ? 0L
                : voucher.secondSignatureManifest.getRotationEpoch();
        voucher.issuerActivatedAt = voucher.secondSignatureManifest == null ? timestamp
                : voucher.secondSignatureManifest.getCreatedAt();
        voucher.issuerPreviousKeystoreAlias = voucher.secondSignatureManifest == null ? null
                : voucher.secondSignatureManifest.getPreviousIssuerKeystoreAlias();
        voucher.issuerRotationReason = voucher.secondSignatureManifest == null ? "active"
                : voucher.secondSignatureManifest.getRotationReason();
        ArrayList<VoucherSignatureBundle.SignatureEntry> signatureEntries = new ArrayList<VoucherSignatureBundle.SignatureEntry>();
        signatureEntries.add(issuerIdentity.createPrimarySignatureEntry(voucher.canonicalPayload));
        signatureEntries.add(issuerIdentity.createSecondaryDetachedEntry(voucher.secondSignatureManifest));
        voucher.signatureBundle = VoucherSignatureBundle.forPayload(voucher.canonicalPayload, signatureEntries);
        voucher.syncLegacySignatureFieldsFromBundle();
        voucher.legacyInteroperabilitySignature = VoucherSignatureAlgorithms.sign(
                issuerIdentity.getAlgorithm(),
                voucher.buildSignedPayload(),
                issuerIdentity.getPrivateKey());

        Log.d(TAG, "Generated " + (direction == DIRECTION_BUY ? "BUY" : "SELL") +
              " voucher " + voucher.voucherId + " for " + denomination + " sats");

        return voucher;
    }

    /**
     * Create voucher QR code payload
     * Format: satnet_voucher|<voucherId>|<secret>|<denomination>|<agentId>|<issuedTime>|<expiryTime>|<state>|<direction>|<exchangeRate>|<currencyCode>
     */
    public String getQRPayload() {
        if (payloadVersion >= PAYLOAD_VERSION_V2 && canonicalPayload != null && signatureBundle != null) {
            try {
                return String.format(Locale.US,
                        "%s|%s|%s|%s",
                        QR_PREFIX,
                        QR_VERSION_V2,
                        encodePayloadSegment(canonicalPayload),
                        encodePayloadSegment(signatureBundle.toJson()));
            } catch (Exception e) {
                Log.w(TAG, "Falling back to legacy voucher QR payload", e);
            }
        }
        return getLegacyQrPayloadForInterop();
    }


    String getLegacyQrPayloadForInterop() {
        return String.format(Locale.US,
                "%s|%s|%s|%d|%s|%d|%d|%d|%d|%s|%s|%d|%s|%s|%s",
                QR_PREFIX,
                voucherId,
                secret,
                denomination,
                agentId,
                issuedTime,
                expiryTime,
                state,
                direction,
                String.valueOf(exchangeRate),
                currencyCode == null ? "USD" : currencyCode,
                rateLockTime,
                issuerKeyId == null ? "" : issuerKeyId,
                issuerPublicKey == null ? "" : issuerPublicKey,
                legacyInteroperabilitySignature != null ? legacyInteroperabilitySignature : (signature == null ? "" : signature));
    }

    /**
     * Parse QR code payload to recreate voucher (supports bidirectional format)
     * Backwards compatible with old format (5 parts = legacy BUY voucher)
     */
    public static BitcoinVoucher parseQRPayload(String payload) throws Exception {
        if (payload == null || payload.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid voucher QR format");
        }
        String[] v2Parts = payload.split("\\|", 4);
        if (v2Parts.length == 4 && QR_PREFIX.equals(v2Parts[0]) && QR_VERSION_V2.equalsIgnoreCase(v2Parts[1])) {
            return parseV2Payload(v2Parts[2], v2Parts[3]);
        }

        String[] parts = payload.split("\\|");

        if (parts.length < 5 || !parts[0].equals(QR_PREFIX)) {
            throw new IllegalArgumentException("Invalid voucher QR format");
        }

        BitcoinVoucher voucher = new BitcoinVoucher();
        voucher.payloadVersion = PAYLOAD_VERSION_LEGACY;
        voucher.voucherId = parts[1];
        voucher.secret = parts[2];
        voucher.denomination = Long.parseLong(parts[3]);
        voucher.agentId = parts[4];
        voucher.secretHash = sha256(voucher.secret);

        // Parse metadata fields if present.
        if (parts.length >= 8) {
            voucher.issuedTime = Long.parseLong(parts[5]);
            voucher.expiryTime = Long.parseLong(parts[6]);
            voucher.state = Integer.parseInt(parts[7]);

            if (parts.length >= 11) {
                voucher.direction = Integer.parseInt(parts[8]);
                voucher.exchangeRate = Double.parseDouble(parts[9]);
                voucher.currencyCode = parts[10];
            } else {
                voucher.direction = DIRECTION_BUY;
                voucher.exchangeRate = 0.0;
                voucher.currencyCode = "USD";
            }
            voucher.rateLockTime = parts.length >= 12 ? Long.parseLong(parts[11]) : voucher.issuedTime + (30 * 60000L);
            voucher.issuerKeyId = parts.length >= 13 ? parts[12] : null;
            voucher.issuerPublicKey = parts.length >= 14 ? parts[13] : null;
            voucher.signature = parts.length >= 15 ? parts[14] : null;
            voucher.legacyInteroperabilitySignature = voucher.signature;
            voucher.primarySignatureAlgorithm = VoucherSignatureAlgorithms.ALG_RSA_SHA256;
        } else {
            // Legacy format - infer timing from scan time for backward compatibility.
            long now = System.currentTimeMillis();
            voucher.issuedTime = now;
            voucher.expiryTime = now + (24 * 3600000L);
            voucher.direction = DIRECTION_BUY;
            voucher.state = STATE_ISSUED;
            voucher.exchangeRate = 0.0;
            voucher.currencyCode = "USD";
            voucher.rateLockTime = now + (30 * 60000L);
        }
        voucher.state = normalizeParsedState(voucher.state, voucher.direction);

        Log.d(TAG, "Parsed " + (voucher.direction == DIRECTION_BUY ? "BUY" : "SELL") +
              " voucher from QR: " + voucher.voucherId);

        return voucher;
    }

    private static BitcoinVoucher parseV2Payload(String encodedBody, String encodedSignatureBundle) throws Exception {
        BitcoinVoucher voucher = new BitcoinVoucher();
        voucher.payloadVersion = PAYLOAD_VERSION_V2;
        voucher.canonicalPayload = decodePayloadSegment(encodedBody);
        voucher.signatureBundle = VoucherSignatureBundle.fromJson(decodePayloadSegment(encodedSignatureBundle));
        voucher.parseCanonicalVoucherBody(voucher.canonicalPayload);
        voucher.secretHash = sha256(voucher.secret);
        voucher.syncLegacySignatureFieldsFromBundle();
        voucher.state = normalizeParsedState(voucher.state, voucher.direction);
        return voucher;
    }

    /**
     * Validate voucher before redemption
     * Checks: not expired, not already redeemed, valid hash, rate lock (30-min for SELL)
     */
    public ValidationResult validate() {
        long now = System.currentTimeMillis();

        // Check expiry
        if (now > expiryTime) {
            state = STATE_EXPIRED;
            return new ValidationResult(false, "Voucher expired");
        }

        // Check state
        if (state == STATE_REDEEMED) {
            return new ValidationResult(false, "Voucher already redeemed");
        }

        if (state == STATE_INVALID) {
            return new ValidationResult(false, "Voucher invalid");
        }

        // Verify secret hash
        try {
            String calculated = sha256(secret);
            if (!calculated.equals(secretHash)) {
                state = STATE_INVALID;
                return new ValidationResult(false, "Voucher hash mismatch - possible tampering");
            }
            if (payloadVersion >= PAYLOAD_VERSION_V2) {
                if (signatureBundle == null || canonicalPayload == null || canonicalPayload.trim().isEmpty()) {
                    state = STATE_INVALID;
                    return new ValidationResult(false, "Voucher signature bundle missing");
                }
                VoucherSignatureBundle.VerificationResult verificationResult = signatureBundle.verify(canonicalPayload);
                if (!verificationResult.isValid) {
                    state = STATE_INVALID;
                    return new ValidationResult(false, verificationResult.message);
                }
                if (verificationResult.verifiedSignature != null) {
                    issuerPublicKey = verificationResult.verifiedSignature.publicKey;
                    issuerKeyId = verificationResult.resolvedKeyId != null
                            ? verificationResult.resolvedKeyId
                            : verificationResult.verifiedSignature.keyId;
                    signature = verificationResult.verifiedSignature.signature;
                    primarySignatureAlgorithm = verificationResult.verifiedSignature.algorithm;
                }
            } else {
                if (issuerPublicKey == null || issuerPublicKey.trim().isEmpty()
                        || issuerKeyId == null || issuerKeyId.trim().isEmpty()
                        || signature == null || signature.trim().isEmpty()) {
                    state = STATE_INVALID;
                    return new ValidationResult(false, "Voucher signature missing - unsigned legacy vouchers are not accepted");
                }
                PublicKey publicKey = decodePublicKey(issuerPublicKey);
                String calculatedKeyId = VoucherIssuerIdentity.fingerprint(publicKey);
                if (!calculatedKeyId.equalsIgnoreCase(issuerKeyId)) {
                    state = STATE_INVALID;
                    return new ValidationResult(false, "Voucher issuer key ID mismatch");
                }
                if (!verifySignature(buildSignedPayload(), signature, publicKey)) {
                    state = STATE_INVALID;
                    return new ValidationResult(false, "Voucher signature verification failed");
                }
            }
        } catch (Exception e) {
            state = STATE_INVALID;
            return new ValidationResult(false, "Voucher validation error: " + e.getMessage());
        }

        if (denomination > SatnetRuntimeConfig.getMaxVoucherDenominationSats()) {
            state = STATE_INVALID;
            return new ValidationResult(false, "Voucher denomination exceeds current SATNET stage cap");
        }

        // For SELL vouchers: check settlement verification window (stage-aware)
        if (direction == DIRECTION_SELL) {
            if (state == STATE_SETTLEMENT_PENDING) {
                long settlementDeadline = issuedTime + SatnetRuntimeConfig.getSettlementWindowMillis();
                if (now > settlementDeadline && !settlementVerified) {
                    // Auto-release after the configured verifier window if not verified (prevents funds lock)
                    state = STATE_SETTLEMENT_VERIFIED;
                    settlementVerified = true;
                    settlementVerifiedTime = now;
                    return new ValidationResult(true,
                            "Voucher released after " + SatnetRuntimeConfig.getSettlementWindowHours() + "h verifier window");
                }

                // Within settlement window but not verified
                if (!settlementVerified) {
                    return new ValidationResult(false,
                            "SELL voucher pending verification (" + SatnetRuntimeConfig.getSettlementWindowHours() + "h window)");
                }
            }

        }

        // Check rate lock (30-minute window prevents rate gaming)
        if (direction == DIRECTION_SELL && now > rateLockTime) {
            return new ValidationResult(false, "SELL voucher rate-lock expired (30-min window)");
        }

        return new ValidationResult(true, "Voucher valid");
    }

    /**
     * Mark voucher as redeemed
     */
    public void redeem(String walletAddress) {
        if (walletAddress == null || walletAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("Wallet address is required");
        }
        ValidationResult validationResult = validate();
        if (!validationResult.isValid) {
            throw new IllegalStateException(validationResult.message);
        }
        if (state != STATE_ISSUED && state != STATE_SETTLEMENT_VERIFIED) {
            throw new IllegalStateException("Cannot redeem voucher in state " + state);
        }

        this.state = STATE_REDEEMED;
        this.redeemedTime = System.currentTimeMillis();
        this.redeemedByWallet = walletAddress;

        Log.d(TAG, "Voucher " + voucherId + " redeemed");
    }

    /**
     * Getters
     */
    public String getVoucherId() { return voucherId; }
    public long getDenomination() { return denomination; }
    public String getAgentId() { return agentId; }
    public long getIssuedTime() { return issuedTime; }
    public long getExpiryTime() { return expiryTime; }
    public String getSecret() { return secret; }
    public String getSecretHash() { return secretHash; }
    public int getState() { return state; }
    public long getRedeemedTime() { return redeemedTime; }
    public String getRedeemedByWallet() { return redeemedByWallet; }

    // Bidirectional voucher getters
    public int getDirection() { return direction; }
    public double getExchangeRate() { return exchangeRate; }
    public long getRateLockTime() { return rateLockTime; }
    public String getCurrencyCode() { return currencyCode; }
    public long getSettlementVerifiedTime() { return settlementVerifiedTime; }
    public boolean isSettlementVerified() { return settlementVerified; }
    public String getIssuerPublicKey() { return issuerPublicKey; }
    public String getIssuerKeyId() { return issuerKeyId; }
    public String getSignature() { return signature; }
    public String getPrimarySignatureAlgorithm() { return primarySignatureAlgorithm; }
    public int getPayloadVersion() { return payloadVersion; }
    public String getCanonicalPayload() { return canonicalPayload; }
    public VoucherSignatureBundle getSignatureBundle() { return signatureBundle; }
    public VoucherSecondSignatureManifest getSecondSignatureManifest() { return resolveSecondSignatureManifest(); }
    public String getIssuerKeystoreAlias() { return issuerKeystoreAlias; }
    public long getIssuerRotationEpoch() { return issuerRotationEpoch; }
    public long getIssuerActivatedAt() { return issuerActivatedAt; }
    public String getIssuerPreviousKeystoreAlias() { return issuerPreviousKeystoreAlias; }
    public String getIssuerRotationReason() { return issuerRotationReason; }
    public String getSecondSignatureManifestJson() {
        VoucherSecondSignatureManifest manifest = resolveSecondSignatureManifest();
        return manifest == null ? null : manifest.toJson();
    }
    public String getSecondSignaturePublicKeyReference() {
        VoucherSecondSignatureManifest manifest = resolveSecondSignatureManifest();
        return manifest == null ? null : manifest.getDetachedPublicKeyReference();
    }
    public String getSecondSignatureMetadataReference() {
        VoucherSecondSignatureManifest manifest = resolveSecondSignatureManifest();
        return manifest == null ? null : manifest.getMetadataReference();
    }
    public String getSecondSignatureReference() {
        VoucherSecondSignatureManifest manifest = resolveSecondSignatureManifest();
        return manifest == null ? null : manifest.getDetachedSignatureReference();
    }
    public String getSignatureBundleJson() {
        try {
            return signatureBundle == null ? null : signatureBundle.toJson();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Mark SELL voucher as settlement-verified by community Verifier
     */
    public void markSettlementVerified() {
        if (direction == DIRECTION_SELL && state == STATE_SETTLEMENT_PENDING) {
            this.state = STATE_SETTLEMENT_VERIFIED;
            this.settlementVerified = true;
            this.settlementVerifiedTime = System.currentTimeMillis();
            Log.d(TAG, "SELL voucher " + voucherId + " marked as settlement-verified");
        }
    }

    /**
     * Utility: Calculate SHA256 hash
     */
    private static String sha256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private String buildSignedPayload() {
        return String.format(Locale.US,
                "%s|%s|%d|%s|%d|%d|%d|%s|%s|%d|%s",
                voucherId,
                secret,
                denomination,
                agentId,
                issuedTime,
                expiryTime,
                direction,
                String.valueOf(exchangeRate),
                currencyCode == null ? "USD" : currencyCode,
                rateLockTime,
                issuerKeyId == null ? "" : issuerKeyId);
    }

    private String buildCanonicalVoucherBody() {
        return String.format(Locale.US,
                "%s|%s|%s|%d|%s|%d|%d|%d|%d|%s|%s|%d",
                BODY_PREFIX_V2,
                encodeTextField(voucherId),
                secret,
                denomination,
                encodeTextField(agentId),
                issuedTime,
                expiryTime,
                state,
                direction,
                Double.toString(exchangeRate),
                encodeTextField(currencyCode == null ? "USD" : currencyCode),
                rateLockTime);
    }

    private void parseCanonicalVoucherBody(String payload) throws Exception {
        String[] parts = payload.split("\\|");
        if (parts.length < 12 || !BODY_PREFIX_V2.equals(parts[0])) {
            throw new IllegalArgumentException("Unsupported voucher canonical payload");
        }
        voucherId = decodeTextField(parts[1]);
        secret = parts[2];
        denomination = Long.parseLong(parts[3]);
        agentId = decodeTextField(parts[4]);
        issuedTime = Long.parseLong(parts[5]);
        expiryTime = Long.parseLong(parts[6]);
        state = Integer.parseInt(parts[7]);
        direction = Integer.parseInt(parts[8]);
        exchangeRate = Double.parseDouble(parts[9]);
        currencyCode = decodeTextField(parts[10]);
        rateLockTime = Long.parseLong(parts[11]);
    }

    private void syncLegacySignatureFieldsFromBundle() {
        if (signatureBundle == null) {
            return;
        }
        VoucherSignatureBundle.SignatureEntry primarySignature = signatureBundle.getPrimarySignature();
        if (primarySignature == null) {
            return;
        }
        primarySignatureAlgorithm = primarySignature.algorithm;
        issuerKeyId = primarySignature.keyId;
        issuerPublicKey = primarySignature.publicKey;
        signature = primarySignature.signature;
        secondSignatureManifest = signatureBundle.getSecondSignatureManifest();
        if (secondSignatureManifest != null) {
            issuerKeystoreAlias = secondSignatureManifest.getIssuerKeystoreAlias();
            issuerRotationEpoch = secondSignatureManifest.getRotationEpoch();
            issuerActivatedAt = secondSignatureManifest.getCreatedAt();
            issuerPreviousKeystoreAlias = secondSignatureManifest.getPreviousIssuerKeystoreAlias();
            issuerRotationReason = secondSignatureManifest.getRotationReason();
        }
    }

    private VoucherSecondSignatureManifest resolveSecondSignatureManifest() {
        if (secondSignatureManifest == null && signatureBundle != null) {
            secondSignatureManifest = signatureBundle.getSecondSignatureManifest();
            if (secondSignatureManifest != null) {
                issuerKeystoreAlias = secondSignatureManifest.getIssuerKeystoreAlias();
                issuerRotationEpoch = secondSignatureManifest.getRotationEpoch();
                issuerActivatedAt = secondSignatureManifest.getCreatedAt();
                issuerPreviousKeystoreAlias = secondSignatureManifest.getPreviousIssuerKeystoreAlias();
                issuerRotationReason = secondSignatureManifest.getRotationReason();
            }
        }
        if (secondSignatureManifest == null) {
            secondSignatureManifest = createFallbackSecondSignatureManifest();
        }
        return secondSignatureManifest;
    }

    private VoucherSecondSignatureManifest createFallbackSecondSignatureManifest() {
        if (canonicalPayload == null || canonicalPayload.trim().isEmpty()) {
            return null;
        }
        String resolvedPrimaryKeyId = issuerKeyId == null ? "" : issuerKeyId;
        String alias = issuerKeystoreAlias == null ? "" : issuerKeystoreAlias;
        String referenceToken = sanitizeReferenceToken(alias.trim().isEmpty() ? resolvedPrimaryKeyId : alias);
        try {
            return VoucherSecondSignatureManifest.createDetachedManifest(
                    VoucherSignatureAlgorithms.ALG_ML_DSA_87,
                    canonicalPayload,
                    resolvedPrimaryKeyId,
                    "",
                    "issuer://" + referenceToken + "#pq-public",
                    "issuer://" + referenceToken + "#pq-signature",
                    "issuer://" + referenceToken + "#pq-metadata",
                    VoucherSecondSignatureManifest.shortDigest(referenceToken + "|" + resolvedPrimaryKeyId),
                    alias,
                    issuerPreviousKeystoreAlias == null ? "" : issuerPreviousKeystoreAlias,
                    issuerRotationReason == null || issuerRotationReason.trim().isEmpty() ? "active" : issuerRotationReason,
                    issuerRotationEpoch,
                    issuerActivatedAt > 0L ? issuerActivatedAt : issuedTime);
        } catch (Exception e) {
            return null;
        }
    }

    private static VoucherSecondSignatureManifest createDefaultSecondSignatureManifest(
            VoucherIssuerIdentity issuerIdentity, String canonicalPayload) throws Exception {
        String alias = issuerIdentity.getKeystoreAlias();
        String referenceToken = sanitizeReferenceToken(alias == null || alias.trim().isEmpty()
                ? issuerIdentity.getIssuerKeyId()
                : alias);
        return VoucherSecondSignatureManifest.createDetachedManifest(
                VoucherSignatureAlgorithms.ALG_ML_DSA_87,
                canonicalPayload,
                issuerIdentity.getIssuerKeyId(),
                "",
                "issuer://" + referenceToken + "#pq-public",
                "issuer://" + referenceToken + "#pq-signature",
                "issuer://" + referenceToken + "#pq-metadata",
                VoucherSecondSignatureManifest.shortDigest(referenceToken + "|" + issuerIdentity.getIssuerKeyId()),
                alias,
                "",
                "active",
                0L,
                System.currentTimeMillis());
    }

    private static String sanitizeReferenceToken(String value) {
        return (value == null ? "issuer" : value.trim()).replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String signPayload(String payload, PrivateKey privateKey) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64Compat.encode(signer.sign());
    }

    private static boolean verifySignature(String payload, String encodedSignature, PublicKey publicKey) throws Exception {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(payload.getBytes(StandardCharsets.UTF_8));
        return verifier.verify(Base64Compat.decode(encodedSignature));
    }

    private static PublicKey decodePublicKey(String encodedPublicKey) throws Exception {
        return VoucherSignatureAlgorithms.decodePublicKey(VoucherSignatureAlgorithms.ALG_RSA_SHA256, encodedPublicKey);
    }

    private static int normalizeParsedState(int parsedState, int direction) {
        switch (parsedState) {
            case STATE_ISSUED:
            case STATE_REDEEMED:
            case STATE_EXPIRED:
            case STATE_INVALID:
            case STATE_SETTLEMENT_PENDING:
            case STATE_SETTLEMENT_VERIFIED:
                return parsedState;
            default:
                return direction == DIRECTION_SELL ? STATE_SETTLEMENT_PENDING : STATE_ISSUED;
        }
    }

    /**
     * Utility: Convert bytes to hex string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String encodeTextField(String value) {
        return Base64Compat.encode((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeTextField(String value) {
        return new String(Base64Compat.decode(value), StandardCharsets.UTF_8);
    }

    private static String encodePayloadSegment(String value) {
        return Base64Compat.encode(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodePayloadSegment(String encodedValue) {
        return new String(Base64Compat.decode(encodedValue), StandardCharsets.UTF_8);
    }

    /**
     * Result of voucher validation
     */
    public static class ValidationResult {
        public boolean isValid;
        public String message;

        public ValidationResult(boolean isValid, String message) {
            this.isValid = isValid;
            this.message = message;
        }
    }

    /**
     * Format: "XXXX-XXXX-XXXX-XXXX" for human-readable entry
     */
    public String getNumericCode() {
        // Convert secret to numeric format
        String hex = secret.substring(0, 16);
        return String.format(Locale.US, "%s-%s-%s-%s",
                hex.substring(0, 4).toUpperCase(Locale.US),
                hex.substring(4, 8).toUpperCase(Locale.US),
                hex.substring(8, 12).toUpperCase(Locale.US),
                hex.substring(12, 16).toUpperCase(Locale.US));
    }

    @Override
    public @NonNull String toString() {
        return String.format(Locale.US, "BitcoinVoucher{id=%s, denom=%d sats, state=%d, expires=%s}",
                voucherId, denomination, state, new Date(expiryTime));
    }
}
