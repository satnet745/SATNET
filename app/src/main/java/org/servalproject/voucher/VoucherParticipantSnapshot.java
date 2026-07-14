package org.servalproject.voucher;

public final class VoucherParticipantSnapshot {
    public final String voucherId;
    public final String agentId;
    public final String agentSubjectId;
    public final String agentParticipantRootId;
    public final String merchantSubjectId;
    public final String merchantParticipantRootId;
    public final String redeemedByWallet;
    public final String redeemerSubjectId;
    public final String redeemerParticipantRootId;
    public final String settlementVerifierSubjectId;
    public final String settlementVerifierParticipantRootId;
    public final String disputeResolverSubjectId;
    public final String disputeResolverParticipantRootId;
    public final int riskState;
    public final int riskScore;
    public final String riskFlagsJson;
    public final String decisionCode;
    public final String decisionMessage;
    public final long decisionAt;
    public final int disputeState;
    public final String disputeId;
    public final int achievedQuorum;
    public final int requiredQuorum;

    public VoucherParticipantSnapshot(String voucherId,
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
            String disputeResolverParticipantRootId,
            int riskState,
            int riskScore,
            String riskFlagsJson,
            String decisionCode,
            String decisionMessage,
            long decisionAt,
            int disputeState,
            String disputeId,
            int achievedQuorum,
            int requiredQuorum) {
        this.voucherId = voucherId;
        this.agentId = agentId;
        this.agentSubjectId = agentSubjectId;
        this.agentParticipantRootId = agentParticipantRootId;
        this.merchantSubjectId = merchantSubjectId;
        this.merchantParticipantRootId = merchantParticipantRootId;
        this.redeemedByWallet = redeemedByWallet;
        this.redeemerSubjectId = redeemerSubjectId;
        this.redeemerParticipantRootId = redeemerParticipantRootId;
        this.settlementVerifierSubjectId = settlementVerifierSubjectId;
        this.settlementVerifierParticipantRootId = settlementVerifierParticipantRootId;
        this.disputeResolverSubjectId = disputeResolverSubjectId;
        this.disputeResolverParticipantRootId = disputeResolverParticipantRootId;
        this.riskState = riskState;
        this.riskScore = riskScore;
        this.riskFlagsJson = riskFlagsJson;
        this.decisionCode = decisionCode;
        this.decisionMessage = decisionMessage;
        this.decisionAt = decisionAt;
        this.disputeState = disputeState;
        this.disputeId = disputeId;
        this.achievedQuorum = achievedQuorum;
        this.requiredQuorum = requiredQuorum;
    }

    public VoucherParticipantSnapshot(String voucherId,
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
            String decisionMessage,
            long decisionAt,
            int disputeState,
            String disputeId,
            int achievedQuorum,
            int requiredQuorum) {
        this(voucherId,
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
                decisionMessage,
                decisionAt,
                disputeState,
                disputeId,
                achievedQuorum,
                requiredQuorum);
    }

    public VoucherLedger.MerchantSettlementContext getMerchantSettlementContext() {
        if (merchantSubjectId == null || merchantSubjectId.trim().isEmpty()) {
            return null;
        }
        return new VoucherLedger.MerchantSettlementContext(
                merchantSubjectId,
                merchantParticipantRootId,
                decisionCode,
                decisionMessage,
                decisionAt);
    }
}
