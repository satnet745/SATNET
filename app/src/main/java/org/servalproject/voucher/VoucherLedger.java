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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.Cursor;
import android.util.Log;

import org.servalproject.satnet.SatnetRoleConflictPolicy;
import org.servalproject.satnet.SatnetRoleManager;
import org.servalproject.satnet.SatnetRuntimeConfig;

import java.util.UUID;

/**
 * Local voucher ledger - tracks issued and redeemed vouchers
 * Persists to SQLite for offline operation
 * Syncs to Rhizome for distributed audit trail
 */
public class VoucherLedger extends SQLiteOpenHelper {
    private static final String TAG = "VoucherLedger";
    private static final String DB_NAME = "satnet_vouchers.db";
    private static final int DB_VERSION = 10;

    private static final String TABLE_VOUCHERS = "vouchers";
    private static final String TABLE_REDEMPTIONS = "redemptions";
    private static final String TABLE_VERIFIER_AUDIT_HISTORY = "verifier_audit_history";
    private static final String TABLE_VOUCHER_DISPUTES = "voucher_disputes";
    private static final String TABLE_VOUCHER_RISK_EVENTS = "voucher_risk_events";
    public static final String AUDIT_ORIGIN_LOCAL = "local";
    public static final String AUDIT_ORIGIN_MESH = "mesh";
    public static final String RISK_EVENT_ORIGIN_LOCAL = "local";
    public static final String RISK_EVENT_ORIGIN_MESH = "mesh";

    public static final int RISK_STATE_NONE = 0;
    public static final int RISK_STATE_MONITOR = 1;
    public static final int RISK_STATE_HOLD = 2;
    public static final int RISK_STATE_BLOCK = 3;

    public static final int DISPUTE_STATUS_NONE = 0;
    public static final int DISPUTE_STATUS_OPEN = 1;
    public static final int DISPUTE_STATUS_UNDER_REVIEW = 2;
    public static final int DISPUTE_STATUS_RESOLVED = 3;
    public static final int DISPUTE_STATUS_ESCALATED = 4;

    public VoucherLedger(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "Creating voucher ledger database");

        // Table for issued vouchers (by agents)
        createVoucherTable(db);

        // Table for redemption confirmations
        createRedemptionsTable(db);
        createVerifierAuditHistoryTable(db);
        createVoucherDisputesTable(db);
        createVoucherRiskEventsTable(db);
        ensureVoucherSchema(db);

