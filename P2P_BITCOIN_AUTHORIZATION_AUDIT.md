# P2P Bitcoin Send/Receive Authorization Audit Report

**Date:** May 11, 2026  
**Status:** ⚠️ CRITICAL ISSUE FOUND  
**Severity:** HIGH

---

## Executive Summary

P2P Bitcoin send and receive activities are **NOT protected by role-based authorization controls**. While the Bitcoin transfer infrastructure has been implemented (EsploraApiClient, BitcoinTransactionBuilder, SendBitcoinActivity), there are **no capability gates, role checks, or authorization enforcement** at any level of the call stack.

### Key Issues

| Issue | Location | Status |
|-------|----------|--------|
| No send/receive capabilities defined | `SatnetRoleManager.java` | ❌ MISSING |
| No authorization checks in wallet methods | `BitcoinWallet.java` | ❌ NONE |
| No authorization checks in UI layer | `SendBitcoinActivity.java` | ❌ NONE |
| No role-to-capability mapping | `SatnetRoleManager.getCapabilityMaskForRole()` | ❌ INCOMPLETE |
| No limits enforcement (daily/monthly) | `SendBitcoinActivity.java` | ❌ MISSING |
| No risk tier checks | `SendBitcoinActivity.java` | ❌ MISSING |
| No suspension status checks | `SendBitcoinActivity.java` | ❌ MISSING |

---

## Detailed Analysis

### 1. Missing Capability Definitions

**File:** `SatnetRoleManager.java` (Lines 81-91)

**Current Defined Capabilities:**
```java
public static final int CAP_WALLET_VIEW = 1 << 0;           // ✓ Exists
public static final int CAP_WALLET_BACKUP = 1 << 1;         // ✓ Exists
public static final int CAP_VOUCHER_REDEEM = 1 << 2;        // ✓ Exists
public static final int CAP_VOUCHER_ISSUE = 1 << 3;         // ✓ Exists
public static final int CAP_MERCHANT_ACCEPT_LIGHTNING = 1 << 4; // ✓ Exists
public static final int CAP_MERCHANT_SETTLEMENT_VIEW = 1 << 5;  // ✓ Exists
public static final int CAP_VERIFIER_INSPECT = 1 << 6;      // ✓ Exists
public static final int CAP_VERIFIER_APPROVE_SETTLEMENT = 1 << 7; // ✓ Exists
public static final int CAP_VERIFIER_RESOLVE_DISPUTE = 1 << 8; // ✓ Exists
public static final int CAP_ROLE_MANAGE = 1 << 9;           // ✓ Exists
public static final int CAP_RISK_REVIEW_LOCAL = 1 << 10;    // ✓ Exists
```

**Missing Capabilities:**
```java
public static final int CAP_BITCOIN_SEND = 1 << 11;         // ❌ MISSING
public static final int CAP_BITCOIN_RECEIVE = 1 << 12;      // ❌ MISSING or implicit
```

### 2. Role Capability Mapping is Incomplete

**File:** `SatnetRoleManager.java` (Lines 696-715)

**Current Mapping:**
```java
private int getCapabilityMaskForRole(int role) {
    switch (role) {
        case ROLE_USER:
            return CAP_WALLET_VIEW | CAP_WALLET_BACKUP | CAP_VOUCHER_REDEEM | CAP_ROLE_MANAGE;
            // ❌ NO CAP_BITCOIN_SEND or CAP_BITCOIN_RECEIVE
        
        case ROLE_AGENT:
            return getCapabilityMaskForRole(ROLE_USER) | CAP_VOUCHER_ISSUE;
            // ❌ NO CAP_BITCOIN_SEND or CAP_BITCOIN_RECEIVE
        
        case ROLE_MERCHANT:
            return getCapabilityMaskForRole(ROLE_USER)
                    | CAP_MERCHANT_SETTLEMENT_VIEW
                    | (FeatureFlags.isLightningEnabled() ? CAP_MERCHANT_ACCEPT_LIGHTNING : 0);
            // ❌ NO CAP_BITCOIN_SEND or CAP_BITCOIN_RECEIVE
        
        case ROLE_VERIFIER:
            return getCapabilityMaskForRole(ROLE_USER)
                    | CAP_VERIFIER_INSPECT
                    | CAP_VERIFIER_APPROVE_SETTLEMENT
                    | CAP_VERIFIER_RESOLVE_DISPUTE
                    | CAP_RISK_REVIEW_LOCAL;
            // ❌ NO CAP_BITCOIN_SEND or CAP_BITCOIN_RECEIVE
        
        default:
            return 0;
    }
}
```

