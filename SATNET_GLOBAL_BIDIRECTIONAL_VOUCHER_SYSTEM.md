# SATNET GLOBAL: Secure Bidirectional Voucher System

## Overview

SATNET GLOBAL implements a **fraud-resistant, bidirectional Bitcoin voucher system** for cash-based economies. Users can:

1. **BUY Bitcoin** with cash from agents (forward flow)
2. **SELL Bitcoin** back to agents for cash (reverse flow)

All transactions are **non-custodial, cryptographically verified, and protected by economic incentives** rather than trust.

---

## Core Architecture

### Bidirectional Voucher Flows

#### BUY Flow (Cash → Bitcoin)
```
1. User meets agent with local currency
2. Agent generates BUY voucher (fixed amount, 24-hour expiry)
3. User scans QR code containing voucher secret
4. User pays agent cash
5. Agent marks voucher as issued
6. User redeems voucher to wallet (Bitcoin instantly received)
7. Settlement complete
```

**Security**: Cryptographic secret (SHA256) prevents double-spend; single-use enforcement via ledger.

#### SELL Flow (Bitcoin → Cash, 72-Hour Verification Window)
```
1. User sends sats to SELL voucher created by agent
2. Voucher enters SETTLEMENT_PENDING state (awaiting Verifier)
3. Agent confirms receipt, prepares cash payment
4. Community Verifier reviews agent's cash delivery proof (72-hour window):
   - Photo of cash + agent identity
   - Timestamp signed with the agent's SATNET ID
5. Verifier confirms cash paid → mark SETTLEMENT_VERIFIED
6. Bitcoin released to user wallet
7. Settlement complete (or auto-release after 72 hours if unverified)
```

**Security**: 72-hour window prevents instant cash-out fraud; Verifier review provides human oversight; 100% stake slashing deters fraud.

---

## Fraud Prevention Layer

### 1. Rate Locking (30-Minute Window)

**Problem**: Agent could manipulate rates mid-transaction.

**Solution**:
- Exchange rate locked for 30 minutes after SELL voucher issuance
- Rate source: Esplora API (every 5 minutes), cached locally
- Manual override: Agents can ±2% adjust for UX (rounded rates for cash)
- Audit trail: All rates logged for community review

**Impact**: Prevents rate-gaming; users know exact conversion before committing.

### 2. Stake-Backed Guarantees

**Problem**: Agent could default on cash payment without consequences.

**Solution**:
- Agents post Bitcoin collateral (stake) before issuing vouchers
- Stake amount ties to tier (progressive limits):
  - Micro: 0.001 BTC → max $50/day SELL
  - Local: 0.005 BTC → max $300/day SELL
  - Regional: 0.02 BTC → max $1,500/day SELL
- **Economic rule**: Maximum potential loss (stake) >> maximum scam gain

**Example**:
- Agent with 0.001 BTC stake can issue max $50/day in SELL vouchers
- Cost of fraud: Lose entire 0.001 BTC ($26 USD equivalent)
- Gain from fraud: $50
- Risk/reward: -0.001 BTC loss vs +$50 gain = **not worth it**

### 3. Cash Reserve Verification

**Problem**: Agent claims to have cash but doesn't.

**Solution**:
- Agent self-declares cash on hand (with consequences)
- Daily SELL limit = declared cash reserve (simplified)
- Community Verifier spot-checks (weekly):
  - Photos of cash + identity card + timestamp
  - Signed with the agent's SATNET ID
- Reserve must be verified within last 7 days
- Verification failure → cannot issue SELL vouchers

**Slashing Incentive**:
- If cash not produced, agent loses 50% of stake immediately
- Fraud confirmed (no cash-out) → 100% stake slash + blacklist

### 4. Settlement Verification (72-Hour Window)

**Problem**: Agent receives Bitcoin but doesn't pay cash; Bitcoin locked in limbo.

**Solution**:
- SELL vouchers enter `STATE_SETTLEMENT_PENDING` upon issuance
- Community Verifier has 72 hours to confirm cash delivery
- Proof: Agent shows receipt, photo, user confirmation
- On verification → `STATE_SETTLEMENT_VERIFIED` → Bitcoin released
- Timeout: If not verified within 72 hours → auto-release (prevents lock-up)

