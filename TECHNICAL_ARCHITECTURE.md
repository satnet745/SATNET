# SATNET Technical Architecture for Global Real-World Deployment

## 1. Objective

SATNET must evolve from a regional prototype into a globally deployable, safety-critical, offline-capable financial communications platform. The architecture must preserve the original product principles:

- self-custody first
- offline-first operation
- censorship resistance
- no central custody of user funds
- regional compliance through partners, not through a single global custodian

This document defines the target technical shape for that evolution.

---

## 2. System Model

SATNET is not one server and one app. It is a distributed system with five cooperating planes:

1. **Client plane**
   - Android app for wallet, vouchers, merchant payments, verifier actions, and SATNET mesh communications.
2. **Relay and distribution plane**
   - package distribution, update mirroring, relay discovery, regional bootstrap metadata, and fallback routing.
3. **Settlement and liquidity plane**
   - Bitcoin/Lightning connectivity, liquidity providers, watchtowers, routing partners, treasury controls, and rate services.
4. **Trust and compliance plane**
   - agent onboarding, verifier workflows, sanctions screening where required, dispute evidence, jurisdictional feature gating.
5. **Operations and observability plane**
   - staged rollout control, incident response, telemetry, crash/ANR monitoring, kill switches, and release governance.

---

## 3. Target Global Topology

### 3.1 Regional Cells

SATNET should deploy as **regional cells**, not as one globally centralized backend.

Each cell contains:

- regional relay directory service
- exchange-rate service with local currency support
- compliance and partner integration adapters
- Lightning liquidity endpoints or federation partners
- support and incident ownership for that geography

Recommended starting cell boundaries:

- East GLOBAL
- West GLOBAL
- Southern GLOBAL
- MENA
- South Asia
- Southeast Asia
- Latin America

### 3.2 Global Control Layer

A thin global control layer should exist only for:

- release coordination
- staged feature rollout
- kill switches
- public key and trust-root distribution
- aggregate health dashboards

It must **not** be required for day-to-day wallet custody or voucher redemption.

---

## 4. Client Architecture

### 4.1 Client Responsibilities

The Android client should own:

- wallet key generation and recovery
- encrypted seed storage
- local voucher ledger
- merchant invoice rendering
- QR encode/decode
- offline queueing
- local dispute evidence cache
- mesh-based peer communications

### 4.2 Client Safety Requirements

For real-world deployment, all heavyweight actions must remain off the UI thread:

- mnemonic generation
- wallet import and decrypt
- QR encode/decode
- voucher validation and redemption bookkeeping
- invoice generation and merchant rendering
- verifier ledger scans

The app must assume low-end Android devices, thermal throttling, weak CPUs, and intermittent storage I/O.

### 4.3 Mobile Release Controls

Every production build must expose deployment controls through build-time configuration:

- deployment stage
- settlement network (`testnet`, `signet`, `mainnet`)
- relay directory endpoint
- exchange-rate endpoint
- compliance profile
- remote kill-switch enablement

These are now scaffolded in `app/build.gradle` and surfaced via `FeatureFlags`.

---

## 5. Wallet and Settlement Architecture

### 5.1 Custody Model

SATNET should remain **non-custodial at the user layer**.

Users hold their own keys.

Partners may provide:

- liquidity
- exchange rate feeds
- local cash conversion
- Lightning routing

but must not silently convert SATNET into a central custodial wallet.

### 5.2 Global Settlement Modes

Support three settlement modes:

1. **pilot mode**
   - testnet/signet only
   - capped volumes
   - operational whitelisting
2. **country beta mode**
   - limited mainnet corridors
   - partner-gated geography
   - small-amount limits
3. **global mode**
   - regional liquidity pools
   - strict incident and compliance controls
   - full monitoring and rollback readiness

### 5.3 Lightning Reliability Requirements

Before global launch, the settlement layer should add:

- multi-provider invoice routing fallback
- route health scoring
- watchtower support
- channel and inbound liquidity monitoring
- fee policy management by region
- safe fallback to on-chain payout where Lightning repeatedly fails

---

## 6. Voucher Network Architecture

### 6.1 Voucher Lifecycle

Voucher lifecycle should be modeled as:

`issued -> presented -> validated -> redeemed -> settled -> audited`

The current local ledger is a useful device-side cache but global deployment needs:

- regional audit export pipelines
- tamper-evident settlement records
- replay protection across sync boundaries
- dispute evidence retention with jurisdiction-aware retention windows

### 6.2 Agent Network Scaling

Agents should be onboarded through regional partner programs with:

