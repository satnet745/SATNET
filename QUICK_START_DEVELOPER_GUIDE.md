# SATNET GLOBAL - Quick Start Developer Guide

## What Is SATNET GLOBAL?

A **non-custodial, decentralized Bitcoin voucher network** for cash-based economies. Users can:

- **BUY Bitcoin** with cash from agents (instant)
- **SELL Bitcoin** back for cash (72-hour verified settlement)

All transactions are secured by **economic incentives, not trust**.

---

## Build & Compile

### Prerequisites

```bash
# Verify you have:
- Android Studio 4.0+
- Gradle 8.13+
- Min SDK: 19 (Micro tier agents)
- Target SDK: 34
- Java 11+
```

### Quick Build

```bash
cd C:\Users\Test\AndroidStudioProjects\batphone
./gradlew clean build
```

### Expected Result

```
✅ Build successful (0 errors, 5 warnings)
APK ready: app/build/outputs/apk/debug/app-debug.apk
```

---

## Core Components (Just Built)

### 1. **BitcoinWallet.java** - Self-Custody
```java
// User's private keys, never touch server
BitcoinWallet wallet = new BitcoinWallet(context, "user_wallet_1");
wallet.generateNewWallet("password");           // Generate seed
wallet.getReceiveAddress();                     // Get BTC address
wallet.getRecoveryPhrase();                     // 12-word backup
```

### 2. **BitcoinVoucher.java** - Bidirectional Vouchers
```java
// BUY voucher (cash → Bitcoin)
BitcoinVoucher buyVoucher = BitcoinVoucher.generateNew(
    "agent_alice",              // Agent ID
    5000,                       // 5000 sats
    24,                         // 24-hour expiry
    BitcoinVoucher.DIRECTION_BUY,
    43500.0,                    // Exchange rate (BTC/UGX)
    "UGX"                       // Currency code
);

// SELL voucher (Bitcoin → cash, requires Verifier)
BitcoinVoucher sellVoucher = BitcoinVoucher.generateNew(
    "agent_alice",
    5000,
    24,
    BitcoinVoucher.DIRECTION_SELL,  // Different direction!
    43500.0,
    "UGX"
);
```

### 3. **ExchangeRateManager.java** - Live Exchange Rates
```java
ExchangeRateManager rates = new ExchangeRateManager(context);

// Fetch BTC/UGX rate (5-min cache, offline-ready)
double rate = rates.getExchangeRate("UGX", new ExchangeRateCallback() {
    @Override
    public void onRateReceived(double rate, String source) {
        Log.d("Rate", "1 BTC = " + rate + " UGX (from " + source + ")");
    }
    
    @Override
    public void onRateFailed(boolean hasOfflineFallback, String reason) {
        Log.w("Rate", "Failed: " + reason);
    }
});
```

### 4. **SettlementVerifier.java** - 72-Hour SELL Verification
```java
SettlementVerifier verifier = new SettlementVerifier(context);

// Agent creates SELL voucher → enters SETTLEMENT_PENDING
verifier.recordSELLVoucherSettlement(sellVoucher, userWallet, 500000.0); // UGX

// Community Verifier confirms cash delivery (72-hour window)
verifier.verifySELLVoucher(
    "settlement_id",
    "verifier_bob",             // Verifier's SATNET ID
    true,                       // Cash confirmed?
    "photo_hash_abc123"         // Evidence (photo of cash)
);

// Bitcoin released to user wallet
verifier.markBitcoinReleased("settlement_id");
```

### 5. **QRCodeGenerator.java** - Crypto-Verified QR Codes
```java
// Generate BUY voucher QR
Bitmap qrBitmap = QRCodeGenerator.generateVoucherQR(
    buyVoucher.getVoucherId(),
    buyVoucher.getSecret(),
    buyVoucher.getDenomination(),
    buyVoucher.getAgentId(),
    BitcoinVoucher.DIRECTION_BUY,
    43500.0,
    "UGX"
);

// QR contains: satnet_voucher|abc123|secret|5000|alice|1|43500.0|UGX
// User scans → verifies SHA256 hash → receives Bitcoin
```

---

## Voucher Flow Examples

### BUY Flow (Instant Settlement)

```
Agent Alice has: Bitcoin stake (0.001 BTC tier)
User Bob has: 500,000 UGX cash

STEP 1: Agent generates BUY voucher
        buyVoucher = generateNew("alice", 5000, 24, BUY, 43500, "UGX")
        
STEP 2: Agent shows QR code to Bob
        Bitmap qr = QRCodeGenerator.generateVoucherQR(...)
        
STEP 3: Bob scans QR → verification passes
        ValidationResult result = buyVoucher.validate()
        ✓ Hash matches, not expired, not redeemed
        
STEP 4: Bob gives Alice 500,000 UGX cash
        
STEP 5: Alice marks voucher issued
        ledger.recordBidirectionalVoucher(buyVoucher)
        
STEP 6: Bob redeems voucher → wallet receives 5000 sats
        INSTANT SETTLEMENT ✓
```

**Duration**: ~5 minutes  
**Trust Required**: None (cryptographic verification)