**Double-Spend Prevention**:
- Each voucher has unique SHA256 secret
- Ledger marks as `STATE_REDEEMED` atomically
- No replay attacks possible

### 5. Slashing Rules (Automatic & Transparent)

| Event | Severity | Consequence |
|-------|----------|-------------|
| Voucher not delivered (BUY) | Partial | Suspend + 25% stake slash |
| Cash not paid (SELL) | Full | Blacklist + 100% stake slash |
| Repeated disputes | Full | Permanent blacklist |
| Rate manipulation (>±5%) | Warning | Temporary suspension |

**Governance**: All slashing events logged to Rhizome (distributed ledger); community can appeal.

---

## Technical Implementation

### Key Classes

#### 1. **BitcoinVoucher.java** (Extended for Bidirectional)
```java
// Voucher directions
DIRECTION_BUY = 1;      // User buys Bitcoin with cash
DIRECTION_SELL = 2;     // User sells Bitcoin for cash

// Voucher states
STATE_ISSUED = 1;
STATE_SETTLEMENT_PENDING = 5;   // SELL: awaiting Verifier
STATE_SETTLEMENT_VERIFIED = 6;  // SELL: verified, ready for release

// New fields
int direction;                  // BUY or SELL
double exchangeRate;           // BTC/local currency rate
long rateLockTime;            // 30-minute rate window expiry
String currencyCode;          // USD, UGX, KES, TZS, ETB, CDF, SSP
boolean settlementVerified;   // SELL verification flag
```

**QR Payload Format**:
```
satnet_voucher|<id>|<secret>|<amount>|<agent>|<direction>|<rate>|<currency>

Example SELL:
satnet_voucher|ag_abc_123|a1b2c3d4e5f6g7h8|5000|alice_agent|2|43500.00|UGX
```

#### 2. **ExchangeRateManager.java**
- Pulls BTC/USD from Esplora API every 5 minutes
- Caches locally (offline-ready)
- Converts to local currency using pre-set multipliers
- Rounds for UX (agents use rounded rates)
- Audit trail: rate source logged

#### 3. **SettlementVerifier.java**
- Tracks 72-hour verification window for SELL vouchers
- Records Verifier confirmations with evidence hash
- Auto-releases on timeout; alerts on failures
- Integrates with AgentReputation for slashing

#### 4. **AgentReputation.java** (Extended)
- `declareCashReserve(agentId, amount)` - Agent declares cash on hand
- `canIssueSELLVoucher(agentId, amount)` - Checks reserve & daily limit
- `markReserveVerified(agentId)` - Verifier confirms cash exists
- `slashStake(agentId, amount, reason, severity)` - Fraud response
- Database schema: Added `declared_cash_reserve`, `reserve_verified_time`, `max_daily_sell_limit`, etc.

---

## Economic Security Model

### Maximum Loss > Maximum Gain

**Formula**:
```
Potential Loss (if fraud detected) > Potential Gain (from fraud)
```

**Example Calculation**:

**Scenario**: Agent with 0.001 BTC (Micro tier) issues SELL voucher for $50
- User sends 5,000 sats → voucher created
- Agent receives Bitcoin but doesn't pay cash
- Verifier investigates → no cash found
- **Result**: Agent loses entire 0.001 BTC stake (~$26) for trying to pocket $50

**Cost/Benefit**:
- Risk: -0.001 BTC (-$26 USD)
- Reward: +$50 USD
- **Expected value: NEGATIVE** (-0.001 BTC + $50 = -$26, not +$24)

**Rational agent chooses honest behavior** because fraud is economically irrational.

---

## Dispute Resolution

### Community Verifier Role

Volunteers with own Bitcoin stake act as arbiters:

1. **Mediate disputes**
   - User claims agent didn't pay cash
   - Agent claims user already received sats
   - Verifier reviews evidence (photos, signatures, ledger)

2. **Apply reputation outcomes**
   - Verified as honest → reputation increases
   - Fraud detected → slashing applied immediately

3. **Endorse agents**
   - Public endorsement on Rhizome
   - Builds agent's reputation

### Appeal Process

Agent can appeal slashing decision:
1. File appeal with evidence
2. Different Verifier panel reviews
3. Decision final if 2-of-3 panel agrees

---

## Privacy & Transparency

### Data Collection

