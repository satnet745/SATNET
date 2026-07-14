# SATNET GLOBAL - System Architecture Diagrams

## 1. High-Level System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                     SATNET GLOBAL Ecosystem (Offline-First)                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────┐         ┌──────────────┐         ┌─────────────┐             │
│  │   Users     │         │   Agents     │         │  Merchants  │             │
│  │             │         │              │         │             │             │
│  │ • Wallet    │◄───────►│ • Voucher    │◄───────►│ • Lightning │             │
│  │ • Vouchers  │         │   Generator  │         │   Invoices  │             │
│  │ • Bitcoin   │         │ • Staking    │         │ • POS       │             │
│  └─────────────┘         │ • Reputation │         │ • Reporting │             │
│         │                └──────────────┘         └─────────────┘             │
│         │                       │                        │                    │
│         └───────────────────────┼────────────────────────┘                    │
│                                 │                                             │
│                    ┌────────────▼────────────┐                               │
│                    │   Settlement Verifier   │                               │
│                    │   (72-hour window)      │                               │
│                    │ • Verification workflow │                               │
│                    │ • Slashing on fraud     │                               │
│                    │ • Auto-release timeout  │                               │
│                    └────────────┬────────────┘                               │
│                                 │                                             │
│                    ┌────────────▼────────────┐                               │
│                    │   Rhizome Ledger        │                               │
│                    │ (Distributed Audit)     │                               │
│                    │ • Voucher history       │                               │
│                    │ • Dispute records       │                               │
│                    │ • Rate sources          │                               │
│                    │ • Slashing events       │                               │
│                    └─────────────────────────┘                               │
│                                 │                                             │
│                    ┌────────────▼────────────┐                               │
│                    │   Local SQLite (Cache)  │                               │
│                    │ • Voucher ledger        │                               │
│                    │ • Agent reputation      │                               │
│                    │ • Exchange rates        │                               │
│                    │ • Settlement status     │                               │
│                    └─────────────────────────┘                               │
│                                                                                 │
│  ┌──────────────────────────────────────────────────────────────────────────┐ │
│  │  Offline-First: Direct/multi-hop/internet first; Tor/I2P preconfigured   │ │
│  │                 fallback routes sync via SATNET mesh/Rhizome             │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Bidirectional Voucher Flow

```
                          SATNET GLOBAL VOUCHER FLOWS

┌─────────────────────────────────────────────────────────────────────────────┐
│  BUY FLOW (Cash → Bitcoin)                   SELL FLOW (Bitcoin → Cash)     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  User          Agent                        User         Agent    Verifier  │
│   │              │                           │            │           │    │
│   │  BUY coin    │                           │  Create    │           │    │
│   │  voucher     │                           │  SELL      │           │    │
│   ├─────────────►│                           │  voucher   │           │    │
│   │              │                           ├───────────►│           │    │
│   │              │ Send sats                 │            │           │    │
│   │              │                           │            │◄──────────┤    │
│   │              │                           │            │ Review    │    │
│   │              │                           │            │ cash      │    │
│   │  Pay cash    │                           │            │ proof     │    │
│   ├─────────────►│                           │            │ (photo)   │    │
│   │              │                           │            │           │    │
│   │ Scan QR      │                           │ SETTLEMENT │ Confirm   │    │
│   ├─────────────┐│                           │ PENDING    │ payment   │    │
│   │ Verify hash ││  (instant)                │ STATE      │           │    │
│   │             ││                           │ (72 hours) │           │    │
│   │ Receive sats │                           │            │           │    │
│   │◄─────────────┤                           │ SETTLEMENT │ MARK      │    │
│   │              │                           │ VERIFIED   │ VERIFIED  │    │
│   │              │                           │            │◄──────────┤    │
│   │              │                           │            │           │    │
│   │              │                           │ Bitcoin    │           │    │
│   │              │                           │ released   │           │    │
│   │              │                           ├───────────►│           │    │
│   │              │                           │            │           │    │
│   ▼              ▼                           ▼            ▼           ▼    │
│  SETTLED      SETTLED                      SETTLED    SETTLED    SETTLED   │
│  (5 min)      (5 min)                      (~1 hour)  (~1 hour)  (72 hrs)  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘

Key Difference:
• BUY = Instant (crypto verification only)
• SELL = 72-hour window (requires human Verifier confirmation to prevent fraud)
```

---

