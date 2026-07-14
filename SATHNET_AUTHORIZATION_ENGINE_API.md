# SatnetAuthorizationEngine API Specification

**Version:** 1.0  
**Status:** Production Ready  
**Last Updated:** May 4, 2026

## Quick Reference

### Primary Method

```java
public static AuthorizationDecision authorize(
    SatnetRoleManager roleManager,
    int actionType,
    int actorRole,
    VoucherParticipantSnapshot participantSnapshot,
    String reasonContext)
```

**Returns:** `AuthorizationDecision` with full authorization metadata

**Throws:** None (all errors encapsulated in decision object)

---

## Detailed API Reference

### SatnetAuthorizationEngine Class

#### Method: `authorize()`

Performs combined authorization by checking both role-based capabilities and conflict-of-interest policies.

**Parameters:**
- `SatnetRoleManager roleManager` - The role manager instance (typically from activity)
  - Cannot be null - will return DENY with "NULL_ROLE_MANAGER"
  - Must have appropriate role registered for the action
  
- `int actionType` - The action being authorized
  - Supported actions:
    - `SatnetRoleConflictPolicy.ACTION_ISSUE_VOUCHER` (1)
    - `SatnetRoleConflictPolicy.ACTION_REDEEM_VOUCHER` (2)
    - `SatnetRoleConflictPolicy.ACTION_ACCEPT_MERCHANT_PAYMENT` (3)
    - `SatnetRoleConflictPolicy.ACTION_INSPECT_VOUCHER` (4)
    - `SatnetRoleConflictPolicy.ACTION_VERIFY_SETTLEMENT` (5)
    - `SatnetRoleConflictPolicy.ACTION_RESOLVE_DISPUTE` (6)

- `int actorRole` - The role attempting the action
  - Supported roles:
    - `SatnetRoleManager.ROLE_NONE` (0)
    - `SatnetRoleManager.ROLE_USER` (1)
    - `SatnetRoleManager.ROLE_AGENT` (2)
    - `SatnetRoleManager.ROLE_MERCHANT` (4)
    - `SatnetRoleManager.ROLE_VERIFIER` (8)

- `VoucherParticipantSnapshot participantSnapshot` - Voucher participant data
  - Can be null - will result in ALLOW (no conflict to check)
  - Should contain all relevant participant IDs and states
  - Used for conflict-of-interest detection

- `String reasonContext` - Human-readable context for authorization
  - Optional (can be null)
  - Included in logs and error messages
  - Examples: "Voucher inspection", "Payment acceptance", "Settlement verification"

**Returns:** `AuthorizationDecision` object with:
- `boolean allowed` - TRUE if action is authorized, FALSE otherwise
- `String reasonCode` - Machine-readable code for why action was allowed/denied
- `String message` - Human-readable message for UI display
- `AuthorizationResult roleAuthorization` - Role layer result details
- `ConflictCheck conflictCheck` - Policy layer result details
- `long decidedAt` - Timestamp when decision was made

**Example Usage:**

```java
// Verifier tries to inspect voucher
VoucherParticipantSnapshot snapshot = 
    voucherLedger.getVoucherParticipantSnapshot(voucherId);

SatnetAuthorizationEngine.AuthorizationDecision decision = 
    SatnetAuthorizationEngine.authorize(
        roleManager,                                    // From activity
        SatnetRoleConflictPolicy.ACTION_INSPECT_VOUCHER,  // What they're doing
        SatnetRoleManager.ROLE_VERIFIER,               // Their role
        snapshot,                                      // Voucher context
        "Inspecting voucher " + voucherId);            // Debug context

if (!decision.allowed) {
    Log.w(TAG, "Authorization denied: " + decision.reasonCode);
    Toast.makeText(this, decision.message, Toast.LENGTH_LONG).show();
    return;
}

// Proceed with action
performInspection(voucherId);
```

---

## AuthorizationDecision Class

### Fields

```java
public final boolean allowed;                              // Overall result
public final String reasonCode;                            // Machine-readable code
public final String message;                               // User-facing message
public final SatnetRoleManager.AuthorizationResult 
    roleAuthorization;                                     // Role check details
public final SatnetRoleConflictPolicy.ConflictCheck 
    conflictCheck;                                         // Policy check details
public final long decidedAt;                               // Decision timestamp
```

### Methods

#### `getRecommendedRiskState()`

```java
public int getRecommendedRiskState()
```

Returns the recommended risk state elevation based on the authorization result.

**Returns:** Risk state value
- `0` - No risk state change recommended
- `SatnetRoleConflictPolicy.RECOMMENDED_RISK_STATE_MONITOR` (1) - Monitor
- `SatnetRoleConflictPolicy.RECOMMENDED_RISK_STATE_HOLD` (2) - Hold
- Other values as defined in policy

**Usage:** After authorization denial, use this to update voucher risk state:

```java
if (!decision.allowed) {
    voucherLedger.updateVoucherRiskState(
        voucherId,
        decision.getRecommendedRiskState(),
        0, null, decision.reasonCode, decision.message);
}
```