| Data | Who Sees | Why |
|------|----------|-----|
| Voucher IDs + amounts | Public (Rhizome ledger) | Enables community audits |
| Agent names + tiers | Public | Reputation system |
| User wallet addresses | Private until redemption | Unlinkability |
| Rate history | Public | Audit fraud detection |
| Dispute decisions | Public | Deter bad actors |

### Off-Chain Privacy

- Wallet addresses hidden until redemption
- No centralized account system
- Identity tied to a SATNET ID (pseudonymous)
- Agent names public; users can choose to interact or not

---

## Offline-First Design

### Voucher Generation (Offline)

1. Agent generates voucher on their phone (no internet needed)
2. User scans QR code (offline)
3. Voucher secret verified cryptographically (offline)

### Ledger Sync (Later Online)

1. When device reconnects, sync to Rhizome (distributed)
2. Multi-relay fallback: Tor, I2P, mesh network
3. Eventually consistent (not real-time)

### Timeout Handling

- SELL settlement defaults to auto-release after 72 hours
- Prevents permanent lock-up in offline scenarios

---

## Sustainability Model

### Principles

- No mandatory protocol toll on every voucher
- No custody rent or pooled-funds monetization
- No surveillance-based business model
- Essential voucher issuance and redemption should remain available without platform lock-in

### Sustainable Funding Sources

1. **Optional federation services**
   - Regional relay hosting
   - Signed audit export infrastructure
   - Compliance adapters for licensed partners

2. **Support and deployment contracts**
   - Field deployments
   - Training for agent networks
   - Operational tooling for partner cooperatives and NGOs

3. **Community and grant funding**
   - Security audits
   - Localization
   - Low-end device optimization
   - Education programs

4. **Optional advanced tools**
   - Merchant analytics
   - Partner reporting dashboards
   - Bulk settlement operations for licensed providers

### Economic Rule

SATNET should earn from services **around** the protocol, not from owning the protocol's choke points.

If the voucher system only stays alive by taxing every user transaction, the model is too centralized.

For the broader sustainability framework, see `doc/SATNET_COMMUNITY_SUSTAINABILITY_MODEL.md`.

---

## Regulatory Position

### Legal Characterization

SATNET GLOBAL is:
- ✅ Non-custodial software (no fund holding)
- ✅ Agent-coordinated payment protocol (decentralized)
- ❌ NOT a bank, exchange, or money transmitter

### Liability Shield

- Agents operate independently (not SATNET employees)
- Users self-custody Bitcoin (SATNET has no access)
- Vouchers are payment instructions, not securities

### Country Risk Map

| Country | Risk | Notes |
|---------|------|-------|
| Uganda | Low–Moderate | Precedent: Mobile Money |
| Kenya | Moderate | CMA monitoring |
| Tanzania | Moderate–High | Regulatory uncertainty |
| South Sudan | Low | Limited enforcement |
| Ethiopia | High | Strict controls possible |
| DRC | Moderate | Political instability |

### Mitigation Strategies

- Software-only posture (no legal entity)
- Decentralized agents (no single point of failure)
- Tiered limits (low transaction sizes)
- Education focus (not financial services pitch)

---

## Implementation Checklist

- [x] BitcoinVoucher extended with direction, exchange rate, settlement fields
- [x] ExchangeRateManager created (5-min cache, manual override)
- [x] SettlementVerifier created (72-hour workflow)
- [x] AgentReputation extended (cash reserve, slashing)
- [x] QRCodeGenerator updated (bidirectional QR format)
- [ ] VoucherLedger extended (settlement status tracking)
- [ ] UI: Agent "Issue SELL Voucher" screen
- [ ] UI: User "Sell Bitcoin" → 72-hour countdown screen
- [ ] UI: Verifier "Confirm Cash Delivery" screen
- [ ] Audit logging to Rhizome (all transactions)
- [ ] Reputation dashboard (agent transparency)

---

## Conclusion

The bidirectional voucher system achieves **fraud resistance through economics, not trust**. By making dishonesty economically irrational (maximum loss > maximum gain), SATNET GLOBAL enables peer-to-peer Bitcoin adoption in cash-based economies—without custody, without banks, and without reliance on third-party intermediaries.

**If SATNET GLOBAL disappears tomorrow, the Bitcoin remains in user wallets.**