## 3. Fraud Prevention Layers

```
                    SATNET GLOBAL FRAUD PREVENTION STACK

┌─────────────────────────────────────────────────────────────────────────┐
│                         Layer 5: Slashing Rules                         │
│                     (Cost of fraud > Gain from fraud)                   │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │ Fraud detected: Agent loses 100% stake + permanent blacklist      │ │
│  │ Cost to agent: 0.001 BTC (~$26)                                   │ │
│  │ Gain from fraud: $50 USD                                          │ │
│  │ Decision: LOSE $26 > GAIN $50 = HONEST BEHAVIOR ✓                │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    △
                                    │
┌─────────────────────────────────────────────────────────────────────────┐
│           Layer 4: Settlement Verification (72-hour window)             │
│                     (Human + Economic oversight)                        │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │ Community Verifiers review SELL redemptions:                      │ │
│  │ • Photo of cash + agent ID (timestamp + signature)                │ │
│  │ • 72-hour review window (auto-release if timeout)                 │ │
│  │ • If cash not delivered: 100% stake slash                         │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    △
                                    │
┌─────────────────────────────────────────────────────────────────────────┐
│    Layer 3: Cash Reserve Verification (7-day validity, weekly checks)   │
│                    (Trust with Real Consequences)                       │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │ Agent declares cash reserve (e.g., 500,000 UGX)                    │ │
│  │ Verifier spot-checks (weekly):                                    │ │
│  │ • Photo of actual cash + identity                                 │ │
│  │ • Timestamp + SATNET ID signature                                 │ │
│  │ • No cash = Cannot issue SELL vouchers                            │ │
│  │ • Insufficient cash = Limited daily SELL cap                      │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    △
                                    │
┌─────────────────────────────────────────────────────────────────────────┐
│         Layer 2: Stake-Backed Guarantees (Bitcoin collateral)            │
│                  (Economic Incentive Alignment)                         │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │ Tier    Stake       Max BUY   Max SELL/day    Cost of Fraud      │ │
│  │ ────────────────────────────────────────────────────────────     │ │
│  │ Micro   0.001 BTC   $5        $50             Lose $26           │ │
│  │ Local   0.005 BTC   $20       $300            Lose $130          │ │
│  │ Regional 0.02 BTC   $100      $1,500          Lose $520          │ │
│  │ Anchor  0.05+ BTC   Dynamic   High            Lose $1,300+       │ │
│  │                                                                   │ │
│  │ Rule: Maximum potential loss MUST exceed maximum possible gain   │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    △
                                    │
┌─────────────────────────────────────────────────────────────────────────┐
│              Layer 1: Rate Locking (Cryptographic)                      │
│                  (Prevent Rate Gaming & Volatility)                    │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │ BUY vouchers:  24-hour rate lock (user protected from swings)     │ │
│  │ SELL vouchers: 30-minute rate lock (prevent agent manipulation)   │ │
│  │ Verified: Rate stored in voucher secret (SHA256)                  │ │
│  │ Audit: Rate source logged (Esplora API or cached)                 │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    △
                                    │
                        User receives Bitcoin
                    (Cryptographically verified)
```

---

