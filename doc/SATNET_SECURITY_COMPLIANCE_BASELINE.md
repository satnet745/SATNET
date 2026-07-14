# SATNET Security and Compliance Baseline

## Purpose

This document defines the minimum security and compliance posture required before SATNET can expand beyond regional pilot use.

---

## 1. Security Baseline

### 1.1 Client Security
- user wallet keys remain device-local
- seed material encrypted at rest
- no plaintext seed persistence in production builds
- heavy crypto never runs on the UI thread
- sensitive logs redacted or disabled in release builds
- screenshot and clipboard policies reviewed for wallet and voucher screens

### 1.2 Build and Release Security
- release signing keys stored in controlled signing infrastructure
- reproducible release inputs recorded
- artifact checksums archived
- build environment access logged and restricted
- emergency revocation and re-signing plan documented

### 1.3 Service Security
- provider credentials isolated per region
- short-lived service credentials preferred
- HSM or KMS-backed secret management for partner systems
- signed relay directory and trust-root distribution
- mTLS or equivalent service authentication for privileged integrations

### 1.4 Fraud and Abuse Controls
- voucher replay protection across sync boundaries
- anomalous issuance/redemption detection
- volume throttles by role and region
- emergency suspension controls for agents and partners
- verifier actions logged and attributable

---

## 2. Compliance Baseline

SATNET must support region-specific compliance profiles, not a single universal model.

### Supported Profiles
- `partner-led`
- `protocol-only`
- `hybrid`

### Per-Profile Controls
- onboarding requirements
- KYC/AML expectations
- sanctions screening responsibilities
- transaction and payout limits
- permitted role types
- evidence retention windows
- partner audit responsibilities

---

## 3. Regional Launch Minimums

Before any production-country launch:

- compliance profile selected
- partner contracts or operating basis documented
- sanctions and restricted-market review completed
- evidence retention and privacy requirements mapped
- incident notification obligations defined
- user support escalation path documented

---

## 4. Privacy Requirements

- collect only operational telemetry required for safety and reliability
- separate financial event telemetry from personal identity whenever possible
- honor jurisdictional retention and deletion requirements
- document all data flows for every regional cell
- avoid centralizing user financial data unless required by local partner obligations

---

## 5. Verifier and Dispute Controls

Verifier workflows must evolve beyond a local dashboard before wide production deployment:

- signed verifier decisions
- immutable case timeline
- evidence attachment support
- role-based authorization for escalations
- regional partner review hooks
- auditable outcome history

---

## 6. Acceptance Gate

SATNET is not eligible for multi-region production if any of the following are missing:

- secure release signing process
- region-aware compliance configuration
- incident rollback capability
- role-based operational ownership
- ANR/crash monitoring in live deployments
- documented abuse/fraud response playbooks