#### `shouldRecordRiskEvent()`

```java
public boolean shouldRecordRiskEvent()
```

Returns TRUE if this authorization denial should be recorded as a risk event.

**Returns:** TRUE if risk event should be recorded, FALSE otherwise

**Usage:** Record risky behavior in audit trail:

```java
if (decision.shouldRecordRiskEvent()) {
    voucherLedger.recordRiskEvent(
        voucherId,
        roleManager.getParticipantSubjectId(),
        roleManager.getParticipantRootSubjectId(),
        actorRole,
        "authorization_denied",
        10,  // Risk score delta
        decision.reasonCode,
        decision.message);
}
```

---

## Authorization Reason Codes

Codes are returned in `AuthorizationDecision.reasonCode` for machine-readable handling.

### Role Layer Codes (from SatnetRoleManager)

| Code | Meaning | Action |
|------|---------|--------|
| `POLICY_VIOLATION` | Build/security policy violated | Fail immediately |
| `ROLE_NOT_REGISTERED` | Role not registered on device | User must register |
| `CAPABILITY_NOT_GRANTED` | Role doesn't have capability | User must switch roles |
| `FEATURE_DISABLED` | Feature disabled in build | Admin config issue |
| `ROLE_SUSPENDED` | Role suspended | Check `suspensionReason` |
| `ROLE_REVIEW_REQUIRED` | Role pending review | Wait for completion |

### Policy Layer Codes (from SatnetRoleConflictPolicy)

| Code | Meaning | Risk Recommendation |
|------|---------|-------------------|
| `SELF_AGENT_VERIFY` | Verifier is own agent | HOLD |
| `SELF_REDEEM_VERIFY` | Verifier is own redeemer | HOLD |
| `SELF_MERCHANT_VERIFY` | Verifier is own merchant | HOLD |
| `CONFLICT_PREVIOUS_REVIEWER` | Already reviewed by this verifier | MONITOR |
| `DISPUTE_ALREADY_OWNED` | Already owned by this resolver | MONITOR |
| `PREVIOUS_VERIFIER_DISPUTE_CONFLICT` | Original verifier cannot resolve | HOLD |
| `RISK_HOLD` | Voucher under dispute, cannot redeem | HOLD |

### Custom Codes

Applications can define their own codes for business logic:

```java
// Example: Merchant network rule
String reasonCode = "MERCHANT_REGION_MISMATCH";
String message = "Merchant not registered for this region";
decision = AuthorizationDecision.deny(reasonCode, message, roleAuth, conflictCheck);
```

---

## Integration Patterns

### Pattern 1: Simple Allow/Deny Check

```java
SatnetAuthorizationEngine.AuthorizationDecision decision =
    SatnetAuthorizationEngine.authorize(roleManager, action, role, snapshot, context);

if (!decision.allowed) {
    Toast.makeText(this, decision.message, Toast.LENGTH_LONG).show();
    return;
}

performAction();
```

### Pattern 2: With Risk Recording

```java
SatnetAuthorizationEngine.AuthorizationDecision decision =
    SatnetAuthorizationEngine.authorize(roleManager, action, role, snapshot, context);

// Record decision for audit
if (decision.shouldRecordRiskEvent()) {
    voucherLedger.recordRiskEvent(
        voucherId,
        roleManager.getParticipantSubjectId(),
        roleManager.getParticipantRootSubjectId(),
        role,
        actionName,
        decision.getRecommendedRiskState() * 5,
        decision.reasonCode,
        decision.message);
}

if (!decision.allowed) {
    throw new IllegalStateException(decision.message);
}

performAction();
```

### Pattern 3: With Role Fallback

```java
SatnetAuthorizationEngine.AuthorizationDecision verifierDecision =
    SatnetAuthorizationEngine.authorize(
        roleManager, ACTION_INSPECT_VOUCHER, ROLE_VERIFIER, snapshot, context);

// Try as different role if needed
if (!verifierDecision.allowed && verifierDecision.roleAuthorization == null) {
    SatnetAuthorizationEngine.AuthorizationDecision adminDecision =
        SatnetAuthorizationEngine.authorize(
            roleManager, ACTION_INSPECT_VOUCHER, ROLE_ADMIN, snapshot, context);
    
    if (adminDecision.allowed) {
        performAction();
        return;
    }
}

if (!verifierDecision.allowed) {
    throw new IllegalStateException(verifierDecision.message);
}

performAction();
```

### Pattern 4: With UI State Updates

```java
// Check authorization
SatnetAuthorizationEngine.AuthorizationDecision decision =
    SatnetAuthorizationEngine.authorize(roleManager, action, role, snapshot, context);

// Update UI based on decision
boolean isAllowed = decision.allowed;
boolean showWarning = !decision.allowed && decision.conflictCheck != null;
int riskLevel = decision.getRecommendedRiskState();

// Disable UI elements if not allowed
actionButton.setEnabled(isAllowed);
actionButton.setAlpha(isAllowed ? 1.0f : 0.5f);

// Show warning badges if conflict detected
if (showWarning) {
    conflictBadge.setVisibility(View.VISIBLE);
    conflictBadge.setText(decision.reasonCode);
}

// Color code by risk
if (riskLevel > 0) {
    riskIndicator.setBackgroundColor(getRiskColor(riskLevel));
}
```

