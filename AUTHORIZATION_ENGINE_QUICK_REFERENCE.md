# SatnetAuthorizationEngine - Quick Reference Card

**Print this for quick reference during development**

---

## Import Statement

```java
import org.servalproject.satnet.SatnetAuthorizationEngine;
import org.servalproject.satnet.SatnetRoleConflictPolicy;
import org.servalproject.satnet.SatnetRoleManager;
```

---

## Basic Usage Pattern

```java
// 1. Get the voucher's participant data
VoucherParticipantSnapshot snapshot = 
    voucherLedger.getVoucherParticipantSnapshot(voucherId);

// 2. Authorize the action
SatnetAuthorizationEngine.AuthorizationDecision decision = 
    SatnetAuthorizationEngine.authorize(
        roleManager,                                    // Your role manager
        SatnetRoleConflictPolicy.ACTION_INSPECT_VOUCHER,  // What user is doing
        SatnetRoleManager.ROLE_VERIFIER,               // What role they're using
        snapshot,                                      // Voucher context
        "Reason for this authorization");              // For logging

// 3. Check the result
if (!decision.allowed) {
    Toast.makeText(this, decision.message, Toast.LENGTH_LONG).show();
    return;
}

// 4. Proceed with action
performYourAction();
```

---

## Action Types (use with SatnetRoleConflictPolicy)

| Action | Code | Role | Use Case |
|--------|------|------|----------|
| ISSUE_VOUCHER | 1 | AGENT | Agent creating new voucher |
| REDEEM_VOUCHER | 2 | USER | User redeeming voucher |
| ACCEPT_MERCHANT_PAYMENT | 3 | MERCHANT | Merchant accepting payment |
| INSPECT_VOUCHER | 4 | VERIFIER | Verifier reviewing voucher |
| VERIFY_SETTLEMENT | 5 | VERIFIER | Verifier approving settlement |
| RESOLVE_DISPUTE | 6 | VERIFIER | Verifier resolving dispute |

---

## Common Reason Codes

| Code | Meaning | Next Step |
|------|---------|-----------|
| SELF_AGENT_VERIFY | User is their own agent | Use different verifier |
| SELF_MERCHANT_VERIFY | User is their own merchant | Use different verifier |
| CONFLICT_PREVIOUS_REVIEWER | Already reviewed by this user | Use different verifier |
| ROLE_NOT_REGISTERED | User doesn't have role | Register role first |
| ROLE_SUSPENDED | User's role suspended | Admin review needed |
| CAPABILITY_NOT_GRANTED | Role can't do this action | Use different role |

---

## When Authorization Fails

```java
if (!decision.allowed) {
    // Show user-friendly error
    Toast.makeText(this, decision.message, Toast.LENGTH_LONG).show();
    
    // Log for debugging
    Log.w(TAG, "Denied: " + decision.reasonCode);
    
    // Optional: Record risk event
    if (decision.shouldRecordRiskEvent()) {
        voucherLedger.recordRiskEvent(voucherId,
            roleManager.getParticipantSubjectId(),
            roleManager.getParticipantRootSubjectId(),
            role, "authorization_denied",
            decision.getRecommendedRiskState() * 3,
            decision.reasonCode,
            decision.message);
    }
    
    return;  // Stop processing
}
```

---

## Authorization Decision Object

```
AuthorizationDecision
├─ allowed: boolean               // TRUE = proceed, FALSE = deny
├─ reasonCode: String             // Machine-readable code (for logging)
├─ message: String                // User-friendly message (for UI)
├─ roleAuthorization: Result      // Details from role layer
├─ conflictCheck: ConflictCheck   // Details from policy layer
└─ decidedAt: long                // Timestamp when decided
```

**Methods:**
- `getRecommendedRiskState()` → Risk level (0, 1, 2, 3)
- `shouldRecordRiskEvent()` → TRUE if should log risk event

---

## Role Hierarchy

```
ROLE_NONE (0)          [Nobody]
├─ ROLE_USER (1)       [Basic wallet user]
├─ ROLE_AGENT (2)      [Issues vouchers]
├─ ROLE_MERCHANT (4)   [Accepts payments]
└─ ROLE_VERIFIER (8)   [Verifies settlements]
```

---

## Capability Checks (SatnetRoleManager)

