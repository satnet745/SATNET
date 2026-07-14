# SATNET GLOBAL Multi-Role Authorization Implementation Guide

**Date:** May 4, 2026  
**Version:** 1.0  
**Status:** Phase 1 Complete, Phase 2-5 Ready for Integration

## Executive Summary

This document outlines the complete implementation of advanced role management features for the SATNET GLOBAL voucher system. The system now supports:

1. **Multi-role participants** - Users can register as User, Agent, Merchant, and/or Verifier
2. **Role-based authorization** - `SatnetRoleManager` enforces capability checks
3. **Conflict-of-interest policies** - `SatnetRoleConflictPolicy` prevents self-interest violations
4. **Combined authorization engine** - `SatnetAuthorizationEngine` integrates both layers
5. **Policy decision persistence** - `VoucherLedger` tracks all authorization decisions with codes/messages

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                   Authorization Decision Flow                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  UI Activity (Verifier/Redemption)                               │
│         ↓                                                         │
│  SatnetAuthorizationEngine.authorize()                            │
│         ├─ Step 1: SatnetRoleManager.authorize()                │
│         │          (Role-based capability check)                │
│         ├─ Step 2: SatnetRoleConflictPolicy.authorizeAction()   │
│         │          (Conflict-of-interest check)                 │
│         └─ Returns: AuthorizationDecision                       │
│             (allowed, reasonCode, message, timestamps)          │
│         ↓                                                         │
│  Decision Handler (Record risk, show badges, enable/disable UI) │
│         ↓                                                         │
│  VoucherLedger.recordPolicyDecision()                            │
│  VoucherLedger.updateVoucherRiskState()                          │
│  VoucherLedger.recordRiskEvent()                                 │
│         ↓                                                         │
│  SQLite Audit Trail (Full traceability)                          │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

## Implementation Phases

### Phase 1: Foundation (COMPLETED ✓)

**Files Created:**
- `SatnetAuthorizationEngine.java` - Combined authorization engine
- `SatnetAuthorizationEngineTest.java` - Comprehensive test suite

**Integration Points:**
- `VerifierDashboardActivity.inspectVoucherPayload()` - Now uses `SatnetAuthorizationEngine`
- `VerifierDashboardActivity.verifySelectedVoucher()` - Now uses `SatnetAuthorizationEngine`
- `VoucherRedemptionActivity.processQRScanResult()` - Now uses `SatnetAuthorizationEngine`
- `VoucherRedemptionActivity.redeemVoucher()` - Now uses `SatnetAuthorizationEngine`

**Key Features:**
- Role-based capability validation (via `SatnetRoleManager`)
- Conflict-of-interest policy enforcement (via `SatnetRoleConflictPolicy`)
- Detailed decision metadata for audit trails
- Risk state recommendation integration
- Timestamp recording for all decisions

### Phase 2: Merchant Settlement Context (READY FOR IMPLEMENTATION)

**Objective:** Wire merchant-linked conflict checks into the first merchant settlement path.

**Files to Modify:**
1. `MerchantLightningActivity` - Add authorization engine checks before payment acceptance
2. `VoucherLedger.recordMerchantSettlementContext()` - Enhance with full decision metadata
3. `VoucherRedemptionActivity` - Display merchant context when linked to voucher

**Implementation Steps:**

**Step 2.1:** Update `MerchantLightningActivity.acceptPayment()`
```java
// Get merchant's role context
SatnetRoleManager.RoleProfile merchantProfile = roleManager.getRoleProfile(SatnetRoleManager.ROLE_MERCHANT);

// Check if can accept payment
SatnetAuthorizationEngine.AuthorizationDecision authDecision = SatnetAuthorizationEngine.authorize(
    roleManager,
    SatnetRoleConflictPolicy.ACTION_ACCEPT_MERCHANT_PAYMENT,
    SatnetRoleManager.ROLE_MERCHANT,
    voucherParticipantSnapshot,
    "Merchant payment acceptance for voucher " + voucherId);

if (!authDecision.allowed) {
    throw new IllegalStateException(authDecision.message);
}

// Record merchant settlement context with decision details
voucherLedger.recordMerchantSettlementContext(
    voucherId,
    roleManager.getParticipantSubjectId(),
    roleManager.getParticipantRootSubjectId(),
    authDecision.reasonCode != null ? "SETTLEMENT_ACCEPTED" : "SETTLEMENT_PENDING",
    authDecision.message);

// If authorization revealed risk, record it
if (authDecision.getRecommendedRiskState() > 0) {
    voucherLedger.updateVoucherRiskState(
        voucherId,
        authDecision.getRecommendedRiskState(),
        0,
        null,
        authDecision.reasonCode,
        authDecision.message);
}
```