        Log.d(TAG, "Voucher ledger database created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        createVoucherTable(db);
        createRedemptionsTable(db);
        createVerifierAuditHistoryTable(db);
        createVoucherDisputesTable(db);
        createVoucherRiskEventsTable(db);
        ensureVoucherSchema(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        ensureVoucherSchema(db);
    }

    /**
     * Record issued voucher in ledger
     */
    public void recordIssuedVoucher(BitcoinVoucher voucher) {
        recordIssuedVoucher(voucher, null, null);
    }

    public void recordIssuedVoucher(BitcoinVoucher voucher, String agentSubjectId) {
        recordIssuedVoucher(voucher, agentSubjectId, null);
    }

    public void recordIssuedVoucher(BitcoinVoucher voucher, String agentSubjectId, String agentParticipantRootId) {
        SQLiteDatabase db = this.getWritableDatabase();

        String sql = "INSERT OR REPLACE INTO " + TABLE_VOUCHERS +
                " (voucher_id, agent_id, agent_subject_id, denomination, secret_hash, issued_time, expiry_time, state," +
                " redeemed_time, redeemed_by_wallet, synced_to_mesh, direction, exchange_rate, currency_code," +
                " rate_lock_time, settlement_verified, settlement_verified_time, payload_version, canonical_payload," +
                " signature_bundle_json, primary_signature_algorithm, primary_issuer_key_id, secondary_signature_manifest_json," +
                " secondary_public_key_reference, secondary_metadata_reference, secondary_signature_reference," +
                " issuer_keystore_alias, issuer_rotation_epoch, issuer_activated_at, issuer_previous_keystore_alias," +
                " issuer_rotation_reason, redeemer_subject_id, settlement_verifier_subject_id, dispute_resolver_subject_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            db.execSQL(sql, new Object[]{
                    voucher.getVoucherId(),
                    voucher.getAgentId(),
                    normalizeSubjectId(agentSubjectId),
                    voucher.getDenomination(),
                    voucher.getSecretHash(),
                    voucher.getIssuedTime(),
                    voucher.getExpiryTime(),
                    voucher.getState(),
                    voucher.getRedeemedTime() > 0 ? voucher.getRedeemedTime() : null,
                    voucher.getRedeemedByWallet(),
                    0,
                    voucher.getDirection(),
                    voucher.getExchangeRate(),
                    voucher.getCurrencyCode(),
                    voucher.getRateLockTime(),
                    voucher.isSettlementVerified() ? 1 : 0,
                    voucher.getSettlementVerifiedTime() > 0 ? voucher.getSettlementVerifiedTime() : null,
                    voucher.getPayloadVersion(),
                    voucher.getCanonicalPayload(),
                    voucher.getSignatureBundleJson(),
                    voucher.getPrimarySignatureAlgorithm(),
                    voucher.getIssuerKeyId(),
                    voucher.getSecondSignatureManifestJson(),
                    voucher.getSecondSignaturePublicKeyReference(),
                    voucher.getSecondSignatureMetadataReference(),
                    voucher.getSecondSignatureReference(),
                    voucher.getIssuerKeystoreAlias(),
                    voucher.getIssuerRotationEpoch(),
                    voucher.getIssuerActivatedAt(),
                    voucher.getIssuerPreviousKeystoreAlias(),
                    voucher.getIssuerRotationReason(),
                    null,
                    null,
                    null
            });
            db.execSQL(
                    "UPDATE " + TABLE_VOUCHERS +
                            " SET agent_participant_root_id = ?, risk_state = COALESCE(risk_state, 0), risk_score = COALESCE(risk_score, 0)," +
                            " dispute_status = COALESCE(dispute_status, 0), required_verifier_quorum = CASE WHEN required_verifier_quorum <= 0 THEN 1 ELSE required_verifier_quorum END," +
                            " achieved_verifier_quorum = COALESCE(achieved_verifier_quorum, 0) WHERE voucher_id = ?",
                    new Object[]{normalizeSubjectId(agentParticipantRootId), voucher.getVoucherId()});
            Log.d(TAG, "Recorded issued voucher: " + voucher.getVoucherId());
        } catch (Exception e) {
            Log.e(TAG, "Error recording voucher", e);
        }
    }

    /**
     * Look up voucher by ID
     */
    public BitcoinVoucher getVoucher(String voucherId) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_VOUCHERS,
                null,
                "voucher_id = ?",
                new String[]{voucherId},
                null, null, null);

        BitcoinVoucher voucher = null;
        if (cursor.moveToFirst()) {
            // Reconstruct voucher from database record
            // (Note: we lose the original secret, only have hash for verification)
            Log.d(TAG, "Found voucher: " + voucherId);
        }
        cursor.close();

        return voucher;
    }

    /**
     * Mark voucher as redeemed
     */
    public void recordRedemption(BitcoinVoucher voucher, String walletAddress, String txHash) {
        recordRedemption(voucher, walletAddress, txHash, null, null);
    }

    public void recordRedemption(BitcoinVoucher voucher, String walletAddress, String txHash, String redeemerSubjectId) {
        recordRedemption(voucher, walletAddress, txHash, redeemerSubjectId, null);
    }

    public void recordRedemption(BitcoinVoucher voucher,
            String walletAddress,
            String txHash,
            String redeemerSubjectId,
            String redeemerParticipantRootId) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Update voucher state
        String updateVoucher = "UPDATE " + TABLE_VOUCHERS +
                " SET state = ?, redeemed_time = ?, redeemed_by_wallet = ?, redeemer_subject_id = ?, redeemer_participant_root_id = ? " +
                "WHERE voucher_id = ?";

        db.execSQL(updateVoucher, new Object[]{
                BitcoinVoucher.STATE_REDEEMED,
                System.currentTimeMillis(),
                walletAddress,
                normalizeSubjectId(redeemerSubjectId),
                normalizeSubjectId(redeemerParticipantRootId),
                voucher.getVoucherId()
        });

        // Record redemption transaction
        String insertRedemption = "INSERT INTO " + TABLE_REDEMPTIONS +
                " (redemption_id, voucher_id, user_wallet, redeemer_subject_id, amount_sats, timestamp, tx_hash) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        String redemptionId = voucher.getVoucherId() + "_" + System.currentTimeMillis();

        db.execSQL(insertRedemption, new Object[]{
                redemptionId,
                voucher.getVoucherId(),
                walletAddress,
                normalizeSubjectId(redeemerSubjectId),
                voucher.getDenomination(),
                System.currentTimeMillis(),
                txHash
        });

        Log.d(TAG, "Recorded redemption for " + voucher.getVoucherId());
    }

    /**
     * Check if voucher already redeemed
     */
    public boolean isVoucherRedeemed(String voucherId) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_VOUCHERS,
                new String[]{"state"},
                "voucher_id = ?",
                new String[]{voucherId},
                null, null, null);

        boolean redeemed = false;
        if (cursor.moveToFirst()) {
            int state = cursor.getInt(0);
            redeemed = (state == BitcoinVoucher.STATE_REDEEMED);
        }
        cursor.close();

        return redeemed;
    }

    /**
     * Get all unredeemed vouchers for agent (for audit)
     */
    public Cursor getAgentUnredeemed(String agentId) {
        SQLiteDatabase db = this.getReadableDatabase();

        return db.query(TABLE_VOUCHERS,
                null,
                "agent_id = ? AND state = ?",
                new String[]{agentId, String.valueOf(BitcoinVoucher.STATE_ISSUED)},
                null, null,
                "issued_time DESC");
    }

    /**
     * Mark voucher as synced to Rhizome mesh
     */
    public void markSyncedToMesh(String voucherId) {
        SQLiteDatabase db = this.getWritableDatabase();

        db.execSQL("UPDATE " + TABLE_VOUCHERS +
                " SET synced_to_mesh = 1 WHERE voucher_id = ?",
                new Object[]{voucherId});

        Log.d(TAG, "Marked voucher synced to mesh: " + voucherId);
    }

    /**
     * Get pending vouchers to sync to Rhizome
     */
    public Cursor getPendingSync() {
        SQLiteDatabase db = this.getReadableDatabase();

        return db.query(TABLE_VOUCHERS,
                null,
                "synced_to_mesh = 0",
                null, null, null, null);
    }

    /**
     * Record bidirectional voucher with extended fields
     */
    public void recordBidirectionalVoucher(BitcoinVoucher voucher) {
        recordBidirectionalVoucher(voucher, null);
    }

    public void recordBidirectionalVoucher(BitcoinVoucher voucher, String agentSubjectId) {
        recordIssuedVoucher(voucher, agentSubjectId);
    }

    public void recordBidirectionalVoucher(BitcoinVoucher voucher, String agentSubjectId, String agentParticipantRootId) {
        recordIssuedVoucher(voucher, agentSubjectId, agentParticipantRootId);
    }

    /**
     * Mark SELL voucher as settlement-verified by Verifier
     */
    public void markSettlementVerified(String voucherId) {
        markSettlementVerified(voucherId, null, null, 1, 1);
    }

    public void markSettlementVerified(String voucherId, String verifierSubjectId) {
        markSettlementVerified(voucherId, verifierSubjectId, null, 1, 1);
    }

    public void markSettlementVerified(String voucherId,
            String verifierSubjectId,
            String verifierParticipantRootId,
            int achievedQuorum,
            int requiredQuorum) {
        String normalizedVerifierSubjectId = normalizeSubjectId(verifierSubjectId);
        String normalizedVerifierRootId = normalizeSubjectId(verifierParticipantRootId);
        SatnetRoleConflictPolicy.ConflictCheck conflictCheck = SatnetRoleConflictPolicy.authorizeAction(
                normalizedVerifierSubjectId,
                normalizedVerifierRootId,
                SatnetRoleConflictPolicy.ACTION_VERIFY_SETTLEMENT,
                SatnetRoleManager.ROLE_VERIFIER,
                getVoucherParticipantSnapshot(voucherId));
        if (!conflictCheck.allowed) {
            if (conflictCheck.shouldRecordRiskEvent) {
                recordRiskEvent(voucherId,
                        normalizedVerifierSubjectId,
                        normalizedVerifierRootId,
                        SatnetRoleManager.ROLE_VERIFIER,
                        "verifier_conflict",
                        25,
                        conflictCheck.reasonCode,
                        conflictCheck.message);
            }
            throw new IllegalStateException(conflictCheck.message);
        }
        SQLiteDatabase db = this.getWritableDatabase();
        int resolvedRequiredQuorum = Math.max(1, requiredQuorum);
        int resolvedAchievedQuorum = Math.max(0, achievedQuorum);
        boolean fullyVerified = resolvedAchievedQuorum >= resolvedRequiredQuorum;

        db.execSQL(
                "UPDATE " + TABLE_VOUCHERS +
                " SET settlement_verified = ?, settlement_verified_time = ?, state = ?, settlement_verifier_subject_id = ?," +
                " settlement_verifier_participant_root_id = ?, achieved_verifier_quorum = ?, required_verifier_quorum = ?," +
                " last_policy_decision_code = ?, last_policy_decision_message = ?, last_policy_decision_at = ? " +
                "WHERE voucher_id = ?",
                new Object[]{
                        fullyVerified ? 1 : 0,
                        fullyVerified ? System.currentTimeMillis() : null,
                        fullyVerified ? BitcoinVoucher.STATE_SETTLEMENT_VERIFIED : BitcoinVoucher.STATE_SETTLEMENT_PENDING,
                        normalizedVerifierSubjectId,
                        normalizedVerifierRootId,
                        resolvedAchievedQuorum,
                        resolvedRequiredQuorum,
                        fullyVerified ? "SETTLEMENT_VERIFIED" : "QUORUM_PENDING",
                        fullyVerified
                                ? "Verifier quorum satisfied; settlement released."
                                : "Verifier quorum pending (" + resolvedAchievedQuorum + "/" + resolvedRequiredQuorum + ")",
                        System.currentTimeMillis(),
                        voucherId});

        Log.d(TAG, "Marked SELL voucher settlement verified: " + voucherId);
    }

    public void openDispute(String voucherId,
            String openedBySubjectId,
            String openedByRootId,
            int openedByRole,
            String reasonCode,
            String evidenceBundleRef) {
        if (voucherId == null || voucherId.trim().isEmpty()) {
            return;
        }
        String disputeId = voucherId.trim() + ":dispute:" + UUID.randomUUID().toString();
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL(
                "INSERT OR REPLACE INTO " + TABLE_VOUCHER_DISPUTES +
                        " (dispute_id, voucher_id, opened_by_subject_id, opened_by_role, opened_by_root_id, opened_at, reason_code, status, evidence_bundle_ref, sync_state)" +
                        " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                new Object[]{
                        disputeId,
                        voucherId,
                        normalizeSubjectId(openedBySubjectId),
                        openedByRole,
                        normalizeSubjectId(openedByRootId),
                        System.currentTimeMillis(),
                        reasonCode,
                        DISPUTE_STATUS_OPEN,
                        evidenceBundleRef
                });
        db.execSQL(
                "UPDATE " + TABLE_VOUCHERS +
                        " SET dispute_status = ?, current_dispute_id = ?, risk_state = CASE WHEN risk_state < ? THEN ? ELSE risk_state END, last_policy_decision_code = ?, last_policy_decision_message = ?, last_policy_decision_at = ? WHERE voucher_id = ?",
                new Object[]{
                        DISPUTE_STATUS_OPEN,
                        disputeId,
                        RISK_STATE_HOLD,
                        RISK_STATE_HOLD,
                        reasonCode,
                        "Voucher dispute opened",
                        System.currentTimeMillis(),
                        voucherId
                });
        recordRiskEvent(voucherId,
                openedBySubjectId,
                openedByRootId,
                openedByRole,
                "dispute_opened",
                20,
                reasonCode,
                "Voucher dispute opened");
    }

    public void assignDisputeResolver(String disputeId, String resolverSubjectId, String resolverRootId) {
        if (disputeId == null || disputeId.trim().isEmpty()) {
            return;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL(
                "UPDATE " + TABLE_VOUCHER_DISPUTES +
                        " SET assigned_resolver_subject_id = ?, assigned_resolver_root_id = ?, status = ? WHERE dispute_id = ?",
                new Object[]{normalizeSubjectId(resolverSubjectId), normalizeSubjectId(resolverRootId), DISPUTE_STATUS_UNDER_REVIEW, disputeId});
        db.execSQL(
                "UPDATE " + TABLE_VOUCHERS +
                        " SET dispute_status = ?, dispute_resolver_subject_id = ?, dispute_resolver_participant_root_id = ?, last_policy_decision_code = ?, last_policy_decision_message = ?, last_policy_decision_at = ? WHERE current_dispute_id = ?",
                new Object[]{
                        DISPUTE_STATUS_UNDER_REVIEW,
                        normalizeSubjectId(resolverSubjectId),
                        normalizeSubjectId(resolverRootId),
                        "DISPUTE_UNDER_REVIEW",
                        "Dispute assigned for verifier review",
                        System.currentTimeMillis(),
                        disputeId});
    }

    public void resolveDispute(String disputeId,
            String resolutionCode,
            String resolutionMessage,
            String resolverSubjectId,
            String resolverRootId) {
        if (disputeId == null || disputeId.trim().isEmpty()) {
            return;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL(
                "UPDATE " + TABLE_VOUCHER_DISPUTES +
                        " SET resolution_code = ?, resolution_message = ?, resolved_at = ?, assigned_resolver_subject_id = ?, assigned_resolver_root_id = ?, status = ? WHERE dispute_id = ?",
                new Object[]{
                        resolutionCode,
                        resolutionMessage,
                        System.currentTimeMillis(),
                        normalizeSubjectId(resolverSubjectId),
                        normalizeSubjectId(resolverRootId),
                        DISPUTE_STATUS_RESOLVED,
                        disputeId
                });
        db.execSQL(
                "UPDATE " + TABLE_VOUCHERS +
                        " SET dispute_status = ?, dispute_resolver_subject_id = ?, dispute_resolver_participant_root_id = ?, risk_state = ?, last_policy_decision_code = ?, last_policy_decision_message = ?, last_policy_decision_at = ? WHERE current_dispute_id = ?",
                new Object[]{
                        DISPUTE_STATUS_RESOLVED,
                        normalizeSubjectId(resolverSubjectId),
                        normalizeSubjectId(resolverRootId),
                        RISK_STATE_MONITOR,
                        resolutionCode,
                        resolutionMessage,
                        System.currentTimeMillis(),
                        disputeId
                });
    }

    public void recordRiskEvent(String voucherId,
            String subjectId,
            String participantRootId,
            int actorRole,
            String eventType,
            int riskScoreDelta,
            String ruleCode,
            String eventMessage) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL(
                "INSERT OR REPLACE INTO " + TABLE_VOUCHER_RISK_EVENTS +
                        " (event_id, voucher_id, subject_id, participant_root_id, actor_role, event_type, risk_score_delta, rule_code, event_message, created_at, origin, exported_to_mesh)" +
                        " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                new Object[]{
                        UUID.randomUUID().toString(),
                        voucherId,
                        normalizeSubjectId(subjectId),
                        normalizeSubjectId(participantRootId),
                        actorRole,
                        eventType,
                        riskScoreDelta,
                        ruleCode,
                        eventMessage,
                        System.currentTimeMillis(),
                        RISK_EVENT_ORIGIN_LOCAL
                });
    }

    public int getAggregatedRiskScoreForParticipant(String participantRootId) {
        String normalizedRootId = normalizeSubjectId(participantRootId);
        if (normalizedRootId == null) {
            return 0;
        }
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT COALESCE(SUM(risk_score_delta), 0) FROM " + TABLE_VOUCHER_RISK_EVENTS + " WHERE participant_root_id = ?",
                    new String[]{normalizedRootId});
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public int getVoucherRiskState(String voucherId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_VOUCHERS,
                    new String[]{"risk_state"},
                    "voucher_id = ?",
                    new String[]{voucherId},
                    null, null, null);
            return cursor.moveToFirst() ? cursor.getInt(0) : RISK_STATE_NONE;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void updateVoucherRiskState(String voucherId, int riskState, int riskScore, String riskFlagsJson, String decisionCode) {
        updateVoucherRiskState(voucherId, riskState, riskScore, riskFlagsJson, decisionCode, null);
    }

    public void updateVoucherRiskState(String voucherId,
            int riskState,
            int riskScore,
            String riskFlagsJson,
            String decisionCode,
            String decisionMessage) {
        if (voucherId == null || voucherId.trim().isEmpty()) {
            return;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL(
                "UPDATE " + TABLE_VOUCHERS +
                        " SET risk_state = ?, risk_score = ?, risk_flags_json = ?, last_policy_decision_code = ?, last_policy_decision_message = ?, last_policy_decision_at = ? WHERE voucher_id = ?",
                new Object[]{riskState, riskScore, riskFlagsJson, decisionCode, decisionMessage, System.currentTimeMillis(), voucherId});
    }

    public void updateVoucherPolicyDecision(String voucherId, String decisionCode, String decisionMessage) {
        if (voucherId == null || voucherId.trim().isEmpty()) {
            return;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL(
                "UPDATE " + TABLE_VOUCHERS +
                        " SET last_policy_decision_code = ?, last_policy_decision_message = ?, last_policy_decision_at = ? WHERE voucher_id = ?",
                new Object[]{decisionCode, decisionMessage, System.currentTimeMillis(), voucherId});
    }

    public void recordMerchantSettlementContext(String voucherId,
            String merchantSubjectId,
            String merchantParticipantRootId,
            String decisionCode,
            String decisionMessage) {
        if (voucherId == null || voucherId.trim().isEmpty()) {
            return;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL(
                "UPDATE " + TABLE_VOUCHERS +
                        " SET merchant_subject_id = ?, merchant_participant_root_id = ?, last_policy_decision_code = ?, last_policy_decision_message = ?, last_policy_decision_at = ? WHERE voucher_id = ?",
                new Object[]{
                        normalizeSubjectId(merchantSubjectId),
                        normalizeSubjectId(merchantParticipantRootId),
                        decisionCode,
                        decisionMessage,
                        System.currentTimeMillis(),
                        voucherId});
    }

    public void recordVerifierAudit(String voucherId,
            boolean manifestVerified,
            boolean ledgerMatched,
            boolean rotationDetected,
            String auditMessage,
            String inspectionSource) {
        recordVerifierAudit(voucherId,
                buildAuditRecordId(voucherId, inspectionSource, auditMessage, System.currentTimeMillis()),
                manifestVerified,
                ledgerMatched,
                rotationDetected,
                auditMessage,
                inspectionSource,
                System.currentTimeMillis());
    }

    public void recordVerifierAudit(String voucherId,
            String auditRecordId,
            boolean manifestVerified,
            boolean ledgerMatched,
            boolean rotationDetected,
            String auditMessage,
            String inspectionSource,
            long auditTime) {
        recordVerifierAudit(voucherId,
                auditRecordId,
                manifestVerified,
                ledgerMatched,
                rotationDetected,
                auditMessage,
                inspectionSource,
                auditTime,
                AUDIT_ORIGIN_LOCAL,
                null,
                false,
                0L);
    }

    public void recordVerifierAudit(String voucherId,
            String auditRecordId,
            boolean manifestVerified,
            boolean ledgerMatched,
            boolean rotationDetected,
            String auditMessage,
            String inspectionSource,
            long auditTime,
            String auditOrigin,
            String sourceNode,
            boolean exportedToMesh,
            long exportedAt) {
        if (voucherId == null || voucherId.trim().isEmpty()) {
            return;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        long resolvedAuditTime = auditTime > 0L ? auditTime : System.currentTimeMillis();
        String resolvedRecordId = auditRecordId == null || auditRecordId.trim().isEmpty()
                ? buildAuditRecordId(voucherId, inspectionSource, auditMessage, resolvedAuditTime)
                : auditRecordId;
        String resolvedAuditOrigin = auditOrigin == null || auditOrigin.trim().isEmpty()
                ? AUDIT_ORIGIN_LOCAL
                : auditOrigin.trim();
        String resolvedSourceNode = sourceNode == null || sourceNode.trim().isEmpty()
                ? null
                : sourceNode.trim();
        long resolvedExportedAt = exportedAt > 0L ? exportedAt : (exportedToMesh ? resolvedAuditTime : 0L);
        db.execSQL(
                "INSERT OR REPLACE INTO " + TABLE_VERIFIER_AUDIT_HISTORY +
                        " (audit_record_id, voucher_id, manifest_verified, ledger_matched, rotation_detected," +
                        " audit_time, audit_message, inspection_source, audit_origin, source_node, exported_to_mesh, exported_at)" +
                        " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new Object[]{
                        resolvedRecordId,
                        voucherId,
                        manifestVerified ? 1 : 0,
                        ledgerMatched ? 1 : 0,
                        rotationDetected ? 1 : 0,
                        resolvedAuditTime,
                        auditMessage,
                        inspectionSource,
                        resolvedAuditOrigin,
                        resolvedSourceNode,
                        exportedToMesh ? 1 : 0,
                        exportedToMesh ? resolvedExportedAt : null
                });
        db.execSQL(
                "UPDATE " + TABLE_VOUCHERS +
                        " SET verifier_manifest_verified = ?, verifier_ledger_matched = ?, verifier_rotation_detected = ?," +
                        " verifier_audit_time = ?, verifier_audit_message = ?, verifier_inspection_source = ?" +
                        " WHERE voucher_id = ? AND verifier_audit_time <= ?",
                new Object[]{
                        manifestVerified ? 1 : 0,
                        ledgerMatched ? 1 : 0,
                        rotationDetected ? 1 : 0,
                        resolvedAuditTime,
                        auditMessage,
                        inspectionSource,
                        voucherId,
                        resolvedAuditTime
                });
    }

    /**
     * Get all SELL vouchers pending settlement verification
     */
    public Cursor getPendingSettlementVerification() {
        SQLiteDatabase db = this.getReadableDatabase();

        return db.query(TABLE_VOUCHERS,
                null,
                "direction = 2 AND settlement_verified = 0 AND state = 5",
                null, null, null,
                "issued_time ASC");
    }

    /**
     * Get SELL vouchers that have exceeded the current stage verifier window.
     */
    public Cursor getExpiredSettlementWindows(long nowMs) {
        SQLiteDatabase db = this.getReadableDatabase();
        long cutoffTime = nowMs - SatnetRuntimeConfig.getSettlementWindowMillis();

        return db.query(TABLE_VOUCHERS,
                null,
                "direction = 2 AND settlement_verified = 0 AND state = ? AND issued_time < ?",
                new String[]{String.valueOf(BitcoinVoucher.STATE_SETTLEMENT_PENDING), String.valueOf(cutoffTime)},
                null, null, null);
    }

    /**
     * Get exchange rate for specific voucher (for audit trail)
     */
    public double getVoucherExchangeRate(String voucherId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.query(TABLE_VOUCHERS,
                    new String[]{"exchange_rate"},
                    "voucher_id = ?",
                    new String[]{voucherId},
                    null, null, null);

            if (cursor.moveToFirst()) {
                return cursor.getDouble(0);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting voucher exchange rate: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return 0.0;
    }

    public int getIssuedVoucherCountForIssuerAlias(String issuerKeystoreAlias) {
        if (issuerKeystoreAlias == null || issuerKeystoreAlias.trim().isEmpty()) {
            return 0;
        }
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM " + TABLE_VOUCHERS + " WHERE issuer_keystore_alias = ?",
                    new String[]{issuerKeystoreAlias});
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public String getVoucherIssuerKeystoreAlias(String voucherId) {
        return getVoucherStringField(voucherId, "issuer_keystore_alias");
    }

    public long getVoucherIssuerRotationEpoch(String voucherId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_VOUCHERS,
                    new String[]{"issuer_rotation_epoch"},
                    "voucher_id = ?",
                    new String[]{voucherId},
                    null, null, null);
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public String getVoucherSecondSignatureManifestJson(String voucherId) {
        return getVoucherStringField(voucherId, "secondary_signature_manifest_json");
    }

    public String getVoucherSecondPublicKeyReference(String voucherId) {
        return getVoucherStringField(voucherId, "secondary_public_key_reference");
    }

    public String getVoucherSecondMetadataReference(String voucherId) {
        return getVoucherStringField(voucherId, "secondary_metadata_reference");
    }

    public String getVoucherSecondSignatureReference(String voucherId) {
        return getVoucherStringField(voucherId, "secondary_signature_reference");
    }

    public long getVoucherIssuerActivatedAt(String voucherId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_VOUCHERS,
                    new String[]{"issuer_activated_at"},
                    "voucher_id = ?",
                    new String[]{voucherId},
                    null, null, null);
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public String getVoucherIssuerPreviousKeystoreAlias(String voucherId) {
        return getVoucherStringField(voucherId, "issuer_previous_keystore_alias");
    }

    public String getVoucherIssuerRotationReason(String voucherId) {
        return getVoucherStringField(voucherId, "issuer_rotation_reason");
    }

    public VoucherMetadataSnapshot getVoucherMetadataSnapshot(String voucherId) {
        if (voucherId == null || voucherId.trim().isEmpty()) {
            return null;
        }
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_VOUCHERS,
                    new String[]{
                            "voucher_id",
                            "payload_version",
                            "primary_issuer_key_id",
                            "secondary_signature_manifest_json",
                            "secondary_public_key_reference",
                            "secondary_metadata_reference",
                            "secondary_signature_reference",
                            "issuer_keystore_alias",
                            "issuer_rotation_epoch",
                            "issuer_activated_at",
                            "issuer_previous_keystore_alias",
                            "issuer_rotation_reason"
                    },
                    "voucher_id = ?",
                    new String[]{voucherId},
                    null, null, null);
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new VoucherMetadataSnapshot(
                    cursor.getString(cursor.getColumnIndexOrThrow("voucher_id")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("payload_version")),
                    cursor.getString(cursor.getColumnIndexOrThrow("primary_issuer_key_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("secondary_signature_manifest_json")),
                    cursor.getString(cursor.getColumnIndexOrThrow("secondary_public_key_reference")),
                    cursor.getString(cursor.getColumnIndexOrThrow("secondary_metadata_reference")),
                    cursor.getString(cursor.getColumnIndexOrThrow("secondary_signature_reference")),
                    cursor.getString(cursor.getColumnIndexOrThrow("issuer_keystore_alias")),
                    cursor.getLong(cursor.getColumnIndexOrThrow("issuer_rotation_epoch")),
                    cursor.getLong(cursor.getColumnIndexOrThrow("issuer_activated_at")),
                    cursor.getString(cursor.getColumnIndexOrThrow("issuer_previous_keystore_alias")),
                    cursor.getString(cursor.getColumnIndexOrThrow("issuer_rotation_reason")));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public VoucherParticipantSnapshot getVoucherParticipantSnapshot(String voucherId) {
        if (voucherId == null || voucherId.trim().isEmpty()) {
            return null;
        }
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_VOUCHERS,
                    new String[]{
                            "voucher_id",
                            "agent_id",
                            "agent_subject_id",
                            "agent_participant_root_id",
                            "merchant_subject_id",
                            "merchant_participant_root_id",
                            "redeemed_by_wallet",
                            "redeemer_subject_id",
                            "redeemer_participant_root_id",
                            "settlement_verifier_subject_id",
                            "settlement_verifier_participant_root_id",
                            "dispute_resolver_subject_id",
                            "dispute_resolver_participant_root_id",
                            "risk_state",
                            "risk_score",
                            "risk_flags_json",
                            "last_policy_decision_code",
                            "last_policy_decision_message",
                            "last_policy_decision_at",
                            "dispute_status",
                            "current_dispute_id",
                            "achieved_verifier_quorum",
                            "required_verifier_quorum"
                    },
                    "voucher_id = ?",
                    new String[]{voucherId},
                    null, null, null);
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new VoucherParticipantSnapshot(
                    cursor.getString(cursor.getColumnIndexOrThrow("voucher_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("agent_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("agent_subject_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("agent_participant_root_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("merchant_subject_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("merchant_participant_root_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("redeemed_by_wallet")),
                    cursor.getString(cursor.getColumnIndexOrThrow("redeemer_subject_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("redeemer_participant_root_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("settlement_verifier_subject_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("settlement_verifier_participant_root_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("dispute_resolver_subject_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("dispute_resolver_participant_root_id")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("risk_state")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("risk_score")),
                    cursor.getString(cursor.getColumnIndexOrThrow("risk_flags_json")),
                    cursor.getString(cursor.getColumnIndexOrThrow("last_policy_decision_code")),
                    cursor.getString(cursor.getColumnIndexOrThrow("last_policy_decision_message")),
                    cursor.isNull(cursor.getColumnIndexOrThrow("last_policy_decision_at"))
                            ? 0L
                            : cursor.getLong(cursor.getColumnIndexOrThrow("last_policy_decision_at")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("dispute_status")),
                    cursor.getString(cursor.getColumnIndexOrThrow("current_dispute_id")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("achieved_verifier_quorum")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("required_verifier_quorum")));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public VerifierAuditSnapshot getVerifierAuditSnapshot(String voucherId) {
        VerifierAuditRecord latestRecord = getLatestVerifierAuditRecord(voucherId);
        if (latestRecord != null) {
            return new VerifierAuditSnapshot(
                    latestRecord.voucherId,
                    latestRecord.manifestVerified ? 1 : 0,
                    latestRecord.ledgerMatched ? 1 : 0,
                    latestRecord.rotationDetected ? 1 : 0,
                    latestRecord.auditTime,
                    latestRecord.auditMessage,
                    latestRecord.inspectionSource);
        }
        if (voucherId == null || voucherId.trim().isEmpty()) {
            return null;
        }
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_VOUCHERS,
                    new String[]{
                            "voucher_id",
                            "verifier_manifest_verified",
                            "verifier_ledger_matched",
                            "verifier_rotation_detected",
                            "verifier_audit_time",
                            "verifier_audit_message",
                            "verifier_inspection_source"
                    },
                    "voucher_id = ?",
                    new String[]{voucherId},
                    null, null, null);
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new VerifierAuditSnapshot(
                    cursor.getString(cursor.getColumnIndexOrThrow("voucher_id")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("verifier_manifest_verified")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("verifier_ledger_matched")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("verifier_rotation_detected")),
                    cursor.getLong(cursor.getColumnIndexOrThrow("verifier_audit_time")),
                    cursor.getString(cursor.getColumnIndexOrThrow("verifier_audit_message")),
                    cursor.getString(cursor.getColumnIndexOrThrow("verifier_inspection_source")));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public VerifierAuditRecord getLatestVerifierAuditRecord(String voucherId) {
        if (voucherId == null || voucherId.trim().isEmpty()) {
            return null;
        }
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_VERIFIER_AUDIT_HISTORY,
                    new String[]{
                            "audit_record_id",
                            "voucher_id",
                            "manifest_verified",
                            "ledger_matched",
                            "rotation_detected",
                            "audit_time",
                            "audit_message",
                            "inspection_source",
                            "audit_origin",
                            "source_node",
                            "exported_to_mesh",
                            "exported_at"
                    },
                    "voucher_id = ?",
                    new String[]{voucherId},
                    null, null,
                    "audit_time DESC, audit_record_id DESC",
                    "1");
            return cursor.moveToFirst() ? verifierAuditRecordFromCursor(cursor) : null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public java.util.List<VerifierAuditRecord> listVerifierAuditRecords(String voucherId) {
        java.util.ArrayList<VerifierAuditRecord> records = new java.util.ArrayList<VerifierAuditRecord>();
        if (voucherId == null || voucherId.trim().isEmpty()) {
            return records;
        }
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_VERIFIER_AUDIT_HISTORY,
                    new String[]{
                            "audit_record_id",
                            "voucher_id",
                            "manifest_verified",
                            "ledger_matched",
                            "rotation_detected",
                            "audit_time",
                            "audit_message",
                            "inspection_source",
                            "audit_origin",
                            "source_node",
                            "exported_to_mesh",
                            "exported_at"
                    },
                    "voucher_id = ?",
                    new String[]{voucherId},
                    null, null,
                    "audit_time ASC, audit_record_id ASC");
            while (cursor.moveToNext()) {
                records.add(verifierAuditRecordFromCursor(cursor));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return records;
    }

    public VerifierTrustSummary getVerifierTrustSummary() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT COUNT(*) AS total_audits, " +
                            "COUNT(DISTINCT voucher_id) AS audited_vouchers, " +
                            "SUM(CASE WHEN manifest_verified = 1 THEN 1 ELSE 0 END) AS manifest_verified_count, " +
                            "SUM(CASE WHEN ledger_matched = 1 THEN 1 ELSE 0 END) AS ledger_matched_count, " +
                            "SUM(CASE WHEN manifest_verified = 1 AND ledger_matched = 1 AND rotation_detected = 0 THEN 1 ELSE 0 END) AS trusted_audit_count, " +
                            "SUM(CASE WHEN ledger_matched = 0 OR rotation_detected = 1 THEN 1 ELSE 0 END) AS caution_audit_count, " +
                            "SUM(CASE WHEN rotation_detected = 1 THEN 1 ELSE 0 END) AS rotation_alert_count, " +
                            "SUM(CASE WHEN audit_origin = ? THEN 1 ELSE 0 END) AS mesh_evidence_count, " +
                            "SUM(CASE WHEN audit_origin = ? THEN 1 ELSE 0 END) AS local_evidence_count, " +
                            "MAX(audit_time) AS latest_audit_time " +
                            "FROM " + TABLE_VERIFIER_AUDIT_HISTORY,
                    new String[]{AUDIT_ORIGIN_MESH, AUDIT_ORIGIN_LOCAL});
            if (!cursor.moveToFirst()) {
                return VerifierTrustSummary.EMPTY;
            }
            return new VerifierTrustSummary(
                    cursor.getInt(cursor.getColumnIndexOrThrow("total_audits")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("audited_vouchers")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("manifest_verified_count")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("ledger_matched_count")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("trusted_audit_count")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("caution_audit_count")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("rotation_alert_count")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("mesh_evidence_count")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("local_evidence_count")),
                    cursor.isNull(cursor.getColumnIndexOrThrow("latest_audit_time")) ? 0L : cursor.getLong(cursor.getColumnIndexOrThrow("latest_audit_time")));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public java.util.List<VerifierAuditRecord> getPendingVerifierAuditExports() {
        java.util.ArrayList<VerifierAuditRecord> records = new java.util.ArrayList<VerifierAuditRecord>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_VERIFIER_AUDIT_HISTORY,
                    new String[]{
                            "audit_record_id",
                            "voucher_id",
                            "manifest_verified",
                            "ledger_matched",
                            "rotation_detected",
                            "audit_time",
                            "audit_message",
                            "inspection_source",
                            "audit_origin",
                            "source_node",
                            "exported_to_mesh",
                            "exported_at"
                    },
                    "exported_to_mesh = 0",
                    null,
                    null, null,
                    "audit_time ASC, audit_record_id ASC");
            while (cursor.moveToNext()) {
                records.add(verifierAuditRecordFromCursor(cursor));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return records;
    }

    public void markVerifierAuditExported(String auditRecordId, long exportedAt) {
        if (auditRecordId == null || auditRecordId.trim().isEmpty()) {
            return;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL(
                "UPDATE " + TABLE_VERIFIER_AUDIT_HISTORY +
                        " SET exported_to_mesh = 1, exported_at = ? WHERE audit_record_id = ?",
                new Object[]{exportedAt > 0L ? exportedAt : System.currentTimeMillis(), auditRecordId});
    }

    public void importVerifierAuditRecord(VerifierAuditRecord auditRecord) {
        if (auditRecord == null || auditRecord.auditRecordId == null || auditRecord.auditRecordId.trim().isEmpty()) {
            return;
        }
        recordVerifierAudit(
                auditRecord.voucherId,
                auditRecord.auditRecordId,
                auditRecord.manifestVerified,
                auditRecord.ledgerMatched,
                auditRecord.rotationDetected,
                auditRecord.auditMessage,
                auditRecord.inspectionSource,
                auditRecord.auditTime,
                auditRecord.auditOrigin,
                auditRecord.sourceNode,
                auditRecord.exportedToMesh,
                auditRecord.exportedAt);
    }

    private String getVoucherStringField(String voucherId, String columnName) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_VOUCHERS,
                    new String[]{columnName},
                    "voucher_id = ?",
                    new String[]{voucherId},
                    null, null, null);
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void createVoucherTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_VOUCHERS + " (" +
                "voucher_id TEXT PRIMARY KEY," +
                "agent_id TEXT NOT NULL," +
                "agent_subject_id TEXT," +
                "denomination INTEGER NOT NULL," +
                "secret_hash TEXT NOT NULL," +
                "issued_time INTEGER NOT NULL," +
                "expiry_time INTEGER NOT NULL," +
                "state INTEGER NOT NULL," +
                "redeemed_time INTEGER," +
                "redeemed_by_wallet TEXT," +
                "synced_to_mesh BOOLEAN DEFAULT 0," +
                "direction INTEGER NOT NULL DEFAULT 1," +
                "exchange_rate REAL NOT NULL DEFAULT 0," +
                "currency_code TEXT DEFAULT 'USD'," +
                "rate_lock_time INTEGER NOT NULL DEFAULT 0," +
                "settlement_verified INTEGER NOT NULL DEFAULT 0," +
                "settlement_verified_time INTEGER," +
                "payload_version INTEGER NOT NULL DEFAULT 1," +
                "canonical_payload TEXT," +
                "signature_bundle_json TEXT," +
                "primary_signature_algorithm TEXT," +
                "primary_issuer_key_id TEXT," +
                "secondary_signature_manifest_json TEXT," +
                "secondary_public_key_reference TEXT," +
                "secondary_metadata_reference TEXT," +
                "secondary_signature_reference TEXT," +
                "issuer_keystore_alias TEXT," +
                "issuer_rotation_epoch INTEGER NOT NULL DEFAULT 0," +
                "issuer_activated_at INTEGER NOT NULL DEFAULT 0," +
                "issuer_previous_keystore_alias TEXT," +
                "issuer_rotation_reason TEXT DEFAULT 'active'," +
                "agent_participant_root_id TEXT," +
                "redeemer_subject_id TEXT," +
                "redeemer_participant_root_id TEXT," +
                "merchant_subject_id TEXT," +
                "merchant_participant_root_id TEXT," +
                "settlement_verifier_subject_id TEXT," +
                "settlement_verifier_participant_root_id TEXT," +
                "dispute_resolver_subject_id TEXT," +
                "dispute_resolver_participant_root_id TEXT," +
                "verifier_manifest_verified INTEGER NOT NULL DEFAULT -1," +
                "verifier_ledger_matched INTEGER NOT NULL DEFAULT -1," +
                "verifier_rotation_detected INTEGER NOT NULL DEFAULT -1," +
                "verifier_audit_time INTEGER NOT NULL DEFAULT 0," +
                "verifier_audit_message TEXT," +
                "verifier_inspection_source TEXT," +
                "risk_state INTEGER NOT NULL DEFAULT 0," +
                "risk_score INTEGER NOT NULL DEFAULT 0," +
                "risk_flags_json TEXT," +
                "dispute_status INTEGER NOT NULL DEFAULT 0," +
                "current_dispute_id TEXT," +
                "required_verifier_quorum INTEGER NOT NULL DEFAULT 1," +
                "achieved_verifier_quorum INTEGER NOT NULL DEFAULT 0," +
                "last_policy_decision_code TEXT," +
                "last_policy_decision_message TEXT," +
                "last_policy_decision_at INTEGER" +
                ")");
    }

    private void createRedemptionsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_REDEMPTIONS + " (" +
                "redemption_id TEXT PRIMARY KEY," +
                "voucher_id TEXT NOT NULL," +
                "user_wallet TEXT NOT NULL," +
                "redeemer_subject_id TEXT," +
                "amount_sats INTEGER NOT NULL," +
                "timestamp INTEGER NOT NULL," +
                "tx_hash TEXT," +
                "confirmed BOOLEAN DEFAULT 0," +
                "FOREIGN KEY(voucher_id) REFERENCES vouchers(voucher_id)" +
                ")");
    }

    private void createVerifierAuditHistoryTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_VERIFIER_AUDIT_HISTORY + " (" +
                "audit_record_id TEXT PRIMARY KEY," +
                "voucher_id TEXT NOT NULL," +
                "manifest_verified INTEGER NOT NULL DEFAULT 0," +
                "ledger_matched INTEGER NOT NULL DEFAULT 0," +
                "rotation_detected INTEGER NOT NULL DEFAULT 0," +
                "audit_time INTEGER NOT NULL," +
                "audit_message TEXT," +
                "inspection_source TEXT," +
                "audit_origin TEXT NOT NULL DEFAULT 'local'," +
                "source_node TEXT," +
                "exported_to_mesh INTEGER NOT NULL DEFAULT 0," +
                "exported_at INTEGER" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS verifier_audit_history_voucher_idx ON " + TABLE_VERIFIER_AUDIT_HISTORY + " (voucher_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS verifier_audit_history_time_idx ON " + TABLE_VERIFIER_AUDIT_HISTORY + " (audit_time)");
        db.execSQL("CREATE INDEX IF NOT EXISTS verifier_audit_history_export_idx ON " + TABLE_VERIFIER_AUDIT_HISTORY + " (exported_to_mesh, audit_time)");
    }

    private void createVoucherDisputesTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_VOUCHER_DISPUTES + " (" +
                "dispute_id TEXT PRIMARY KEY," +
                "voucher_id TEXT NOT NULL," +
                "opened_by_subject_id TEXT," +
                "opened_by_role INTEGER NOT NULL," +
                "opened_by_root_id TEXT," +
                "opened_at INTEGER NOT NULL," +
                "reason_code TEXT NOT NULL," +
                "status INTEGER NOT NULL," +
                "assigned_resolver_subject_id TEXT," +
                "assigned_resolver_root_id TEXT," +
                "resolution_code TEXT," +
                "resolution_message TEXT," +
                "resolved_at INTEGER," +
                "evidence_bundle_ref TEXT," +
                "sync_state INTEGER NOT NULL DEFAULT 0" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS voucher_disputes_voucher_idx ON " + TABLE_VOUCHER_DISPUTES + " (voucher_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS voucher_disputes_status_idx ON " + TABLE_VOUCHER_DISPUTES + " (status, opened_at)");
    }

    private void createVoucherRiskEventsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_VOUCHER_RISK_EVENTS + " (" +
                "event_id TEXT PRIMARY KEY," +
                "voucher_id TEXT," +
                "subject_id TEXT," +
                "participant_root_id TEXT," +
                "actor_role INTEGER NOT NULL," +
                "event_type TEXT NOT NULL," +
                "risk_score_delta INTEGER NOT NULL DEFAULT 0," +
                "rule_code TEXT NOT NULL," +
                "event_message TEXT," +
                "created_at INTEGER NOT NULL," +
                "origin TEXT NOT NULL DEFAULT 'local'," +
                "source_node TEXT," +
                "exported_to_mesh INTEGER NOT NULL DEFAULT 0," +
                "exported_at INTEGER" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS voucher_risk_events_voucher_idx ON " + TABLE_VOUCHER_RISK_EVENTS + " (voucher_id, created_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS voucher_risk_events_participant_idx ON " + TABLE_VOUCHER_RISK_EVENTS + " (participant_root_id, created_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS voucher_risk_events_type_idx ON " + TABLE_VOUCHER_RISK_EVENTS + " (event_type, created_at)");
    }

    private void ensureVoucherSchema(SQLiteDatabase db) {
        createVoucherTable(db);
        createRedemptionsTable(db);
        createVerifierAuditHistoryTable(db);
        createVoucherDisputesTable(db);
        createVoucherRiskEventsTable(db);
        addColumnIfMissing(db, TABLE_VOUCHERS, "agent_subject_id", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "agent_participant_root_id", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "direction", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing(db, TABLE_VOUCHERS, "exchange_rate", "REAL NOT NULL DEFAULT 0");
        addColumnIfMissing(db, TABLE_VOUCHERS, "currency_code", "TEXT DEFAULT 'USD'");
        addColumnIfMissing(db, TABLE_VOUCHERS, "rate_lock_time", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db, TABLE_VOUCHERS, "settlement_verified", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db, TABLE_VOUCHERS, "settlement_verified_time", "INTEGER");
        addColumnIfMissing(db, TABLE_VOUCHERS, "payload_version", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing(db, TABLE_VOUCHERS, "canonical_payload", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "signature_bundle_json", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "primary_signature_algorithm", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "primary_issuer_key_id", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "secondary_signature_manifest_json", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "secondary_public_key_reference", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "secondary_metadata_reference", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "secondary_signature_reference", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "issuer_keystore_alias", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "issuer_rotation_epoch", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db, TABLE_VOUCHERS, "issuer_activated_at", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db, TABLE_VOUCHERS, "issuer_previous_keystore_alias", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "issuer_rotation_reason", "TEXT DEFAULT 'active'");
        addColumnIfMissing(db, TABLE_VOUCHERS, "redeemer_subject_id", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "redeemer_participant_root_id", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "merchant_subject_id", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "merchant_participant_root_id", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "settlement_verifier_subject_id", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "settlement_verifier_participant_root_id", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "dispute_resolver_subject_id", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "dispute_resolver_participant_root_id", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "verifier_manifest_verified", "INTEGER NOT NULL DEFAULT -1");
        addColumnIfMissing(db, TABLE_VOUCHERS, "verifier_ledger_matched", "INTEGER NOT NULL DEFAULT -1");
        addColumnIfMissing(db, TABLE_VOUCHERS, "verifier_rotation_detected", "INTEGER NOT NULL DEFAULT -1");
        addColumnIfMissing(db, TABLE_VOUCHERS, "verifier_audit_time", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db, TABLE_VOUCHERS, "verifier_audit_message", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "verifier_inspection_source", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "risk_state", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db, TABLE_VOUCHERS, "risk_score", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db, TABLE_VOUCHERS, "risk_flags_json", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "dispute_status", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db, TABLE_VOUCHERS, "current_dispute_id", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "required_verifier_quorum", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing(db, TABLE_VOUCHERS, "achieved_verifier_quorum", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db, TABLE_VOUCHERS, "last_policy_decision_code", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "last_policy_decision_message", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHERS, "last_policy_decision_at", "INTEGER");
        addColumnIfMissing(db, TABLE_REDEMPTIONS, "redeemer_subject_id", "TEXT");
        addColumnIfMissing(db, TABLE_VERIFIER_AUDIT_HISTORY, "manifest_verified", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db, TABLE_VERIFIER_AUDIT_HISTORY, "ledger_matched", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db, TABLE_VERIFIER_AUDIT_HISTORY, "rotation_detected", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db, TABLE_VERIFIER_AUDIT_HISTORY, "audit_time", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db, TABLE_VERIFIER_AUDIT_HISTORY, "audit_message", "TEXT");
        addColumnIfMissing(db, TABLE_VERIFIER_AUDIT_HISTORY, "inspection_source", "TEXT");
        addColumnIfMissing(db, TABLE_VERIFIER_AUDIT_HISTORY, "audit_origin", "TEXT NOT NULL DEFAULT 'local'");
        addColumnIfMissing(db, TABLE_VERIFIER_AUDIT_HISTORY, "source_node", "TEXT");
        addColumnIfMissing(db, TABLE_VERIFIER_AUDIT_HISTORY, "exported_to_mesh", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db, TABLE_VERIFIER_AUDIT_HISTORY, "exported_at", "INTEGER");
        addColumnIfMissing(db, TABLE_VOUCHER_DISPUTES, "assigned_resolver_subject_id", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHER_DISPUTES, "assigned_resolver_root_id", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHER_DISPUTES, "resolution_code", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHER_DISPUTES, "resolution_message", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHER_DISPUTES, "resolved_at", "INTEGER");
        addColumnIfMissing(db, TABLE_VOUCHER_DISPUTES, "evidence_bundle_ref", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHER_DISPUTES, "sync_state", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db, TABLE_VOUCHER_RISK_EVENTS, "source_node", "TEXT");
        addColumnIfMissing(db, TABLE_VOUCHER_RISK_EVENTS, "exported_to_mesh", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db, TABLE_VOUCHER_RISK_EVENTS, "exported_at", "INTEGER");
    }

    private static String buildAuditRecordId(String voucherId, String inspectionSource, String auditMessage, long auditTime) {
        String normalizedVoucherId = voucherId == null ? "voucher" : voucherId.trim();
        String normalizedSource = inspectionSource == null ? "inspection" : inspectionSource.trim();
        String normalizedMessage = auditMessage == null ? "" : auditMessage.trim();
        return normalizedVoucherId + ":" + normalizedSource + ":" + auditTime + ":" + Integer.toHexString(normalizedMessage.hashCode());
    }

    private VerifierAuditRecord verifierAuditRecordFromCursor(Cursor cursor) {
        return new VerifierAuditRecord(
                cursor.getString(cursor.getColumnIndexOrThrow("audit_record_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("voucher_id")),
                cursor.getInt(cursor.getColumnIndexOrThrow("manifest_verified")) > 0,
                cursor.getInt(cursor.getColumnIndexOrThrow("ledger_matched")) > 0,
                cursor.getInt(cursor.getColumnIndexOrThrow("rotation_detected")) > 0,
                cursor.getLong(cursor.getColumnIndexOrThrow("audit_time")),
                cursor.getString(cursor.getColumnIndexOrThrow("audit_message")),
                cursor.getString(cursor.getColumnIndexOrThrow("inspection_source")),
                cursor.getString(cursor.getColumnIndexOrThrow("audit_origin")),
                cursor.getString(cursor.getColumnIndexOrThrow("source_node")),
                cursor.getInt(cursor.getColumnIndexOrThrow("exported_to_mesh")) > 0,
                cursor.isNull(cursor.getColumnIndexOrThrow("exported_at")) ? 0L : cursor.getLong(cursor.getColumnIndexOrThrow("exported_at")));
    }

    private String normalizeSubjectId(String subjectId) {
        if (subjectId == null) {
            return null;
        }
        String normalized = subjectId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void addColumnIfMissing(SQLiteDatabase db, String tableName, String columnName, String columnDefinition) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
            while (cursor.moveToNext()) {
                if (columnName.equalsIgnoreCase(cursor.getString(cursor.getColumnIndexOrThrow("name")))) {
                    return;
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        db.execSQL("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
    }

    public static final class VoucherMetadataSnapshot {
        public final String voucherId;
        public final int payloadVersion;
        public final String primaryIssuerKeyId;
        public final String secondSignatureManifestJson;
        public final String secondPublicKeyReference;
        public final String secondMetadataReference;
        public final String secondSignatureReference;
        public final String issuerKeystoreAlias;
        public final long issuerRotationEpoch;
        public final long issuerActivatedAt;
        public final String issuerPreviousKeystoreAlias;
        public final String issuerRotationReason;

        VoucherMetadataSnapshot(String voucherId,
                int payloadVersion,
                String primaryIssuerKeyId,
                String secondSignatureManifestJson,
                String secondPublicKeyReference,
                String secondMetadataReference,
                String secondSignatureReference,
                String issuerKeystoreAlias,
                long issuerRotationEpoch,
                long issuerActivatedAt,
                String issuerPreviousKeystoreAlias,
                String issuerRotationReason) {
            this.voucherId = voucherId;
            this.payloadVersion = payloadVersion;
            this.primaryIssuerKeyId = primaryIssuerKeyId;
            this.secondSignatureManifestJson = secondSignatureManifestJson;
            this.secondPublicKeyReference = secondPublicKeyReference;
            this.secondMetadataReference = secondMetadataReference;
            this.secondSignatureReference = secondSignatureReference;
            this.issuerKeystoreAlias = issuerKeystoreAlias;
            this.issuerRotationEpoch = issuerRotationEpoch;
            this.issuerActivatedAt = issuerActivatedAt;
            this.issuerPreviousKeystoreAlias = issuerPreviousKeystoreAlias;
            this.issuerRotationReason = issuerRotationReason;
        }

        public boolean hasPhase3Metadata() {
            return (secondSignatureManifestJson != null && !secondSignatureManifestJson.trim().isEmpty())
                    || (issuerKeystoreAlias != null && !issuerKeystoreAlias.trim().isEmpty())
                    || issuerRotationEpoch > 0L;
        }
    }

    public static final class VerifierAuditSnapshot {
        public final String voucherId;
        public final int manifestVerifiedState;
        public final int ledgerMatchedState;
        public final int rotationDetectedState;
        public final long auditTime;
        public final String auditMessage;
        public final String inspectionSource;

        VerifierAuditSnapshot(String voucherId,
                int manifestVerifiedState,
                int ledgerMatchedState,
                int rotationDetectedState,
                long auditTime,
                String auditMessage,
                String inspectionSource) {
            this.voucherId = voucherId;
            this.manifestVerifiedState = manifestVerifiedState;
            this.ledgerMatchedState = ledgerMatchedState;
            this.rotationDetectedState = rotationDetectedState;
            this.auditTime = auditTime;
            this.auditMessage = auditMessage;
            this.inspectionSource = inspectionSource;
        }

        public boolean hasAudit() {
            return auditTime > 0L || manifestVerifiedState >= 0 || ledgerMatchedState >= 0 || rotationDetectedState >= 0;
        }

        public boolean isManifestVerified() {
            return manifestVerifiedState > 0;
        }

        public boolean isLedgerMatched() {
            return ledgerMatchedState > 0;
        }

        public boolean isRotationDetected() {
            return rotationDetectedState > 0;
        }
    }

    public static final class VerifierAuditRecord {
        public final String auditRecordId;
        public final String voucherId;
        public final boolean manifestVerified;
        public final boolean ledgerMatched;
        public final boolean rotationDetected;
        public final long auditTime;
        public final String auditMessage;
        public final String inspectionSource;
        public final String auditOrigin;
        public final String sourceNode;
        public final boolean exportedToMesh;
        public final long exportedAt;

        public VerifierAuditRecord(String auditRecordId,
                String voucherId,
                boolean manifestVerified,
                boolean ledgerMatched,
                boolean rotationDetected,
                long auditTime,
                String auditMessage,
                String inspectionSource,
                String auditOrigin,
                String sourceNode,
                boolean exportedToMesh,
                long exportedAt) {
            this.auditRecordId = auditRecordId;
            this.voucherId = voucherId;
            this.manifestVerified = manifestVerified;
            this.ledgerMatched = ledgerMatched;
            this.rotationDetected = rotationDetected;
            this.auditTime = auditTime;
            this.auditMessage = auditMessage;
            this.inspectionSource = inspectionSource;
            this.auditOrigin = auditOrigin;
            this.sourceNode = sourceNode;
            this.exportedToMesh = exportedToMesh;
            this.exportedAt = exportedAt;
        }
    }

    public static final class VerifierTrustSummary {
        public static final VerifierTrustSummary EMPTY = new VerifierTrustSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0L);

        public final int totalAudits;
        public final int auditedVoucherCount;
        public final int manifestVerifiedCount;
        public final int ledgerMatchedCount;
        public final int trustedAuditCount;
        public final int cautionAuditCount;
        public final int rotationAlertCount;
        public final int meshEvidenceCount;
        public final int localEvidenceCount;
        public final long latestAuditTime;

        VerifierTrustSummary(int totalAudits,
                             int auditedVoucherCount,
                             int manifestVerifiedCount,
                             int ledgerMatchedCount,
                             int trustedAuditCount,
                             int cautionAuditCount,
                             int rotationAlertCount,
                             int meshEvidenceCount,
                             int localEvidenceCount,
                             long latestAuditTime) {
            this.totalAudits = Math.max(0, totalAudits);
            this.auditedVoucherCount = Math.max(0, auditedVoucherCount);
            this.manifestVerifiedCount = Math.max(0, manifestVerifiedCount);
            this.ledgerMatchedCount = Math.max(0, ledgerMatchedCount);
            this.trustedAuditCount = Math.max(0, trustedAuditCount);
            this.cautionAuditCount = Math.max(0, cautionAuditCount);
            this.rotationAlertCount = Math.max(0, rotationAlertCount);
            this.meshEvidenceCount = Math.max(0, meshEvidenceCount);
            this.localEvidenceCount = Math.max(0, localEvidenceCount);
            this.latestAuditTime = Math.max(0L, latestAuditTime);
        }

        public boolean hasEvidence() {
            return totalAudits > 0 || latestAuditTime > 0L;
        }

        public boolean hasCautionSignals() {
            return cautionAuditCount > 0 || rotationAlertCount > 0;
        }

        public boolean hasStrongTrustSignals() {
            return trustedAuditCount > 0 && !hasCautionSignals();
        }
    }

    public static final class MerchantSettlementContext {
        public final String merchantSubjectId;
        public final String merchantParticipantRootId;
        public final String settlementStatus;
        public final String decisionMessage;
        public final long decisionAt;

        public MerchantSettlementContext(String merchantSubjectId,
                String merchantParticipantRootId,
                String settlementStatus,
                String decisionMessage,
                long decisionAt) {
            this.merchantSubjectId = merchantSubjectId;
            this.merchantParticipantRootId = merchantParticipantRootId;
            this.settlementStatus = settlementStatus;
            this.decisionMessage = decisionMessage;
            this.decisionAt = decisionAt;
        }
    }
}