### 3. No Authorization in BitcoinWallet Methods

**File:** `BitcoinWallet.java`

#### Method: `createAndSignTransaction()` (Lines 315-387)

```java
public SendTransactionResult createAndSignTransaction(
        String recipientAddress,
        long amountSats,
        long feeRateSatPerVbyte,
        char[] passwordChars) throws Exception {
    
    // ❌ NO AUTHORIZATION CHECKS
    // ❌ NO ROLE VALIDATION
    // ❌ NO LIMIT CHECKING
    // ❌ NO SUSPENSION/REVIEW STATUS CHECK
    
    if (!isInitialized()) {
        throw new IllegalStateException("Wallet not initialized");
    }
    
    if (passwordChars == null || passwordChars.length == 0) {
        throw new IllegalArgumentException("Password is required for transaction signing");
    }
    
    // ... transaction creation proceeds WITHOUT ANY AUTHORIZATION
```

**Issues:**
- Only checks: wallet initialization and password
- Missing: Role-based authorization
- Missing: Daily/monthly limits enforcement
- Missing: Risk tier validation
- Missing: Suspension/review status check
- Missing: Step-up authentication (for high-value transactions)

#### Method: `broadcastTransaction()` (Lines 392-397)

```java
public String broadcastTransaction(String signedTxHex) throws Exception {
    EsploraApiClient apiClient = new EsploraApiClient(networkParams);
    String txid = apiClient.broadcastTransaction(signedTxHex);
    Log.d(TAG, "Broadcasted transaction: " + txid);
    return txid;
}
```

**Issues:**
- ❌ NO AUTHORIZATION CHECKS WHATSOEVER
- ❌ NO VALIDATION THAT TRANSACTION IS AUTHORIZED
- ❌ NO AUDIT LOGGING

### 4. No Authorization in SendBitcoinActivity

**File:** `SendBitcoinActivity.java`

#### Key Methods Without Authorization:

**`onCreate()` (Line 86)**
```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    try {
        // ❌ NO CHECK: Can user access send Bitcoin function?
        // ❌ NO CHECK: Does this role have CAP_BITCOIN_SEND?
        // ❌ NO CHECK: Is user suspended?
        
        setContentView(R.layout.activity_send_bitcoin);
        wallet = new BitcoinWallet(this, walletId);
        // ... continues without authorization
```

**`executeTransaction()` (Line 287)**
```java
private void executeTransaction(String recipientAddress, long amountSats, String password) {
    sendButton.setEnabled(false);
    
    backgroundExecutor.execute(() -> {
        try {
            // ❌ NO AUTHORIZATION CHECK BEFORE:
            // ❌ NO: wallet.loadEncryptedSeed()
            // ❌ NO: wallet.createAndSignTransaction()
            // ❌ NO: wallet.broadcastTransaction()
            
            wallet.loadEncryptedSeed(passwordChars);
            BitcoinWallet.SendTransactionResult txResult =
                    wallet.createAndSignTransaction(recipientAddress, amountSats, 
                                                   currentFeeRateSatPerVbyte, passwordChars);
            String txid = wallet.broadcastTransaction(txResult.signedTxHex);
            // ... proceeds with no authorization
```

---

## Role Authorization Gaps

### ROLE_USER
- **Current Capabilities:** Wallet View, Wallet Backup, Voucher Redeem, Role Manage
- **Bitcoin Send:** ❌ NOT GRANTED
- **Bitcoin Receive:** ✓ Implied (getting address)
- **Expected:** Should be granted send capability (base user)