**Step 2.2:** Display merchant context in `VoucherRedemptionActivity`

When displaying voucher details, check if merchant is linked and show:
```
Merchant Processing: [Merchant Name]
Settlement Status: [PENDING / ACCEPTED / BLOCKED]
Risk Level: [NONE / MONITOR / HOLD / BLOCK]
Last Decision: [Policy Decision Code] - [Message]
```

**Step 2.3:** Add merchant badge to redemption UI

Update `VoucherRedemptionActivity.applyPolicyBadges()` to include merchant context:
```java
private void applyMerchantBadges(VoucherParticipantSnapshot snapshot) {
    if (merchantBadgeText == null || snapshot == null) {
        return;
    }
    if (snapshot.merchantSubjectId == null || snapshot.merchantSubjectId.isEmpty()) {
        merchantBadgeText.setVisibility(View.GONE);
        return;
    }
    merchantBadgeText.setVisibility(View.VISIBLE);
    merchantBadgeText.setText("Merchant: " + snapshot.merchantSubjectId);
}
```

### Phase 3: Dispute Badge Enhancement (READY FOR IMPLEMENTATION)

**Objective:** Show quorum-pending state explicitly and wire merchant-linked conflict checks into dispute resolution.

**Files to Modify:**
1. `VerifierDashboardActivity` - Enhanced dispute badge with quorum display
2. `VoucherLedger` - Add quorum tracking methods
3. `DisputeResolverActivity` (new or extend existing) - Merchant conflict checks

**Key Changes:**

**Step 3.1:** Update dispute badge rendering

```java
private void renderDisputeBadgeWithQuorum(VoucherParticipantSnapshot snapshot) {
    if (disputeBadgeText == null || snapshot == null) {
        return;
    }
    
    String badgeText;
    if (snapshot.disputeState == VoucherLedger.DISPUTE_STATUS_NONE) {
        badgeText = getString(R.string.satnet_verifier_dispute_badge_none);
    } else if ("QUORUM_PENDING".equalsIgnoreCase(snapshot.decisionCode)) {
        badgeText = getString(R.string.satnet_verifier_dispute_badge_quorum_pending,
            snapshot.achievedQuorum, snapshot.requiredQuorum);
    } else {
        badgeText = getDisputeBadgeLabel(snapshot.disputeState);
    }
    
    bindBadge(disputeBadgeText, badgeText, 
        snapshot.disputeState == VoucherLedger.DISPUTE_STATUS_NONE ||
        snapshot.disputeState == VoucherLedger.DISPUTE_STATUS_RESOLVED);
}
```

**Step 3.2:** Wire merchant conflict checks into dispute resolution

Before resolving dispute:
```java
SatnetAuthorizationEngine.AuthorizationDecision authDecision = SatnetAuthorizationEngine.authorize(
    roleManager,
    SatnetRoleConflictPolicy.ACTION_RESOLVE_DISPUTE,
    SatnetRoleManager.ROLE_VERIFIER,
    participantSnapshot,
    "Dispute resolution for voucher " + voucherId);

if (!authDecision.allowed) {
    voucherLedger.recordRiskEvent(
        voucherId,
        roleManager.getParticipantSubjectId(),
        roleManager.getParticipantRootSubjectId(),
        SatnetRoleManager.ROLE_VERIFIER,
        "dispute_resolution_conflict",
        authDecision.getRecommendedRiskState() * 5,
        authDecision.reasonCode,
        authDecision.message);
    throw new IllegalStateException(authDecision.message);
}
```

### Phase 4: Comprehensive Policy Persistence (READY FOR IMPLEMENTATION)

**Objective:** Ensure all authorization decisions are logged with full traceability.

**Files to Modify:**
1. `VoucherLedger` - Extend with decision persistence methods
2. All activity files - Record all authorization decisions

**New Methods for VoucherLedger:**