---

### SELL Flow (72-Hour Verified Settlement)

```
User Bob has: 5000 sats in wallet
Agent Alice has: 500,000 UGX cash reserve (verified)

STEP 1: Bob creates SELL voucher (requires verified cash reserve)
        ✓ Alice declared 500,000 UGX reserve
        ✓ Verifier confirmed last week (valid for 7 days)
        ✓ Daily limit not exceeded
        
        sellVoucher = generateNew("alice", 5000, 24, SELL, 43500, "UGX")
        ledger.recordBidirectionalVoucher(sellVoucher)
        
STEP 2: Bob sends 5000 sats to Alice
        wallet.sendTransaction(5000, alice_address)
        
STEP 3: Voucher enters SETTLEMENT_PENDING (72-hour window)
        State: STATE_SETTLEMENT_PENDING
        Deadline: now + 72 hours
        
STEP 4: Community Verifier reviews within 72 hours
        Verifier Carol examines:
        - Photo of 500,000 UGX cash (with timestamp)
        - Alice's identity card + SATNET ID signature
        - Bob confirms received cash
        
        verifier.verifySELLVoucher("settlement_id", "carol", true, "photo_hash")
        
STEP 5: Upon verification → SETTLEMENT_VERIFIED
        State: STATE_SETTLEMENT_VERIFIED
        
STEP 6: Bitcoin released to Bob's wallet
        verifier.markBitcoinReleased("settlement_id")
        Bob now owns sats (if he sent them) OR settled trade complete
        
FALLBACK: If 72 hours pass without verification
        verifier.checkAndAutoRelease("settlement_id")
        Settlement auto-released (no Bitcoin locked forever)
```

**Duration**: < 1 hour typical (up to 72 hours max)  
**Fraud Prevention**: Rate lock (30-min), Verifier review (72-hour window)  
**Economic Security**: Cost of fraud (100% stake slash) > Gain ($50 max)

---

## Key Classes & Methods

### BitcoinVoucher

```java
// Generation
static BitcoinVoucher generateNew(String agent, long sats, int expiryHours,
                                  int direction, double rate, String currency)

// Parsing (from QR code)
static BitcoinVoucher parseQRPayload(String qrData)

// Validation
ValidationResult validate()

// Settlement (SELL only)
void markSettlementVerified()

// QR format
String getQRPayload()  
// Returns: satnet_voucher|id|secret|amount|agent|direction|rate|currency
```

### ExchangeRateManager

```java
// Get rate with async callback
double getExchangeRate(String currencyCode, ExchangeRateCallback callback)

// Manual override (±2% limit)
boolean setManualOverride(String currency, double baseRate, double override)

// Clear override to use live rates
void clearManualOverride(String currency)

// Get rate source for audit
String getRateSource(String currency)
```

### SettlementVerifier

```java
// Record SELL voucher entry into 72-hour window
void recordSELLVoucherSettlement(BitcoinVoucher voucher, 
                                 String userWallet, double localAmount)

// Verifier confirms cash delivery
boolean verifySELLVoucher(String settlementId, String verifierId,
                         boolean cashConfirmed, String evidenceHash)

// Check if past 72-hour deadline and auto-release
boolean checkAndAutoRelease(String settlementId)

// Get remaining time (milliseconds)
long getRemainingTime(String settlementId)
```

### AgentReputation

```java
// Cash reserve management
void declareCashReserve(String agentId, double amount)
boolean canIssueSELLVoucher(String agentId, double amount)
void recordSELLVoucher(String agentId, double amount)
void markReserveVerified(String agentId)
boolean isReserveVerified(String agentId)

// Fraud response
void slashStake(String agentId, long amount, String reason, String severity)
// severity: "partial" (50%) or "full" (100% + blacklist)
```

---

## Fraud Prevention at a Glance

### Economic Security Rule

**Maximum Potential Loss > Maximum Possible Scam Gain**

| Scenario | Loss | Gain | Decision |
|----------|------|------|----------|
| Micro agent fraud (0.001 BTC) | -$26 | +$50 | **Honest** (loss > gain) |
| Local agent fraud (0.005 BTC) | -$130 | +$300 | **Honest** |
| Agent defaults on SELL | -0.001 BTC + blacklist | $50 one-time | **Never worth it** |

### Rate Locking

- **BUY**: 24-hour lock (user protected from volatility)
- **SELL**: 30-minute lock (rate gaming prevention)

### Settlement Verification

- **SELL vouchers**: 72-hour Verifier window (human review)
- **Timeout protection**: Auto-release if unverified (no frozen funds)
- **Slashing**: 100% stake removal if fraud detected

---

## Database Schema Quick Reference