### ROLE_AGENT  
- **Current Capabilities:** ROLE_USER + Voucher Issue
- **Bitcoin Send:** ❌ NOT GRANTED
- **Bitcoin Receive:** ✓ Implied
- **Expected:** Should be granted send capability with limits (5M daily, 100M monthly)

### ROLE_MERCHANT
- **Current Capabilities:** ROLE_USER + Settlement View + Lightning (if enabled)
- **Bitcoin Send:** ❌ NOT GRANTED
- **Bitcoin Receive:** ✓ Receives payments via Lightning
- **Expected:** Should be granted send capability with limits (10M daily, 200M monthly)

### ROLE_VERIFIER
- **Current Capabilities:** ROLE_USER + Verifier functions
- **Bitcoin Send:** ❌ NOT GRANTED
- **Bitcoin Receive:** ✓ Implied
- **Expected:** May or may not need P2P send (depends on policy)

---

## Missing Security Controls

### 1. Authorization Gate
```java
// MISSING: Before any transaction
SatnetRoleManager.AuthorizationResult auth = roleManager.authorize(
    SatnetRoleManager.CAP_BITCOIN_SEND,
    "Direct peer-to-peer Bitcoin transfer of " + amountSats + " sats"
);
if (!auth.allowed) {
    throw new SecurityException(auth.message);
}
```

### 2. Limit Enforcement
```java
// MISSING: Check daily/monthly limits
RoleProfile profile = roleManager.getActiveRoleProfile();
if (amountSats > profile.dailyLimitSats) {
    throw new LimitExceededException("Daily limit exceeded");
}
```

### 3. Risk Tier Validation
```java
// MISSING: Check if role can send high-value transactions
RoleProfile profile = roleManager.getActiveRoleProfile();
if (profile.riskTier == RISK_TIER_RESTRICTED) {
    throw new SecurityException("This role cannot send Bitcoin");
}
```

### 4. Suspension Status Check
```java
// MISSING: Check if role is suspended
RoleProfile profile = roleManager.getActiveRoleProfile();
if (profile.status == ROLE_STATUS_SUSPENDED) {
    throw new SecurityException("Role is suspended: " + profile.suspensionReason);
}
```

### 5. High-Value Step-Up
```java
// MISSING: Require additional verification for high values
RoleProfile profile = roleManager.getActiveRoleProfile();
if (profile.requiresStepUpForHighValue && 
    amountSats > getDefaultStepUpThreshold(profile.role)) {
    // Require additional face authentication, SMS verification, etc.
}
```

---

## Compliance Issues

### SATNET Policy
According to `SatnetPolicy.enforceBuildPolicy()` pattern used throughout:
- **Current State:** Policy checks ONLY in role initialization, not in transactions
- **Gap:** Bitcoin send/receive should also respect policy

### Feature Flags
According to `FeatureFlags` pattern:
- **Current State:** Lightning is gated by feature flag, but onchain Bitcoin send is NOT
- **Gap:** Should have feature flag for P2P Bitcoin send status

---

## Recommendations

### Priority 1: CRITICAL - Implement Missing Capabilities

1. Add capability constants:
```java
public static final int CAP_BITCOIN_SEND = 1 << 11;
public static final int CAP_BITCOIN_RECEIVE = 1 << 12;  // if needed
```

2. Add to `hasFeature()` check:
```java
case "bitcoin_send":
    return hasCapability(CAP_BITCOIN_SEND);
case "bitcoin_receive":
    return hasCapability(CAP_BITCOIN_RECEIVE);
```

### Priority 2: CRITICAL - Add Authorization Checks