```java
public void recordAuthorizationDecision(String voucherId,
        String actorSubjectId,
        String actorRootId,
        int actorRole,
        String actionType,
        SatnetAuthorizationEngine.AuthorizationDecision decision) {
    // 1. Record decision code/message
    updateVoucherPolicyDecision(voucherId,
        decision.reasonCode != null ? decision.reasonCode : "AUTHORIZED",
        decision.message);
    
    // 2. If denied, record risk event
    if (!decision.allowed && decision.shouldRecordRiskEvent()) {
        recordRiskEvent(voucherId,
            actorSubjectId,
            actorRootId,
            actorRole,
            "authorization_denied",
            decision.getRecommendedRiskState() * 3,
            decision.reasonCode,
            decision.message);
    }
    
    // 3. Update risk state if recommended
    if (decision.getRecommendedRiskState() > 0) {
        int currentRiskState = getVoucherRiskState(voucherId);
        if (decision.getRecommendedRiskState() > currentRiskState) {
            updateVoucherRiskState(voucherId,
                decision.getRecommendedRiskState(),
                0, null, decision.reasonCode, decision.message);
        }
    }
}

public void recordQuorumProgress(String voucherId,
        int achievedQuorum,
        int requiredQuorum,
        String decisionCode,
        String decisionMessage) {
    // Persist quorum state
    markSettlementVerified(voucherId, null, null, achievedQuorum, requiredQuorum);
    updateVoucherPolicyDecision(voucherId, decisionCode, decisionMessage);
}
```

### Phase 5: Complete UI Integration (READY FOR IMPLEMENTATION)

**Objective:** Show all authorization decisions, merchant context, and risk/dispute badges in UI.

**Files to Update:**
1. All Activity files - Call `recordAuthorizationDecision()` after authorization checks
2. `VerifierDashboardActivity` - Already integrated (Step 1)
3. `VoucherRedemptionActivity` - Already integrated (Step 1)
4. `MerchantLightningActivity` - Add merchant badges and conflict displays

**Example for Verifier Dashboard:**

```java
private void verifySelectedVoucher() {
    // ... existing code ...
    
    // Perform authorization
    SatnetAuthorizationEngine.AuthorizationDecision authDecision = 
        SatnetAuthorizationEngine.authorize(...);
    
    // Record decision to audit trail
    voucherLedger.recordAuthorizationDecision(
        voucherId,
        roleManager.getParticipantSubjectId(),
        roleManager.getParticipantRootSubjectId(),
        SatnetRoleManager.ROLE_VERIFIER,
        "VERIFY_SETTLEMENT",
        authDecision);
    
    // Check if allowed
    if (!authDecision.allowed) {
        // Show why it was denied
        Toast.makeText(this, authDecision.message, Toast.LENGTH_LONG).show();
        return;
    }
    
    // Proceed with verification
    voucherLedger.markSettlementVerified(voucherId, ...);
}
```

## Data Model Extensions

### VoucherParticipantSnapshot Updates

Current fields and their use:
- `merchantSubjectId`, `merchantParticipantRootId` - Merchant context
- `decisionCode`, `decisionMessage`, `decisionAt` - Latest policy decision
- `achievedQuorum`, `requiredQuorum` - Quorum tracking
- `riskState`, `riskScore`, `riskFlagsJson` - Risk assessment
- `disputeStatus`, `currentDisputeId` - Dispute tracking

### Database Schema (VoucherLedger)

Relevant columns in `TABLE_VOUCHERS`:
```sql
last_policy_decision_code TEXT
last_policy_decision_message TEXT
last_policy_decision_at INTEGER
merchant_subject_id TEXT
merchant_participant_root_id TEXT
risk_state INTEGER
risk_score INTEGER
dispute_status INTEGER
achieved_verifier_quorum INTEGER
required_verifier_quorum INTEGER
```

New risk event table `TABLE_VOUCHER_RISK_EVENTS`:
```sql
event_id TEXT PRIMARY KEY
voucher_id TEXT
subject_id TEXT
participant_root_id TEXT
actor_role INTEGER
event_type TEXT
risk_score_delta INTEGER
rule_code TEXT
event_message TEXT
created_at INTEGER
origin TEXT ('local' or 'mesh')
exported_to_mesh INTEGER
```

## Testing Strategy

### Unit Tests (Complete)
- `SatnetAuthorizationEngineTest` - Authorization engine logic
- Test verifier can inspect non-owned vouchers ✓
- Test verifier cannot inspect self-owned vouchers ✓
- Test capability-less users cannot inspect ✓
- Test user can redeem unconnected vouchers ✓
- Test decision timestamp recording ✓

### Integration Tests (To Implement)

**Merchant Settlement Flow:**
```
1. Agent issues voucher with direction=SELL
2. Merchant scans and accepts payment
3. Verify: merchant context recorded, conflict check passed
4. Verify: decision code persisted
5. Verify: risk state updated if conflicted
```

**Dispute Resolution Flow:**
```
1. User opens dispute on voucher
2. Verifier A inspects (OK - different person)
3. Verifier B attempts resolution (conflict check)
4. Verify: decision recorded, risk escalated if denied
5. Verify: quorum state persisted
```

