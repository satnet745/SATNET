package org.servalproject.satnet;

import org.junit.Test;
import org.servalproject.voucher.VoucherParticipantSnapshot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SatnetRoleConflictPolicyTest {

    private static VoucherParticipantSnapshot snapshot(String voucherId,
            String agentId,
            String agentSubjectId,
            String agentParticipantRootId,
            String merchantSubjectId,
            String merchantParticipantRootId,
            String redeemedByWallet,
            String redeemerSubjectId,
            String redeemerParticipantRootId,
            String settlementVerifierSubjectId,
            String settlementVerifierParticipantRootId,
            String disputeResolverSubjectId,
            int riskState,
            int riskScore,
            String riskFlagsJson,
            String decisionCode,
            int disputeState,
            String disputeId,
            int achievedQuorum,
            int requiredQuorum) {
        return new VoucherParticipantSnapshot(
                voucherId,
                agentId,
                agentSubjectId,
                agentParticipantRootId,
                merchantSubjectId,
                merchantParticipantRootId,
                redeemedByWallet,
                redeemerSubjectId,
                redeemerParticipantRootId,
                settlementVerifierSubjectId,
                settlementVerifierParticipantRootId,
                disputeResolverSubjectId,
                null,
                riskState,
                riskScore,
                riskFlagsJson,
                decisionCode,
                null,
                0L,
                disputeState,
                disputeId,
                achievedQuorum,
                requiredQuorum);
    }

    @Test
    public void verifySettlementBlocksSameParticipantByRootIdEvenWhenSubjectsDiffer() {
        VoucherParticipantSnapshot snapshot = snapshot(
                "voucher-1",
                "agent-1",
                "agent-subject-a",
                "shared-root",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                null,
                null,
                0,
                null,
                0,
                1);

        SatnetRoleConflictPolicy.ConflictCheck conflictCheck = SatnetRoleConflictPolicy.authorizeAction(
                "verifier-subject-b",
                "shared-root",
                SatnetRoleConflictPolicy.ACTION_VERIFY_SETTLEMENT,
                SatnetRoleManager.ROLE_VERIFIER,
                snapshot);

        assertFalse(conflictCheck.allowed);
        assertEquals("SELF_AGENT_VERIFY", conflictCheck.reasonCode);
        assertEquals(SatnetRoleConflictPolicy.RECOMMENDED_RISK_STATE_HOLD, conflictCheck.recommendedRiskState);
        assertTrue(conflictCheck.shouldRecordRiskEvent);
    }

    @Test
    public void inspectVoucherFallsBackToSubjectComparisonWhenRootIdsMissing() {
        VoucherParticipantSnapshot snapshot = snapshot(
                "voucher-2",
                "agent-2",
                "shared-subject",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                null,
                null,
                0,
                null,
                0,
                1);

        SatnetRoleConflictPolicy.ConflictCheck conflictCheck = SatnetRoleConflictPolicy.canReviewVoucher(
                "shared-subject",
                snapshot);

        assertFalse(conflictCheck.allowed);
        assertEquals("SELF_AGENT_VERIFY", conflictCheck.reasonCode);
    }

    @Test
    public void resolveDisputeBlocksPreviousVerifierFromResolvingSameCase() {
        VoucherParticipantSnapshot snapshot = snapshot(
                "voucher-3",
                "agent-3",
                "agent-subject",
                "agent-root",
                null,
                null,
                null,
                "redeemer-subject",
                "redeemer-root",
                "verifier-subject",
                "verifier-root",
                null,
                0,
                0,
                null,
                null,
                0,
                null,
                0,
                1);

        SatnetRoleConflictPolicy.ConflictCheck conflictCheck = SatnetRoleConflictPolicy.authorizeAction(
                "different-subject",
                "verifier-root",
                SatnetRoleConflictPolicy.ACTION_RESOLVE_DISPUTE,
                SatnetRoleManager.ROLE_VERIFIER,
                snapshot);

        assertFalse(conflictCheck.allowed);
        assertEquals("PREVIOUS_VERIFIER_DISPUTE_CONFLICT", conflictCheck.reasonCode);
    }

    @Test
    public void redeemVoucherDeniedWhileDisputeIsOpenAndQuorumPending() {
        VoucherParticipantSnapshot snapshot = snapshot(
                "voucher-4",
                "agent-4",
                "agent-subject",
                "agent-root",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                null,
                null,
                1,
                "dispute-1",
                0,
                2);

        SatnetRoleConflictPolicy.ConflictCheck conflictCheck = SatnetRoleConflictPolicy.authorizeAction(
                "redeemer-subject",
                "redeemer-root",
                SatnetRoleConflictPolicy.ACTION_REDEEM_VOUCHER,
                SatnetRoleManager.ROLE_USER,
                snapshot);

        assertFalse(conflictCheck.allowed);
        assertEquals("RISK_HOLD", conflictCheck.reasonCode);
        assertEquals(SatnetRoleConflictPolicy.RECOMMENDED_RISK_STATE_HOLD, conflictCheck.recommendedRiskState);
    }
}