1. Modify `BitcoinWallet.createAndSignTransaction()`:
```java
// Add at the beginning
SatnetRoleManager roleManager = new SatnetRoleManager(appContext);
AuthorizationResult auth = roleManager.authorize(
    CAP_BITCOIN_SEND, 
    "Create transaction: " + amountSats + " sats to " + recipientAddress);
if (!auth.allowed) {
    throw new SecurityException(auth.message);
}

// Check limits
RoleProfile profile = roleManager.getActiveRoleProfile();
if (amountSats > profile.dailyLimitSats) {
    throw new IllegalArgumentException("Exceeds daily limit: " + profile.dailyLimitSats);
}
if (amountSats > profile.monthlyLimitSats) {
    throw new IllegalArgumentException("Exceeds monthly limit: " + profile.monthlyLimitSats);
}
```

2. Modify `SendBitcoinActivity.onCreate()`:
```java
// Add role check
SatnetRoleManager roleManager = new SatnetRoleManager(this);
if (!roleManager.hasCapability(SatnetRoleManager.CAP_BITCOIN_SEND)) {
    Toast.makeText(this, "Your role is not authorized for Bitcoin transfers", 
                  Toast.LENGTH_LONG).show();
    finish();
    return;
}
```

### Priority 3: Map Capabilities to Roles

Update `getCapabilityMaskForRole()`:
```java
case ROLE_USER:
    return CAP_WALLET_VIEW | CAP_WALLET_BACKUP | CAP_VOUCHER_REDEEM 
         | CAP_ROLE_MANAGE | CAP_BITCOIN_SEND;  // Add this
    
case ROLE_AGENT:
    return getCapabilityMaskForRole(ROLE_USER) | CAP_VOUCHER_ISSUE 
         | CAP_BITCOIN_SEND;  // Explicitly include
    
case ROLE_MERCHANT:
    return getCapabilityMaskForRole(ROLE_USER) 
         | CAP_MERCHANT_SETTLEMENT_VIEW | CAP_BITCOIN_SEND  // Add this
         | (FeatureFlags.isLightningEnabled() ? CAP_MERCHANT_ACCEPT_LIGHTNING : 0);
```

### Priority 4: Add Feature Flag

```java
// In FeatureFlags.java or configuration
public static boolean isBitcoinP2PSendEnabled() {
    return BuildConfig.FEATURE_BITCOIN_P2P_SEND;
}
```

### Priority 5: Audit Trail

Add logging to `VoucherLedger` or new `TransactionAuditLog`:
```java
voucherLedger.recordBitcoinTransaction(
    participantId,
    roleManager.getActiveRole(),
    recipientAddress,
    amountSats,
    txid,
    "SENT",
    System.currentTimeMillis()
);
```

---

## Testing Gaps

No authorization tests for P2P Bitcoin operations:
- ❌ Test: User cannot send if not authorized
- ❌ Test: Agent cannot exceed daily limits  
- ❌ Test: Merchant with high risk tier cannot send
- ❌ Test: Suspended role cannot send
- ❌ Test: High-value transactions require step-up
- ❌ Test: Authorization events logged to audit trail

---

## Files Requiring Changes

| File | Change Type | Priority |
|------|------------|----------|
| `SatnetRoleManager.java` | Add capabilities, map to roles | P1 |
| `BitcoinWallet.java` | Add authorization checks | P1 |
| `SendBitcoinActivity.java` | Add role gate at onCreate | P1 |
| `ReceiveBitcoinActivity.java` | If exists, add role gate | P2 |
| `FeatureFlags.java` | Add P2P send feature flag | P2 |
| `VoucherLedger.java` | Add transaction audit tracking | P2 |
| Test files (new) | Add authorization tests | P3 |

---

## Conclusion

**Current State:** Bitcoin send/receive is a WIDE-OPEN security vulnerability with ZERO role-based access control.

**Risk Level:** SEVERE - Any user/role can send Bitcoin regardless of their designated permissions.

**Action Required:** Implement comprehensive authorization framework for P2P Bitcoin operations BEFORE deployment to production.

---

**Next Steps:**
1. Schedule immediate code review
2. Implement Priority 1 & 2 fixes
3. Add comprehensive test coverage
4. Conduct security audit before production release

