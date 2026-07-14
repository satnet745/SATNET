# SATNET Global Deployment Plan

## Purpose

This document turns SATNET from a feature prototype into an executable global rollout program.

It complements:

- `TECHNICAL_ARCHITECTURE.md`
- `doc/PRODUCTION_ACCEPTANCE_CRITERIA.md`
- `doc/RELEASE_RUNBOOK.md`
- `DEPLOYMENT_TESTING_GUIDE.md`

---

## 1. Rollout Strategy

SATNET should launch through controlled stages, never as an immediate worldwide release.

### Stage 0 - Internal Lab
- emulator and device matrix validation
- synthetic vouchers and testnet-only settlement
- chaos and network-interruption testing
- crash/ANR baseline collection

### Stage 1 - Pilot Region
- one region only
- invited users, agents, merchants, and verifiers
- capped transaction volumes
- named partner operations owner
- manual incident response

### Stage 2 - Country Beta
- one or two production countries
- limited mainnet liquidity corridors
- support desk and incident escalation live
- regulated partner integration in place
- daily operational review cadence

### Stage 3 - Regional Production
- multiple countries in one regional cell
- redundant providers for rates and settlement
- formal on-call rotation
- weekly risk and fraud review
- signed release promotion process

### Stage 4 - Global Production
- multiple regional cells active
- region-aware kill switches
- capacity planning and failover drills complete
- quarterly security and compliance review cadence

---

## 2. Country Readiness Checklist

A country is not eligible for launch until all of the following are complete:

- legal and regulatory model selected
- local partner and operational owner assigned
- user support language coverage available
- target device matrix completed
- local currency rate source approved
- incident and fraud escalation contacts documented
- payment corridor and liquidity model approved
- field training for agents and verifiers completed

---

## 3. Global Scaling Principles

- ship one codebase with region-specific configuration
- use staged rollout percentages for every release
- isolate provider failures by region
- keep custody local to the user wallet
- minimize global dependencies in critical paths
- prefer additive rollout over big-bang launches

---

## 4. Release Promotion Gates

A SATNET release can move from one stage to the next only if:

- `:app:productionGate` passes
- device matrix is green for low-end and mid-tier Android hardware
- no critical wallet, voucher, QR, or ANR regressions exist
- support playbooks are updated
- partner readiness sign-off is recorded
- rollback artifact is verified and archived

---

## 5. Operational SLO Targets

Initial recommended targets for regional production:

- crash-free sessions: >= 99.5%
- ANR rate: <= 0.25%
- wallet setup p95: <= 5 seconds on target devices
- voucher validation p95: <= 2 seconds
- merchant invoice generation p95: <= 2 seconds
- relay directory availability: >= 99.9%
- settlement provider availability by region: >= 99.5%

---

## 6. Rollback Policy

Immediate rollback is required when any of these occur:

- critical crash or ANR spike
- wallet data loss or corruption
- voucher replay or settlement inconsistency
- security defect with active exploit path
- partner integration defect causing fund or accounting mismatch

Rollback actions:

1. halt staged rollout
2. disable affected region or feature flag
3. restore previous approved artifact
4. open incident with severity and owner
5. publish operator notice and user guidance

---

## 7. Engineering Deliverables Before Stage 3+

- structured telemetry for wallet, voucher, invoice, and verifier flows
- region-aware service configuration
- provider failover logic
- signed relay directory documents
- dispute evidence bundle format
- formal capacity plan for global relay and rate services
- partner operations dashboard
- automated release promotion checklist in CI

---

## 8. Ownership Model

Every live region must have named owners for:

- engineering
- site reliability / operations
- security
- compliance / partner operations
- support and escalation

No region should launch without explicit ownership coverage.

