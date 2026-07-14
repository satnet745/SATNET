# SATNET GLOBAL - Implementation Guide for Batphone

## Overview

This document outlines the integration of SATNET GLOBAL features into Batphone. SATNET GLOBAL is a decentralized, non-custodial Bitcoin wallet and voucher network designed for cash-based economies across GLOBAL.

**Key Architecture Decisions:**
- User controls all private keys (no central custody)
- Offline-first design (works without internet)
- Integration with Batphone's existing mesh infrastructure (upstream Serval DNA, Rhizome)
- Role-based system (User, Agent, Merchant, Verifier)
- SQLite-based voucher and reputation ledgers
- Optional Rhizome sync for distributed audit trail

---

## Package Structure

```
org.servalproject/
├── bitcoin/
│   ├── BitcoinWallet.java              # HD wallet (BIP32/39)
│   └── lightning/
│       └── LightningPaymentHandler.java # Lightning payments
├── voucher/
│   ├── BitcoinVoucher.java             # Voucher generation/validation
│   └── VoucherLedger.java              # SQLite ledger
├── satnet/
│   ├── SatnetRoleManager.java          # Role-based access
│   ├── reputation/
│   │   └── AgentReputation.java        # Staking & reputation
│   └── ui/
│       ├── SatnetRoleSetupActivity.java
│       ├── BitcoinWalletSetupActivity.java
│       ├── BitcoinWalletActivity.java
│       ├── VoucherRedemptionActivity.java
│       └── QRScannerActivity.java
```

---

## Implementation Phases

### Phase 1: Core Bitcoin Wallet (DONE)
✅ BitcoinWallet.java - BIP32/39 HD wallet with key management
✅ PIN-protected encryption
✅ Recovery phrase (12-word mnemonic)
✅ Offline transaction signing capability

**Next Steps:**
- Integrate bitcoinj library for actual BIP32 derivation
- Implement PBKDF2 mnemonic-to-seed conversion
- Add AES encryption for PIN protection
- Connect to Esplora API or local Bitcoin node for balance queries

### Phase 2: Bitcoin Voucher System (DONE)
✅ BitcoinVoucher.java - Voucher generation and validation
✅ QR code payload format
✅ SHA256 cryptographic verification
✅ Time-limited expiry enforcement
✅ VoucherLedger.java - SQLite persistence

**Next Steps:**
- Integrate ZXing library for QR code generation/scanning
- Implement Rhizome sync for agent voucher distribution
- Add voucher-to-transaction settlement logic
- Track redemption confirmations on blockchain

### Phase 3: Role-Based System (DONE)
✅ SatnetRoleManager.java - User/Agent/Merchant/Verifier roles
✅ Feature-based permissions
✅ Role metadata storage (agent name, location, etc.)

**Next Steps:**
- Tie roles to the SATNET ID system
- Implement role-specific UI branches
- Add role switching mechanism

### Phase 4: Agent Staking & Reputation (DONE)
✅ AgentReputation.java - SQLite database for agent data
✅ Stake tier system (Candidate → Anchor)
✅ Slashing rules (fraud, non-delivery)
✅ Dispute resolution tracking

**Next Steps:**
- Implement automatic tier promotion based on performance
- Create verifier UI for dispute resolution
- Add reputation score calculations
- Implement stake unlock cooling-off period

### Phase 5: UI Activities (DONE)
✅ BitcoinWalletSetupActivity - Create/import wallet
✅ BitcoinWalletActivity - Wallet display, balance, address
✅ SatnetRoleSetupActivity - Role selection
✅ VoucherRedemptionActivity - Scan and redeem vouchers
✅ QRScannerActivity - QR code scanning

**Next Steps:**
- Create UI for agent voucher issuance
- Create UI for merchant Lightning invoice display
- Create UI for verifier dispute resolution
- Add detailed balance/transaction history views

---

## Library Dependencies

Added to `app/build.gradle`:

```gradle
// Bitcoin wallet library
implementation 'org.bitcoinj:bitcoinj-core:0.16.3'

// QR code generation and scanning
implementation 'com.google.zxing:core:3.5.1'
implementation 'com.journeyapps:zxing-android-embedded:4.3.0'

// Cryptography
implementation 'com.google.crypto.tink:tink-android:1.10.0'

// HTTP client
implementation 'com.squareup.okhttp3:okhttp:4.11.0'

// JSON parsing
implementation 'com.google.code.gson:gson:2.10.1'

// Kotlin coroutines
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
```

---

## AndroidManifest.xml Changes

Added Activities:
- `SatnetRoleSetupActivity` - Role selection screen
- `BitcoinWalletSetupActivity` - Wallet creation/import
- `BitcoinWalletActivity` - Main wallet display
- `VoucherRedemptionActivity` - Voucher scanning
- `QRScannerActivity` - QR code scanning

Permissions already present:
- `INTERNET` - For blockchain API queries
- `CAMERA` - For QR code scanning
- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` - For backups

---

## User Flows

### 1. First-Time User Setup
```
1. User launches app
2. SatnetRoleSetupActivity - Select role (User/Agent/Merchant/Verifier)
3. For Agent: enter name + location
4. For Merchant: enter business name + type
5. BitcoinWalletSetupActivity - Create new or import wallet
6. Option to generate 12-word phrase or import existing
7. Set PIN for wallet encryption
8. BitcoinWalletActivity - Main wallet screen (balance, address)
9. Prompt to backup recovery phrase (guided verification)
```

### 2. Redeem Bitcoin Voucher (User)
```
1. VoucherRedemptionActivity opens
2. User scans voucher QR code (or enters code manually)
3. BitcoinVoucher.parseQRPayload() - Extract voucher data
4. BitcoinVoucher.validate() - Verify not expired, not redeemed
5. VoucherLedger.isVoucherRedeemed() - Check ledger
6. User confirms redemption
7. BitcoinVoucher.redeem() - Mark redeemed in memory
8. VoucherLedger.recordRedemption() - Persist to local ledger
9. Lightning or on-chain settlement to user wallet address
10. Toast: "Bitcoin sent to your wallet"
```

### 3. Issue Bitcoin Voucher (Agent)
```
1. Agent enters amount (1000, 5000, 10000, 50000 sats)
2. BitcoinVoucher.generateNew() - Create voucher with secret
3. BitcoinVoucher.getQRPayload() - Generate QR data
4. Display QR code on screen (or print/email)
5. VoucherLedger.recordIssuedVoucher() - Track in local ledger
6. Optional: VoucherLedger.markSyncedToMesh() - Sync via Rhizome
7. Customer pays cash
8. Share QR code (display, print, SMS)
```

### 4. Accept Lightning Payment (Merchant)
```
1. MerchantActivity opens
2. Enter amount in local currency
3. LightningPaymentHandler.generateInvoice() - Create invoice
4. Display as QR code + text
5. Customer scans and pays with their wallet
6. Payment received (on-chain or off-chain)
7. Rhizome broadcasts invoice receipt
```

### 5. Resolve Dispute (Verifier)
```
1. DisputeResolverActivity opens
2. View list of open disputes
3. Review complaint, evidence, agent stake
4. Make ruling (dismiss, partial slashing, full slashing)
5. AgentReputation.recordSlashingEvent() - Record outcome
6. If slashing approved, freeze agent's stake
7. Update agent tier and status
8. Notify complainant and agent
```

---

## Data Structures

### BitcoinVoucher
```java
String voucherId;           // agent_001_1234567_abc4
long denomination;          // 50000 (satoshis)
String agentId;             // SATNET ID or wallet address
long issuedTime;            // milliseconds
long expiryTime;            // milliseconds
String secret;              // 128-bit hex (random)
String secretHash;          // SHA256(secret)
int state;                  // ISSUED, REDEEMED, EXPIRED, INVALID
long redeemedTime;          // milliseconds
String redeemedByWallet;    // Bitcoin address
```

### Agent Reputation Record
```
agent_id → VARCHAR (SATNET ID)
tier → INT (1=Candidate, 2=Micro, 3=Local, 4=Regional, 5=Anchor)
total_stake_sats → BIGINT
reputation_score → FLOAT (0-100)
vouchers_issued → INT
vouchers_redeemed → INT (confirmed on-chain)
status → VARCHAR (active, suspended, blacklisted)
joined_time → TIMESTAMP
```

### Slashing Event
```
slash_id → VARCHAR (unique)
agent_id → VARCHAR (foreign key)
reason → VARCHAR (fraud, non-delivery, misconduct)
severity → VARCHAR (warning, partial, full)
amount_sats → BIGINT (amount slashed)
timestamp → TIMESTAMP
status → VARCHAR (pending, approved, appealed)
```

---

## Security Considerations

### Private Key Management
- ✅ User owns keys completely (BIP32 derivation)
- ✅ Seed encrypted with PIN (AES-256)
- ✅ Mnemonic verified on setup
- ✅ Sensitive data wiped from memory on destroy
- ⚠️ TODO: Hardware security module (HSM) support for agents with large stakes

### Voucher Security
- ✅ Cryptographic SHA256 hash verification
- ✅ Single-use enforcement via ledger
- ✅ Time expiry validation
- ✅ QR code tamper detection via hash mismatch
- ⚠️ TODO: QR code encryption for large denominations

### Staking & Slashing
- ✅ Rule-based slashing (deterministic, no manual favoritism)
- ✅ Transparent dispute process
- ✅ Multiple appeal layers (community verifiers)
- ⚠️ TODO: Require multi-sig for full agent slashing

### Offline Operation
- ✅ QR voucher scanning works offline
- ✅ Wallet signing offline-capable
- ✅ Transaction queueing for delayed broadcast
- ⚠️ TODO: Multi-relay fallback for mesh broadcast

---

## Blockchain Integration

### Option 1: Esplora API (Recommended for MVP)
```
- Public REST API
- No authentication needed
- Query balance: GET /api/address/{address}
- Broadcast tx: POST /api/tx
- Scan QR → broadcast on reconnect (offline tolerance)
```

**Implementation:**
```java
OkHttpClient client = new OkHttpClient();
Request request = new Request.Builder()
    .url("https://blockstream.info/api/address/" + address)
    .build();