| Capability | Constant | Roles |
|------------|----------|-------|
| View wallet | CAP_WALLET_VIEW | All |
| Backup wallet | CAP_WALLET_BACKUP | All |
| Redeem vouchers | CAP_VOUCHER_REDEEM | User, Agent, Merchant |
| Issue vouchers | CAP_VOUCHER_ISSUE | Agent |
| Accept Lightning | CAP_MERCHANT_ACCEPT_LIGHTNING | Merchant |
| Inspect vouchers | CAP_VERIFIER_INSPECT | Verifier |
| Verify settlement | CAP_VERIFIER_APPROVE_SETTLEMENT | Verifier |
| Resolve disputes | CAP_VERIFIER_RESOLVE_DISPUTE | Verifier |

---

## Conflict Detection Rules

✓ **Allowed:**
- Verifier A verifies voucher issued by Agent B (different people)
- Merchant A accepts payment on voucher from Agent B (different people)
- User redeems voucher with no conflict

✗ **Denied:**
- Verifier A verifies own issued voucher (self-conflict)
- Verifier A verifies voucher they already verified (duplicate)
- Verifier is also the original issuer (multi-role conflict)
- Merchant is also the issuer (multi-role conflict)

---

## Decision Recording

```java
// Record authorized decision
voucherLedger.updateVoucherPolicyDecision(
    voucherId,
    "AUTHORIZED",
    "User authorized to perform action");

// Record denied decision + risk
if (!decision.allowed) {
    voucherLedger.recordRiskEvent(
        voucherId,
        actorId, actorRoot, role,
        "authorization_denied",
        10,  // Risk score
        decision.reasonCode,
        decision.message);
}
```

---

## Integration Checklist

Before deploying authorization checks to new activity:

- [ ] Import SatnetAuthorizationEngine
- [ ] Get VoucherParticipantSnapshot before checking
- [ ] Call `SatnetAuthorizationEngine.authorize()`
- [ ] Check `decision.allowed`
- [ ] Show `decision.message` to user on denial
- [ ] Record risk event if `decision.shouldRecordRiskEvent()`
- [ ] Update risk state if `decision.getRecommendedRiskState() > 0`
- [ ] Log all decisions for audit trail

---

## Testing Checklist

Before submitting code:

- [ ] Test with authorized user → should allow
- [ ] Test with unauthorized user → should deny
- [ ] Test with self-conflict → should deny
- [ ] Test with no role registered → should deny
- [ ] Test with suspended role → should deny
- [ ] Test error message clarity
- [ ] Test risk event recording
- [ ] Check logs for decision details

---

## Common Mistakes to Avoid

❌ **Wrong:**
```java
// Forgetting to check decision
SatnetAuthorizationEngine.authorize(roleManager, ...);
performAction();  // Crashes if denied!
```

✅ **Right:**
```java
SatnetAuthorizationEngine.AuthorizationDecision decision = 
    SatnetAuthorizationEngine.authorize(roleManager, ...);
if (!decision.allowed) return;
performAction();
```

---

❌ **Wrong:**
```java
// Using old ConflictCheck directly
ConflictCheck check = SatnetRoleConflictPolicy.authorizeAction(...);
// Misses role layer checks!
```

✅ **Right:**
```java
// Use authorization engine (checks both layers)
AuthorizationDecision decision = 
    SatnetAuthorizationEngine.authorize(...);
// Has both role and conflict details
```

---

❌ **Wrong:**
```java
// Swallowing errors
try {
    authorize();
    performAction();
} catch (Exception e) {
    // Silently ignore
}
```

✅ **Right:**
```java
AuthorizationDecision decision = authorize();
if (!decision.allowed) {
    Log.w(TAG, "Denied: " + decision.reasonCode);
    Toast.makeText(this, decision.message, Toast.LENGTH_LONG).show();
    return;
}
performAction();
```

---

## Getting Help

**Documentation:**
- Full API: `SATHNET_AUTHORIZATION_ENGINE_API.md`
- Implementation Guide: `AUTHORIZATION_ENGINE_IMPLEMENTATION_GUIDE.md`
- Test Examples: `SatnetAuthorizationEngineTest.java`

**Code Examples:**
- Verifier: `VerifierDashboardActivity.java`
- Redemption: `VoucherRedemptionActivity.java`

**Questions?**
- Check test cases first
- Trace through with debugger
- Verify role registration
- Check participant snapshot data

---

**Last Updated:** May 4, 2026  
**Version:** 1.0