## 4. Database Schema Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                      SATNET GLOBAL DATABASE                        │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│  agents                          stakes                            │
│  ┌──────────────────────┐      ┌──────────────────────┐          │
│  │ agent_id (PK)        │◄─────│ stake_id (PK)        │          │
│  │ name                 │      │ agent_id (FK)        │          │
│  │ tier [1-5]           │      │ amount_sats          │          │
│  │ reputation_score     │      │ tx_hash              │          │
│  │ declared_cash_       │      │ locked_time          │          │
│  │   reserve [USD]      │      │ status [locked,      │          │
│  │ reserve_verified_    │      │         slashed]     │          │
│  │   time               │      └──────────────────────┘          │
│  │ max_daily_sell_limit │                                        │
│  │ status [active,      │      slashing_events                   │
│  │         suspended,   │      ┌──────────────────────┐          │
│  │         blacklisted] │◄─────│ slash_id (PK)        │          │
│  └──────────────────────┘      │ agent_id (FK)        │          │
│         │                       │ reason               │          │
│         │                       │ severity [warning,   │          │
│         │                       │           partial,   │          │
│         │                       │           full]      │          │
│         │                       │ amount_sats          │          │
│         │                       │ timestamp            │          │
│         │                       └──────────────────────┘          │
│         │                                                         │
│  vouchers                                                          │
│  ┌──────────────────────┐      settlements                       │
│  │ voucher_id (PK)      │      ┌──────────────────────┐          │
│  │ agent_id (FK)────────┼─────►│ settlement_id (PK)   │          │
│  │ denomination_sats    │      │ voucher_id (FK)      │          │
│  │ secret_hash          │      │ agent_id (FK)        │          │
│  │ direction [1=BUY,    │      │ user_wallet          │          │
│  │          2=SELL]     │      │ issued_time          │          │
│  │ exchange_rate        │      │ deadline [+72hrs]    │          │
│  │ currency_code        │      │ status [pending,     │          │
│  │ state [1=issued,     │      │         verified,    │          │
│  │        5=settlement_ │      │         auto_release,│          │
│  │          pending,    │      │         failed]      │          │
│  │        6=settlement_ │      │ verified_by (FK)     │          │
│  │          verified]   │      │ verified_time        │          │
│  │ settlement_verified  │      └──────────────────────┘          │
│  │ issued_time          │                                        │
│  │ expiry_time          │      verifications                      │
│  │ redeemed_time        │      ┌──────────────────────┐          │
│  └──────────────────────┘◄─────│ verification_id (PK) │          │
│                                 │ settlement_id (FK)   │          │
│                                 │ verifier_id          │          │
│                                 │ verification_time    │          │
│                                 │ cash_confirmed [T/F] │          │
│                                 │ evidence_hash        │          │
│                                 └──────────────────────┘          │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

---

## 5. Class Interaction Diagram

```
                    SATNET GLOBAL CLASS INTERACTIONS

┌─────────────────────────────────────────────────────────────────┐
│                      User/Agent Interface                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  [BitcoinWalletActivity]                                        │
│         │                                                       │
│         ├──► BitcoinWallet                                      │
│         │    • generateNewWallet()                              │
│         │    • getReceiveAddress()                              │
│         │    • signTransaction()                                │
│         │    └──► WalletEncryption                              │
│         │         • encryptSeed()                               │
│         │         • decryptSeed()                               │
│         │                                                       │
│         ├──► AgentVoucherActivity                               │
│         │    • generateBUYVoucher()                             │
│         │    • generateSELLVoucher()                            │
│         │    └──► BitcoinVoucher                                │
│         │         • generateNew()                               │
│         │         • validate()                                  │
│         │         • markSettlementVerified()                    │
│         │         └──► QRCodeGenerator                          │
│         │              • generateVoucherQR()                    │
│         │              • decodeQRCode()                         │
│         │                                                       │
│         ├──► ExchangeRateManager                                │
│         │    • getExchangeRate()                                │
│         │    • setManualOverride()                              │
│         │    └──► EsploraAPI (existing)                         │
│         │         • fetchRatess()                               │
│         │                                                       │
│         ├──► SettlementVerifier                                 │
│         │    • recordSELLVoucherSettlement()                    │
│         │    • verifySELLVoucher()                              │
│         │    • checkAndAutoRelease()                            │
│         │    └──► AgentReputation                               │
│         │         • slashStake()  ◄── ON FRAUD                  │
│         │                                                       │
│         ├──► VoucherLedger                                      │
│         │    • recordIssuedVoucher()                            │
│         │    • recordBidirectionalVoucher()                     │
│         │    • markSettlementVerified()                         │
│         │    └──► SQLiteDatabase                                │
│         │         (Local cache)                                 │
│         │                                                       │
│         └──► AgentReputation                                    │
│              • registerAgent()                                  │
│              • recordStake()                                    │
│              • declareCashReserve()                             │
│              • canIssueSELLVoucher()                            │
│              • recordSELLVoucher()                              │
│              • markReserveVerified()                            │
│              • slashStake()                                     │
│              └──► SQLiteDatabase                                │
│                   (Agent profiles, stakes)                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 6. Settlement Verification State Machine (SELL Only)

```
                         SELL VOUCHER STATE MACHINE

                              ┌─────────────┐
                              │   ISSUED    │  (BUY vouchers end here)
                              └──────┬──────┘
                                     │
                              New SELL voucher
                                     │
                                     ▼
                        ┌────────────────────────┐
                        │ SETTLEMENT_PENDING     │
                        │ (72-hour window)       │
                        │ • Awaiting Verifier    │
                        │ • User sent sats ✓     │
                        │ • Agent has Bitcoin    │
                        └────────┬───────┬───────┘
                                 │       │
                    ┌────────────┘       └─────────────┐
                    │                                   │
         Verifier confirms         72 hours expire      
         cash delivered            (no verification)    
                    │                                   │
                    ▼                                   ▼
        ┌────────────────────┐           ┌──────────────────────┐
        │ SETTLEMENT_        │           │ AUTO_RELEASED        │
        │ VERIFIED           │           │ (Timeout safety)     │
        │ • Evidence hashed  │           │ • No funds locked    │
        │ • Verified by      │           │ • Alert raised       │
        │   Verifier         │           │ • May indicate       │
        │ • Bitcoin ready    │           │   problem            │
        └────────┬───────────┘           └──────────┬───────────┘
                 │                                   │
         Bitcoin released                  Bitcoin released
         (1 hour typical)              (if user needs rescue)
                 │                                   │
                 ▼                                   ▼
        ┌────────────────────┐           ┌──────────────────────┐
        │ REDEEMED           │           │ REDEEMED             │
        │ (Settlement OK)    │           │ (Auto-released)      │
        └────────────────────┘           └──────────────────────┘