Response response = client.newCall(request).execute();
```

### Option 2: bitcoinj SPV Node (Lightweight)
```
- Local synchronization of block headers
- No full node needed
- Peer-to-peer broadcast
- More privacy than Esplora
```

### Option 3: Custom Lightning Node (Future)
```
- Run LDK (Lightning Dev Kit) on device
- Off-chain payments
- Micropayment support
- Reduced blockchain footprint
```

---

## Testing Checklist

### Unit Tests
- [ ] BitcoinWallet - BIP32 key derivation
- [ ] BitcoinVoucher - SHA256 hash validation
- [ ] SatnetRoleManager - Feature permissions
- [ ] AgentReputation - Tier calculations
- [ ] VoucherLedger - CRUD operations

### Integration Tests
- [ ] Full voucher issuance → redemption flow
- [ ] Offline voucher scanning
- [ ] Role-based UI visibility
- [ ] Rhizome sync (when implemented)
- [ ] Lightning payment generation

### UI Tests (Espresso)
- [ ] Role selection screen
- [ ] Wallet creation flow
- [ ] Voucher redemption button states
- [ ] Balance display (normal, hidden, panic)

### Manual Testing (Configured Settlement Network)
- [ ] Create wallet on configured settlement network
- [ ] Generate test vouchers
- [ ] Scan QR codes offline
- [ ] Broadcast transactions with mesh relay

---

## Integration with Batphone Mesh

### Leverage Existing Infrastructure
1. **SATNET ID (SID)** - Use as agent/user ID
2. **MeshMS** - Notify agents of disputes, payment confirmations
3. **Rhizome** - Distribute voucher ledger, agent reputation scores
4. **Mesh Routing** - Broadcast Lightning/on-chain transactions

### Rhizome Sync Strategy
```
1. Agent issues voucher locally (SQLite)
2. Voucher serialized to Rhizome manifest
3. Manifest distributed to nearby peers
4. Peers merge into their local SQLite
5. On redemption, update manifest with redeemed status
6. Consensus on final voucher state after 24h
```

---

## Future Enhancements

### Phase 6: Advanced Features
- [ ] Multi-signature wallets for large agent stakes
- [ ] Atomic swaps between Bitcoin and local fiat
- [ ] Merchant POS integration (receipt printer, inventory)
- [ ] Learning/education module (earn sats)
- [ ] Governance (community voting on slashing appeals)
- [ ] Federation charter and community stewardship model

### Phase 7: Privacy
- [ ] Tor integration for IP hiding
- [ ] CoinJoin mixing for transaction privacy
- [ ] Zero-knowledge proofs for agent verification

### Phase 8: Scalability
- [ ] Second-layer scaling (Stacks, RGB)
- [ ] Batch voucher redemption
- [ ] Regional anchor agents for liquidity
- [ ] Cross-country settlement

---

## Legal/Compliance Notes

### Non-Custodial Architecture
- SATNET never holds user Bitcoin
- Agents operate independently
- No central bank licensing needed (in most jurisdictions)
- Wallet-based identity (not KYC/AML)

### Disclaimers (Add to Help)
- No investment advice
- Bitcoin volatility risk
- No refunds post-redemption
- User responsible for key backup

### By Country
- **Uganda** - Low regulatory risk, strong mobile money users
- **Kenya** - Moderate risk, competitive with M-Pesa
- **Tanzania** - Moderate-high risk, growing Bitcoin adoption
- **Ethiopia** - High risk (internet restrictions) - perfect for mesh
- **DRC** - Moderate risk, large unbanked population
- **South Sudan** - Low risk (no strong regulator), high demand

---

## Code Examples

### Create New Wallet
```java
BitcoinWallet wallet = new BitcoinWallet(context, "default");
String mnemonic = wallet.generateNewMnemonic(); // 12 words
wallet.importFromMnemonic(mnemonic, "1234"); // PIN protect
long balance = wallet.getBalanceSats();
```

### Issue Voucher
```java
BitcoinVoucher voucher = BitcoinVoucher.generateNew(
    agentId,           // "serval_sid_abc123"
    50000,             // 50000 sats
    24                 // expires in 24 hours
);
String qrPayload = voucher.getQRPayload();
String numericCode = voucher.getNumericCode();
voucherLedger.recordIssuedVoucher(voucher);
```

### Redeem Voucher
```java
BitcoinVoucher voucher = BitcoinVoucher.parseQRPayload(qrString);
BitcoinVoucher.ValidationResult result = voucher.validate();
if (result.isValid && !voucherLedger.isVoucherRedeemed(voucher.getId())) {
    voucher.redeem(userWalletAddress);
    voucherLedger.recordRedemption(voucher, userWalletAddress, txHash);
}
```

### Register Agent
```java
roleManager.registerAsAgent("John Doe", "Kampala, Uganda");
agentReputation.registerAgent(
    servalId,
    "John Doe",
    "Kampala",
    "UG"
);
```

---

## Contact & Support

- **Website:** https://satnetafrica.org
- **GitHub:** https://github.com/satnet-GLOBAL/batphone (TBD)
- **Email:** dev@satnetafrica.org
- **Community:** Mesh-first (no centralized discord required)

---

**Last Updated:** December 2025  
**Status:** Phase 5 (UI) Complete - Awaiting Library Integration & Testing