**Complete Redemption Flow:**
```
1. User scans voucher (authorization checked)
2. Display shows risk/dispute badges
3. Display shows merchant context
4. User redeems (final authorization check)
5. Verify: all decisions persisted to audit trail
```

## Backward Compatibility

✓ All changes are **backward compatible**:
- `SatnetRoleManager` unchanged (extended via engine)
- `SatnetRoleConflictPolicy` unchanged (extended via engine)
- `VoucherLedger` extended with new methods (old methods still work)
- `AuthorizationResult` and `ConflictCheck` unchanged
- Existing activities still work, now enhanced with authorization engine

## Security Considerations

1. **Role Isolation:** Each role has its own subject ID to prevent cross-role self-dealings
2. **Quorum Requirements:** Settlement verification requires configurable verifier quorum
3. **Conflict Detection:** Multi-role participants cannot review their own transactions
4. **Audit Trail:** All authorization decisions logged with timestamps, codes, messages
5. **Risk Scoring:** Failed authorizations increment risk scores for participants
6. **Merchant Linking:** Merchant settlement context prevents double-binding

## Performance Considerations

- **Authorization Engine:** O(1) role check + O(n) participant field comparisons
- **Database:** Existing indexes on `voucher_id`, `created_at` optimize queries
- **UI Rendering:** Badge updates use cached decision data, no extra queries
- **Quorum Tracking:** Incremental updates, no recalculation on each verification

## Migration Path

1. **Phase 1 (ACTIVE):** Deploy `SatnetAuthorizationEngine`
   - No database changes required
   - Activities updated to use engine
   - Tests validate functionality

2. **Phase 2-3 (PLANNED):** Merchant and dispute enhancements
   - Existing merchant settlement flow enhanced
   - No breaking changes
   - Gradual rollout per region

3. **Phase 4-5 (PLANNED):** Complete policy persistence and UI
   - All activities updated
   - Comprehensive audit trails
   - Regional verifier networks enabled

## Troubleshooting

### Authorization Denied for Valid Verifier

**Symptoms:** Verifier cannot inspect vouchers even though role is registered

**Root Causes:**
1. Verifier role suspended → Check `roleStatus` and `suspensionReason`
2. Self-conflict detected → Verify participant IDs don't match
3. Policy level conflict → Check conflict check reason code

**Debug Steps:**
```java
// Check role capability
AuthorizationResult roleAuth = roleManager.authorize(SatnetRoleManager.CAP_VERIFIER_INSPECT);
Log.d(TAG, "Role auth: " + roleAuth.allowed + " - " + roleAuth.reasonCode);

// Check conflict policy
ConflictCheck conflictCheck = SatnetRoleConflictPolicy.authorizeAction(
    roleManager, ACTION_INSPECT_VOUCHER, ROLE_VERIFIER, snapshot);
Log.d(TAG, "Conflict check: " + conflictCheck.allowed + " - " + conflictCheck.reasonCode);

// Check combined decision
AuthorizationDecision decision = SatnetAuthorizationEngine.authorize(...);
Log.d(TAG, "Combined: " + decision.allowed + " - " + decision.reasonCode);
```

### Quorum State Not Persisting

**Symptoms:** Quorum progress shows incorrect values on refresh

**Root Causes:**
1. `markSettlementVerified()` not called with correct quorum values
2. Database query cached old value
3. Wrong voucher ID in snapshot retrieval

**Fix:**
```java
// Ensure correct order
voucherLedger.markSettlementVerified(voucherId, verifierId, verifierRootId, 
    achievedQuorum, requiredQuorum);  // Updates quorum + state

// Then refresh snapshot
VoucherParticipantSnapshot fresh = voucherLedger.getVoucherParticipantSnapshot(voucherId);
applyPolicyBadges(fresh);  // Shows updated quorum
```

## Future Enhancements

1. **Quorum Presets:** Configurable quorum requirements by voucher type/amount
2. **Risk Escalation:** Automatic escalation to higher verifier levels based on risk scores
3. **Merchant Networks:** Region-specific merchant verifier pools
4. **Redemption Limits:** Time-based or amount-based throttling per verifier
5. **Cross-Device Sync:** Authorize decisions replicated across verifier mesh
6. **Machine Learning:** Risk scoring based on historical patterns

---

**End of Implementation Guide**

For questions or clarifications, refer to code comments in:
- `SatnetAuthorizationEngine.java`
- `SatnetAuthorizationEngineTest.java`
- Activity integration points