FRAUD PATH (If cash not delivered):
    SETTLEMENT_PENDING ──NO CASH──► slashStake() ──► Full slash (100%)
                                   + Blacklist agent
```

---

## 7. Exchange Rate Update Flow

```
                    EXCHANGE RATE FETCH CYCLE

┌──────────────────────────────────────────────────────────────┐
│                                                              │
│  Every 5 minutes (or on demand)                              │
│  ├─► ExchangeRateManager.getExchangeRate("UGX")             │
│  │   ├─► Check manual override (agent ±2%)                  │
│  │   │   └─► Return immediately if set                      │
│  │   │                                                       │
│  │   ├─► Check local cache (SharedPreferences)              │
│  │   │   ├─► If fresh (< 5 min old)                         │
│  │   │   │   └─► Return cached rate                         │
│  │   │   │                                                   │
│  │   │   └─► If stale (> 5 min old)                         │
│  │   │       └─► Fetch from Esplora API (async)             │
│  │   │                                                       │
│  │   ├─► OkHttpClient.newCall(Esplora API)                  │
│  │   │   └─► GET https://blockstream.info/api/ticker        │
│  │   │       ├─► Get BTC/USD rate                           │
│  │   │       └─► Convert to local currency                  │
│  │   │           (USD × multiplier for UGX, etc.)           │
│  │   │                                                       │
│  │   ├─► Round rate for UX                                  │
│  │   │   (e.g., 43567 → 43570 for cash txns)               │
│  │   │                                                       │
│  │   └─► Cache result + timestamp                           │
│  │       └─► SharedPreferences.putLong(rate, source)       │
│  │                                                           │
│  └─► Return rate to caller                                  │
│      └─► If offline: Return last cached rate                │
│                                                              │
│  All rates logged for audit trail (Rhizome sync)            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 8. Slashing Decision Tree (Fraud Response)

```
                     FRAUD DETECTION & SLASHING LOGIC

Fraud Detected?
├─ YES: Cash not delivered (SELL voucher)
│  │
│  ├─► Gather evidence
│  │   ├─ Verifier photo (no cash)
│  │   ├─ User testimony (no payment)
│  │   └─ Ledger mismatch (Bitcoin gone)
│  │
│  ├─► Calculate damage
│  │   ├─ Voucher amount: 5000 sats
│  │   ├─ Agent stake: 0.001 BTC (Micro tier)
│  │   └─ Slashing severity: FULL (100%)
│  │
│  ├─► Apply slashing
│  │   ├─► AgentReputation.slashStake(
│  │   │     agent_id, 100000, 
│  │   │     "SELL fraud: cash not delivered",
│  │   │     "full")
│  │   │
│  │   └─ Database updates:
│  │      ├─ stakes table: status = "slashed"
│  │      ├─ agents table: status = "blacklisted"
│  │      ├─ slashing_events table: record added
│  │      └─ settlements table: status = "failed"
│  │
│  └─► Outcome
│      ├─ Agent loses: 0.001 BTC (~$26)
│      ├─ Gain from fraud: $50 (voucher amount)
│      ├─ Expected value: -$26 (NEGATIVE)
│      └─ Rational behavior: NEVER FRAUD ✓
│
└─ NO: No fraud detected
   └─► Continue normal settlement
       └─► SETTLEMENT_VERIFIED state
```