- identity verification appropriate to local regulation
- explicit tiering and limits
- behavior-based risk scoring
- dispute and slashing rules
- emergency suspension controls

### 6.3 Verifier Network Scaling

Verifier actions should become a structured subsystem with:

- multi-verifier approvals for sensitive cases
- signed verifier outcomes
- evidence bundles
- immutable case timelines
- escalation to regional partner ops

---

## 7. Relay, Distribution, and Anti-Censorship Architecture

### 7.1 Distribution Strategy

Global deployment should support:

- Play Store distribution where allowed
- direct APK sideload distribution
- mirrored download endpoints
- signed release manifests
- regional CDN plus origin fallback

### 7.2 Relay Directory Model

Relay discovery should be regionalized and cacheable.

Recommended pattern:

- signed relay directory documents
- multiple bootstrap URLs per region
- hardcoded emergency fallback roots in the app
- local caching with expiry windows
- optional mesh-only operation when internet discovery is unavailable

---

## 8. Security Architecture

### 8.1 Key Security

- user keys remain local and encrypted at rest
- release signing keys must be stored in controlled signing infrastructure
- partner and service keys must live in HSM-backed or cloud KMS-backed systems
- verifier/admin actions should be signed and auditable

### 8.2 Backend Security

- zero-trust service-to-service auth
- regional secret isolation
- per-region blast-radius containment
- strict audit logs for compliance actions
- mandatory key rotation and short-lived credentials

### 8.3 Abuse and Fraud Controls

- device abuse scoring
- voucher replay detection
- merchant invoice fraud detection
- anomalous agent activity alerts
- geographic and volume throttles
- emergency region disablement via kill switch

---

## 9. Compliance Architecture

SATNET should not assume a single global legal model.

Instead, use a **compliance profile per deployment region**:

- `partner-led`
- `protocol-only`
- `hybrid`

Each profile controls:

- onboarding requirements
- permitted role types
- payout limits
- KYC/AML expectations
- sanctions screening obligations
- evidence retention windows

The build scaffolding now includes a compliance profile field to support this rollout model.

---

## 9.5 Anti-Rent-Seeking Architecture Rule

SATNET should be engineered so sustainability does not depend on turning protocol freedom into a toll booth.

Architecture implications:

- no mandatory protocol fee should be required for wallet creation, voucher redemption, or basic peer communications
- users must be able to keep self-custody even if a hosted partner or regional operator disappears
- optional hosted services may exist, but they must remain replaceable
- federation and partner revenue should come from support, hosting, compliance adapters, and operational tooling rather than transaction rent extraction
- software must remain forkable and interoperable enough for communities to exit abusive governance

This rule keeps the implementation aligned with both open-source sustainability and cypherpunk values.

---

## 10. Operations and Observability

### 10.1 Required Telemetry

For global launch, capture at minimum:

- crash-free sessions
- ANR rate
- wallet setup latency
- voucher issue latency
- voucher redeem latency
- invoice generation latency
- QR scan success/failure rate
- relay reachability by region
- settlement success/failure rate
- partner integration error rate

### 10.2 Required Operational Controls

- staged rollout percentages
- region-level feature enable/disable
- provider failover
- remote kill switch
- safe rollback to prior release artifact
- incident playbooks by severity

---

## 11. Deployment Stages

### Stage A: Pilot

- limited region
- limited users and agents
- testnet/signet preferred
- manual operational approval

### Stage B: Country Beta

- one or two production countries
- bounded agent and merchant counts
- capped mainnet volume
- 24/7 incident rotation

### Stage C: Regional Production

- multi-country within one cell
- partner-backed compliance
- provider redundancy
- formal SLAs

### Stage D: Global Production

- multiple regional cells
- per-region kill switches
- global release governance
- independent incident ownership in each region

---

## 12. Engineering Priorities Before Global Launch

1. finish async hardening of all user-facing heavy flows
2. add structured telemetry and ANR/crash reporting
3. introduce environment-aware service configuration
4. implement partner-backed rate and liquidity providers
5. add replay-resistant voucher sync and audit export
6. add structured verifier case management
7. formalize release governance and rollout runbooks
8. complete security review and compliance mapping per region

---

## 13. Definition of “Global Ready”

SATNET is only globally ready when:

- user funds remain non-custodial
- region-specific compliance can be enforced without forking the app
- release rollout can be staged and reversed quickly
- no core role flow causes ANRs on low-end devices
- relay, rate, and settlement dependencies are regionally redundant
- verifier and dispute workflows are auditable
- operational ownership exists for every live region

Until then, SATNET should be treated as a staged rollout platform, not a single-step global launch artifact.

