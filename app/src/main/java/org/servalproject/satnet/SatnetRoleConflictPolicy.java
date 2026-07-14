package org.servalproject.satnet;

import org.servalproject.voucher.VoucherParticipantSnapshot;

/**
 * Shared conflict-of-interest rules for multi-role SATNET participants.
 *
 * A single user/device may register multiple roles, but that participant must not
 * review, verify, or resolve disputes on records where they are already a party.
 */
public final class SatnetRoleConflictPolicy {
    public static final int ACTION_ISSUE_VOUCHER = 1;
    public static final int ACTION_REDEEM_VOUCHER = 2;
    public static final int ACTION_ACCEPT_MERCHANT_PAYMENT = 3;
    public static final int ACTION_INSPECT_VOUCHER = 4;
    public static final int ACTION_VERIFY_SETTLEMENT = 5;
    public static final int ACTION_RESOLVE_DISPUTE = 6;

    public static final int RECOMMENDED_RISK_STATE_NONE = 0;
    public static final int RECOMMENDED_RISK_STATE_MONITOR = 1;
    public static final int RECOMMENDED_RISK_STATE_HOLD = 2;
    private SatnetRoleConflictPolicy() {
    }

    public static ConflictCheck canReviewVoucher(SatnetRoleManager roleManager,
            VoucherParticipantSnapshot participantSnapshot) {
        return authorizeAction(roleManager, ACTION_INSPECT_VOUCHER, SatnetRoleManager.ROLE_VERIFIER, participantSnapshot);
    }

    public static ConflictCheck canReviewVoucher(String actorSubjectId,
            VoucherParticipantSnapshot participantSnapshot) {
        return authorizeAction(actorSubjectId, null, ACTION_INSPECT_VOUCHER, SatnetRoleManager.ROLE_VERIFIER, participantSnapshot);
    }

    @SuppressWarnings("unused")
    public static ConflictCheck canResolveDispute(String actorSubjectId,
            VoucherParticipantSnapshot participantSnapshot) {
        return authorizeAction(actorSubjectId, null, ACTION_RESOLVE_DISPUTE, SatnetRoleManager.ROLE_VERIFIER, participantSnapshot);
    }

    public static ConflictCheck authorizeAction(SatnetRoleManager roleManager,
            int actionType,
            int actorRole,
            VoucherParticipantSnapshot participantSnapshot) {
        if (roleManager == null) {
            return authorizeAction(null, null, actionType, actorRole, participantSnapshot);
        }
        String actorSubjectId = actorRole == SatnetRoleManager.ROLE_NONE
                ? roleManager.getParticipantSubjectId()
                : roleManager.getRoleSubjectId(actorRole);
        return authorizeAction(actorSubjectId,
                roleManager.getParticipantRootSubjectId(),
                actionType,
                actorRole,
                participantSnapshot);
    }

    public static ConflictCheck authorizeAction(String actorRoleSubjectId,
            String actorRootSubjectId,
            int actionType,
            int actorRole,
            VoucherParticipantSnapshot participantSnapshot) {
        String normalizedSubjectId = normalize(actorRoleSubjectId);
        String normalizedRootId = normalize(actorRootSubjectId);
        if ((normalizedSubjectId == null && normalizedRootId == null) || participantSnapshot == null) {
            return ConflictCheck.allow();
        }
        switch (actionType) {
            case ACTION_ISSUE_VOUCHER:
                return canIssueVoucher(normalizedRootId, actorRole, participantSnapshot);
            case ACTION_REDEEM_VOUCHER:
                return canRedeemVoucher(normalizedSubjectId, normalizedRootId, actorRole, participantSnapshot);
            case ACTION_ACCEPT_MERCHANT_PAYMENT:
                return canAcceptMerchantPayment(normalizedSubjectId, normalizedRootId, actorRole, participantSnapshot);
            case ACTION_INSPECT_VOUCHER:
            case ACTION_VERIFY_SETTLEMENT:
                return canInspectOrVerify(normalizedSubjectId, normalizedRootId, participantSnapshot, actionType == ACTION_INSPECT_VOUCHER);
            case ACTION_RESOLVE_DISPUTE:
                return canResolveDisputeInternal(normalizedSubjectId, normalizedRootId, participantSnapshot);
            default:
                return ConflictCheck.allow();
        }
    }