### vouchers table
```sql
CREATE TABLE vouchers (
  voucher_id TEXT PRIMARY KEY,      -- Unique ID
  agent_id TEXT,                    -- Agent who issued
  denomination INTEGER,             -- Sats amount
  secret_hash TEXT,                 -- SHA256 of secret (fraud prevention)
  direction INTEGER,                -- 1=BUY, 2=SELL
  exchange_rate REAL,               -- BTC/local currency rate
  currency_code TEXT,               -- USD, UGX, KES, TZS, ETB, CDF, SSP
  state INTEGER,                    -- 1=ISSUED, 2=REDEEMED, 5=SETTLEMENT_PENDING, 6=SETTLEMENT_VERIFIED
  settlement_verified BOOLEAN,      -- SELL: verified by Verifier?
  issued_time INTEGER,              -- Timestamp
  expiry_time INTEGER               -- Deadline
);
```

### agents table
```sql
CREATE TABLE agents (
  agent_id TEXT PRIMARY KEY,
  name TEXT,
  tier INTEGER,                     -- 1-5 (Candidate-Anchor)
  declared_cash_reserve REAL,       -- Self-declared (Verifier-checked)
  reserve_verified_time INTEGER,    -- When last verified (7-day validity)
  max_daily_sell_limit REAL,        -- Max SELL vouchers/day
  sell_vouchers_issued_today INTEGER  -- Daily counter
);
```

### settlements table
```sql
CREATE TABLE settlements (
  settlement_id TEXT PRIMARY KEY,
  voucher_id TEXT,
  agent_id TEXT,
  state TEXT,                       -- pending, verified, auto_released, failed
  issued_time INTEGER,
  settlement_deadline INTEGER,      -- issued_time + 72 hours
  verified_by_verifier_id TEXT,
  verified_time INTEGER
);
```

---

## Testing Checklist

### Unit Tests (Ready)

```java
// Test BUY voucher creation
BitcoinVoucher buy = generateNew("agent1", 5000, 24, BUY, 43500, "UGX");
assert(buy.validate().isValid);

// Test SELL voucher creation
BitcoinVoucher sell = generateNew("agent1", 5000, 24, SELL, 43500, "UGX");
assert(sell.getState() == STATE_SETTLEMENT_PENDING);

// Test rate lock validation (30-min window for SELL)
long now = System.currentTimeMillis();
long past = now - 31 * 60 * 1000;  // 31 minutes ago
// expect: ValidationResult.isValid == false

// Test slashing (cost > gain)
long stakeAmount = 100000;   // 0.001 BTC
double maxDailySellUSD = 50;
// Fraud detected → slash full stake
// Loss: 0.001 BTC (~$26) > Gain: $50 → Agent stays honest
```

### Integration Tests (Phase 3)

```
[ ] BUY flow: User → Agent → Instant settlement
[ ] SELL flow: User → Agent → Verifier → Release (72hr)
[ ] Rate lock: Prevent rate changes during transaction
[ ] Auto-release: SELL settlement auto-released after 72hr timeout
[ ] Slashing: Fraud detected → agent blacklisted + 100% stake removal
[ ] Offline: Generate vouchers, sync later via Rhizome
```

---

## Deployment Checklist

- [ ] APK builds without errors
- [ ] Unit tests pass
- [ ] Integration tests on device pass
- [ ] Reputation system tuned for your region
- [ ] Exchange rate sources verified (Esplora API)
- [ ] Community Verifiers onboarded
- [ ] Launch agents invited
- [ ] Documentation translated (if needed)

---

## Next Steps

### Phase 2 (2-3 weeks): UI Implementation

- [ ] Agent screens: "Issue BUY" + "Issue SELL"
- [ ] User screens: "Earn Bitcoin" + "Sell Bitcoin"  
- [ ] Merchant screens: "Generate Invoice" + "Accept Payment"
- [ ] Verifier screens: "Confirm Cash" + "Review Disputes"

### Phase 3 (3-4 weeks): Advanced Features

- [ ] Merchant POS (sales reports, tax calculation)
- [ ] Education platform (learn-and-earn)
- [ ] DAO governance (community slashing appeals)
- [ ] Satellite relay support (Starlink, Iridium)

### Phase 4 (2-3 weeks): Testing & Launch

- [ ] Device testing (Pixel, Samsung, etc.)
- [ ] Load testing (1000+ agents)
- [ ] Security audit (third-party)
- [ ] Alpha launch (friends & family)

---

## Resources

| Resource | Link |
|----------|------|
| **Documentation** | [SATNET_GLOBAL_BIDIRECTIONAL_VOUCHER_SYSTEM.md](./SATNET_GLOBAL_BIDIRECTIONAL_VOUCHER_SYSTEM.md) |
| **Implementation Status** | [IMPLEMENTATION_STATUS.md](./IMPLEMENTATION_STATUS.md) |
| **Source Code** | `app/src/main/java/org/servalproject/satnet/` |
| **Bitcoin Core** | `app/src/main/java/org/servalproject/bitcoin/` |

---

## Support

### For Questions

1. Check the **SATNET_GLOBAL_BIDIRECTIONAL_VOUCHER_SYSTEM.md** for architecture details
2. Review **IMPLEMENTATION_STATUS.md** for current limitations
3. Run unit tests to verify your environment

### For Bug Reports

Include:
- Android version (min SDK 19)
- Error message + stack trace
- Steps to reproduce
- Expected behavior

---

**Last Updated**: December 27, 2025  
**Status**: Ready for Phase 2 UI Implementation