---

## Testing Examples

### Unit Test: Allow Case

```java
@Test
public void verifierCanInspectVoucherFromDifferentAgent() {
    // Setup: Different participants
    SatnetRoleManager verifier = createVerifierRole();
    VoucherParticipantSnapshot snapshot = createSnapshot(
        agentId: "agent_alice",
        agentRoot: "participant_alice");
    
    // Act
    SatnetAuthorizationEngine.AuthorizationDecision decision =
        SatnetAuthorizationEngine.authorize(
            verifier,
            ACTION_INSPECT_VOUCHER,
            ROLE_VERIFIER,
            snapshot,
            "Test inspection");
    
    // Assert
    assertTrue(decision.allowed);
    assertNull(decision.reasonCode);
}
```

### Unit Test: Deny Case

```java
@Test
public void verifierCannotInspectOwnIssuedVoucher() {
    // Setup: Same participant
    SatnetRoleManager agent = createAgentRole();
    agent.registerAsVerifier();
    String myRoot = agent.getParticipantRootSubjectId();
    
    VoucherParticipantSnapshot snapshot = createSnapshot(
        agentRoot: myRoot,
        verifierRoot: myRoot);  // Same person
    
    // Act
    SatnetAuthorizationEngine.AuthorizationDecision decision =
        SatnetAuthorizationEngine.authorize(
            agent,
            ACTION_INSPECT_VOUCHER,
            ROLE_VERIFIER,
            snapshot,
            "Self-inspection");
    
    // Assert
    assertFalse(decision.allowed);
    assertEquals("SELF_AGENT_VERIFY", decision.reasonCode);
    assertNotNull(decision.conflictCheck);
    assertFalse(decision.conflictCheck.allowed);
}
```

---

## Error Handling

### Null Handling

| Parameter | Null Behavior |
|-----------|---------------|
| `roleManager` | Returns DENY("NULL_ROLE_MANAGER") |
| `participantSnapshot` | Returns ALLOW (no conflict to check) |
| `reasonContext` | Ignored (optional for logging) |
| `decisionCode` | Uses default based on decision |
| `message` | Uses generic message |

### Exception Handling

The authorization engine **never throws exceptions**. All errors are encapsulated:

```java
// Wrong way - can throw NullPointerException
if (roleManager == null) {
    // crash!
}

// Right way - authorization engine handles it
SatnetAuthorizationEngine.AuthorizationDecision decision =
    SatnetAuthorizationEngine.authorize(roleManager, ...);
if (!decision.allowed) {
    Log.w(TAG, "Authorization failed: " + decision.reasonCode);
}
```

---

## Logging Best Practices

### Recommended Logging Levels

```java
SatnetAuthorizationEngine.AuthorizationDecision decision = 
    SatnetAuthorizationEngine.authorize(...);

// Log denials
if (!decision.allowed) {
    Log.w(TAG, "Authorization denied for " + actionName 
        + ": " + decision.reasonCode 
        + " - " + decision.message);
}

// Log role failures
if (decision.roleAuthorization != null && !decision.roleAuthorization.allowed) {
    Log.d(TAG, "Role check failed: " + decision.roleAuthorization.reasonCode);
}

// Log conflict detections
if (decision.conflictCheck != null && !decision.conflictCheck.allowed) {
    Log.i(TAG, "Conflict detected: " + decision.conflictCheck.reasonCode);
}

// Log successes (debug only)
if (decision.allowed) {
    Log.d(TAG, "Authorization granted for " + actionName);
}
```

### Audit Logging

```java
// Record all denials to audit
if (!decision.allowed) {
    voucherLedger.recordAuthorizationDecision(
        voucherId,
        roleManager.getParticipantSubjectId(),
        roleManager.getParticipantRootSubjectId(),
        role,
        actionName,
        decision);  // Entire decision stored
}
```

---

## Performance Characteristics

- **Time Complexity:** O(1) role check + O(n) participant comparisons = O(n)
  - n = number of participant IDs (typically 6-8)
  
- **Space Complexity:** O(1)
  - Fixed-size decision object, no collections

- **Typical Execution Time:** 1-2 milliseconds

- **Bottleneck:** Database read of VoucherParticipantSnapshot (~5-10ms)
  - Authorization engine decision is negligible

---

## Future Enhancements

### v1.1 Planned
- Cache authorization results per voucher
- Batch authorization for multiple vouchers
- Custom policy injection mechanism

### v2.0 Planned
- Cross-device authorization sync
- Machine learning risk scoring
- Regional verifier network checks

---

**End of API Specification**

For implementation examples, see:
- `VerifierDashboardActivity.java` - Verifier integration
- `VoucherRedemptionActivity.java` - Redemption integration
- `SatnetAuthorizationEngineTest.java` - Unit tests