    public static ConflictCheck canIssueVoucher(String actorRootSubjectId,
            int actorRole,
            VoucherParticipantSnapshot participantSnapshot) {
        if (actorRole == SatnetRoleManager.ROLE_NONE) {
            return ConflictCheck.allow();
        }
        if (actorRole == SatnetRoleManager.ROLE_VERIFIER
                && sameParticipant(actorRootSubjectId, null,
                participantSnapshot.settlementVerifierParticipantRootId,
                participantSnapshot.settlementVerifierSubjectId)) {
            return ConflictCheck.deny("PREVIOUS_VERIFIER_DISPUTE_CONFLICT",
                    "Conflict of interest: your verifier profile cannot issue a voucher it already reviewed.",
                    RECOMMENDED_RISK_STATE_MONITOR,
                    true);
        }
        return ConflictCheck.allow();
    }

    public static ConflictCheck canRedeemVoucher(String actorRoleSubjectId,
            String actorRootSubjectId,
            int actorRole,
            VoucherParticipantSnapshot participantSnapshot) {
        if (sameParticipant(actorRootSubjectId, actorRoleSubjectId,
                participantSnapshot.disputeResolverParticipantRootId,
                participantSnapshot.disputeResolverSubjectId)) {
            return ConflictCheck.deny("DISPUTE_ALREADY_OWNED",
                    "Conflict of interest: you cannot redeem a voucher tied to your own dispute resolution profile.",
                    RECOMMENDED_RISK_STATE_MONITOR,
                    true);
        }
        if (participantSnapshot.disputeState > 0 && participantSnapshot.requiredQuorum > participantSnapshot.achievedQuorum) {
            return ConflictCheck.deny("RISK_HOLD",
                    "This voucher is currently under dispute review and cannot be redeemed yet.",
                    RECOMMENDED_RISK_STATE_HOLD,
                    false);
        }
        return ConflictCheck.allow();
    }

    public static ConflictCheck canAcceptMerchantPayment(String actorRoleSubjectId,
            String actorRootSubjectId,
            int actorRole,
            VoucherParticipantSnapshot participantSnapshot) {
        if (actorRole == SatnetRoleManager.ROLE_NONE) {
            return ConflictCheck.allow();
        }
        if (sameParticipant(actorRootSubjectId, actorRoleSubjectId,
                participantSnapshot.settlementVerifierParticipantRootId,
                participantSnapshot.settlementVerifierSubjectId)) {
            return ConflictCheck.deny("SELF_MERCHANT_VERIFY",
                    "Conflict of interest: a merchant profile cannot accept payment on a voucher already handled by the same participant as verifier.",
                    RECOMMENDED_RISK_STATE_MONITOR,
                    true);
        }
        return ConflictCheck.allow();
    }

    private static ConflictCheck canInspectOrVerify(String actorSubjectId,
            String actorRootSubjectId,
            VoucherParticipantSnapshot participantSnapshot,
            boolean inspectOnly) {
        if (sameParticipant(actorRootSubjectId, actorSubjectId,
                participantSnapshot.agentParticipantRootId,
                participantSnapshot.agentSubjectId)) {
            return ConflictCheck.deny("SELF_AGENT_VERIFY",
                    "Conflict of interest: you cannot verify or inspect vouchers issued under your own agent profile.",
                    RECOMMENDED_RISK_STATE_HOLD,
                    true);
        }
        if (sameParticipant(actorRootSubjectId, actorSubjectId,
                participantSnapshot.redeemerParticipantRootId,
                participantSnapshot.redeemerSubjectId)) {
            return ConflictCheck.deny("SELF_REDEEM_VERIFY",
                    inspectOnly
                            ? "Conflict of interest: you cannot inspect settlements tied to your own redemption."
                            : "Conflict of interest: you cannot verify settlements tied to your own redemption.",
                    RECOMMENDED_RISK_STATE_HOLD,
                    true);
        }
        if (sameParticipant(actorRootSubjectId, actorSubjectId,
                participantSnapshot.merchantParticipantRootId,
                participantSnapshot.merchantSubjectId)) {
            return ConflictCheck.deny("SELF_MERCHANT_VERIFY",
                    "Conflict of interest: you cannot review merchant activity that belongs to your own participant profile.",
                    RECOMMENDED_RISK_STATE_HOLD,
                    true);
        }
        if (sameParticipant(actorRootSubjectId, actorSubjectId,
                participantSnapshot.settlementVerifierParticipantRootId,
                participantSnapshot.settlementVerifierSubjectId)) {
            return ConflictCheck.deny("CONFLICT_PREVIOUS_REVIEWER",
                    "Conflict of interest: this settlement was already handled by your verifier profile.",
                    RECOMMENDED_RISK_STATE_MONITOR,
                    true);
        }
        if (sameParticipant(actorRootSubjectId, actorSubjectId,
                participantSnapshot.disputeResolverParticipantRootId,
                participantSnapshot.disputeResolverSubjectId)) {
            return ConflictCheck.deny("DISPUTE_ALREADY_OWNED",
                    "Conflict of interest: you cannot review a dispute that you already resolved.",
                    RECOMMENDED_RISK_STATE_MONITOR,
                    true);
        }
        return ConflictCheck.allow();
    }

