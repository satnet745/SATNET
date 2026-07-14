# SATNET Operations Runbook

## Scope

This runbook covers live operations for SATNET regional cells.

---

## 1. Core Operational Signals

Monitor at minimum:

- app crash rate
- ANR rate
- wallet setup latency
- voucher issue latency
- voucher redeem latency
- merchant invoice latency
- QR scan success/failure
- relay directory reachability
- exchange-rate freshness
- settlement provider success/failure rate

---

## 2. Incident Severity Model

### Sev 1
- fund loss risk
- widespread wallet corruption
- critical security defect
- system-wide outage in active production region

### Sev 2
- elevated ANR/crash rate
- voucher redemption failures in one region
- merchant payment failure spike
- partner or liquidity provider outage with degraded service

### Sev 3
- partial feature degradation
- regional telemetry outage
- delayed audit export

---

## 3. Immediate Response Actions

### Crash / ANR Spike
1. confirm rollout cohort and region impact
2. pause staged rollout
3. compare with previous known-good release
4. disable affected feature or region where possible
5. prepare rollback if threshold exceeds release gate

### Settlement Provider Failure
1. switch to backup provider if approved
2. throttle affected flow if no safe fallback exists
3. notify regional ops owner
4. verify queued/offline operations remain safe

### Voucher Integrity Incident
1. halt new voucher issuance if replay or tampering is suspected
2. preserve logs and audit artifacts
3. notify fraud and compliance owners
4. disable affected cell if blast radius is unclear

---

## 4. Change Management

Every production release must record:

- commit SHA
- artifact checksum
- deployment stage
- target regions
- feature changes
- rollback artifact reference
- approving owners

---

## 5. On-Call Ownership

Each live region must maintain named coverage for:

- engineering on-call
- operations / SRE
- partner operations
- security escalation
- compliance escalation

---

## 6. Safe Disablement Controls

A region should be disableable without a full global shutdown.

Preferred controls:

- feature flags by role
- region-specific rollout hold
- remote kill switch for high-risk flows
- provider failover rules
- settlement-network downgrade where appropriate

---

## 7. Post-Incident Review

Every Sev 1 / Sev 2 incident should produce:

- timeline
- user impact summary
- root cause
- corrective actions
- release/process follow-ups
- owner and due date for each action