---

## 9. Integration Architecture

```
SATNET GLOBAL Integrated with Existing SATNET Mesh & SATNET Components

┌──────────────────────────────────────────────────────────────────┐
│                    SATNET Mesh / SATNET App                      │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │           UI Layer (Phase 2)                            │   │
│  │  • Agent Voucher Screens                                │   │
│  │  • User Wallet Screens                                  │   │
│  │  • Merchant POS Screens                                 │   │
│  │  • Verifier Review Screens                              │   │
│  └─────────────────────────────────────────────────────────┘   │
│                           │                                      │
│  ┌────────────────────────▼────────────────────────────────┐   │
│  │        SATNET GLOBAL Core (Phase 1 - COMPLETE)          │   │
│  │                                                          │   │
│  │  ┌──────────────┬──────────────┬──────────────┐         │   │
│  │  │   Bitcoin    │   Vouchers   │   Merchants  │         │   │
│  │  │   Wallet     │   System     │   POS        │         │   │
│  │  │              │              │              │         │   │
│  │  │ • Self-      │ • BUY/SELL   │ • Lightning  │         │   │
│  │  │   custody    │   vouchers   │   invoices   │         │   │
│  │  │ • BIP39      │ • Crypto     │ • Local      │         │   │
│  │  │   recovery   │   verification│   currency  │         │   │
│  │  │ • Offline    │ • Rate       │ • POS tools  │         │   │
│  │  │   signing    │   locking    │              │         │   │
│  │  └──────────────┴──────────────┴──────────────┘         │   │
│  │                                                          │   │
│  │  ┌──────────────┬──────────────┬──────────────┐         │   │
│  │  │  Agent       │ Settlement   │ Exchange     │         │   │
│  │  │  Staking     │ Verifier     │ Rates        │         │   │
│  │  │              │              │              │         │   │
│  │  │ • Tiers      │ • 72-hr      │ • 5-min      │         │   │
│  │  │   [1-5]      │   window     │   cache      │         │   │
│  │  │ • Slashing   │ • Auto-      │ • Esplora    │         │   │
│  │  │   rules      │   release    │   API        │         │   │
│  │  │ • Cash       │ • Verifier   │ • 7 currency │         │   │
│  │  │   reserves   │   workflow   │   codes      │         │   │
│  │  └──────────────┴──────────────┴──────────────┘         │   │
│  └──────────────────────┬───────────────────────────────────┘   │
│                         │                                        │
│  ┌──────────────────────▼───────────────────────────────────┐   │
│  │        Existing SATNET Components                        │   │
│  │                                                          │   │
│  │  ├─ SatnetRoleManager (User/Agent/Merchant roles)       │   │
│  │  ├─ EsploraAPI (Bitcoin blockchain queries)             │   │
│  │  ├─ LightningPaymentHandler (LN integration)            │   │
│  │  ├─ Rhizome (Distributed ledger & files)                │   │
│  │  └─ SATNET Mesh (Offline mesh routing)                  │   │
│  └──────────────────────┬───────────────────────────────────┘   │
│                         │                                        │
│  ┌──────────────────────▼───────────────────────────────────┐   │
│  │        Android Framework & Libraries                     │   │
│  │                                                          │   │
│  │  ├─ SQLite (Local cache)                                │   │
│  │  ├─ SharedPreferences (Rate cache)                       │   │
│  │  ├─ OkHttp3 (API calls)                                  │   │
│  │  ├─ ZXing (QR generation)                                │   │
│  │  └─ bitcoinj (Wallet operations)                         │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## Summary

These diagrams illustrate:

1. **High-Level System**: All components and data flows
2. **Bidirectional Flows**: BUY (instant) vs SELL (72-hour)
3. **Fraud Prevention**: 5-layer stack from cryptography to slashing
4. **Database Schema**: Normalized tables with relationships
5. **Class Interactions**: How components communicate
6. **State Machine**: SELL voucher lifecycle
7. **Exchange Rates**: 5-minute update cycle with caching
8. **Slashing Logic**: Economic decision tree for fraud response
9. **Integration**: SATNET GLOBAL within the SATNET mesh ecosystem

All components are **implemented, compiled, and ready for Phase 2 UI implementation**.