    private static ConflictCheck canResolveDisputeInternal(String actorSubjectId,
            String actorRootSubjectId,
            VoucherParticipantSnapshot participantSnapshot) {
        if (sameParticipant(actorRootSubjectId, actorSubjectId,
                participantSnapshot.agentParticipantRootId,
                participantSnapshot.agentSubjectId)) {
            return ConflictCheck.deny("SELF_AGENT_VERIFY",
                    "Conflict of interest: you cannot resolve a dispute involving your own agent profile.",
                    RECOMMENDED_RISK_STATE_HOLD,
                    true);
        }
        if (sameParticipant(actorRootSubjectId, actorSubjectId,
                participantSnapshot.redeemerParticipantRootId,
                participantSnapshot.redeemerSubjectId)) {
            return ConflictCheck.deny("SELF_REDEEM_VERIFY",
                    "Conflict of interest: you cannot resolve your own dispute.",
                    RECOMMENDED_RISK_STATE_HOLD,
                    true);
        }
        if (sameParticipant(actorRootSubjectId, actorSubjectId,
                participantSnapshot.merchantParticipantRootId,
                participantSnapshot.merchantSubjectId)) {
            return ConflictCheck.deny("SELF_MERCHANT_VERIFY",
                    "Conflict of interest: you cannot resolve a dispute involving your own merchant profile.",
                    RECOMMENDED_RISK_STATE_HOLD,
                    true);
        }
        if (sameParticipant(actorRootSubjectId, actorSubjectId,
                participantSnapshot.settlementVerifierParticipantRootId,
                participantSnapshot.settlementVerifierSubjectId)) {
            return ConflictCheck.deny("PREVIOUS_VERIFIER_DISPUTE_CONFLICT",
                    "Conflict of interest: the original verifier cannot act as the dispute resolver.",
                    RECOMMENDED_RISK_STATE_HOLD,
                    true);
        }
        if (sameParticipant(actorRootSubjectId, actorSubjectId,
                participantSnapshot.disputeResolverParticipantRootId,
                participantSnapshot.disputeResolverSubjectId)) {
            return ConflictCheck.deny("DISPUTE_ALREADY_OWNED",
                    "Conflict of interest: this dispute already belongs to your resolver profile.",
                    RECOMMENDED_RISK_STATE_MONITOR,
                    true);
        }
        return ConflictCheck.allow();
    }

    private static boolean sameSubject(String left, String right) {
        return left != null && left.equals(normalize(right));
    }

    private static boolean sameParticipant(String actorRootId,
            String actorSubjectId,
            String recordRootId,
            String recordSubjectId) {
        String normalizedActorRootId = normalize(actorRootId);
        String normalizedRecordRootId = normalize(recordRootId);
        if (normalizedActorRootId != null && normalizedRecordRootId != null) {
            return normalizedActorRootId.equals(normalizedRecordRootId);
        }
        return sameSubject(actorSubjectId, recordSubjectId);
    }

    private static String normalize(String subjectId) {
        if (subjectId == null) {
            return null;
        }
        String normalized = subjectId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public static final class ConflictCheck {
        public final boolean allowed;
        public final String reasonCode;
        public final String message;
        public final int recommendedRiskState;
        public final boolean shouldRecordRiskEvent;

        private ConflictCheck(boolean allowed,
                String reasonCode,
                String message,
                int recommendedRiskState,
                boolean shouldRecordRiskEvent) {
            this.allowed = allowed;
            this.reasonCode = reasonCode;
            this.message = message;
            this.recommendedRiskState = recommendedRiskState;
            this.shouldRecordRiskEvent = shouldRecordRiskEvent;
        }

        public static ConflictCheck allow() {
            return new ConflictCheck(true, null, null, RECOMMENDED_RISK_STATE_NONE, false);
        }

        public static ConflictCheck deny(String message) {
            return deny("CONFLICT_SELF_PARTICIPATION", message, RECOMMENDED_RISK_STATE_HOLD, true);
        }

        public static ConflictCheck deny(String reasonCode,
                String message,
                int recommendedRiskState,
                boolean shouldRecordRiskEvent) {
            return new ConflictCheck(false, reasonCode, message, recommendedRiskState, shouldRecordRiskEvent);
        }
    }
}

