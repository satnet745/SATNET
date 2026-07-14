# Production Acceptance Criteria

This file is the concise production gate for public release.

## Hard Requirements

- CI pipeline defined in `.github/workflows/android-ci.yml` is green on the release commit:
  - `validate-wrapper`
  - `quality-gates`
  - `release-sanity`
- `:app:productionGate` passes from a clean checkout with release signing configured.
- `:app:releaseReadinessGate` passes for the exact signed release candidate being shipped.
- Lint report is reviewed and approved with no new critical regressions.
- No critical or high-severity unresolved defects for core user journeys.
- `doc/DEVICE_MATRIX_TEMPLATE.md` completed with pass results on at least 5 physical devices.
- Security review complete for key management, encrypted storage, and sensitive logs.
- Any unsigned release artifact is explicitly marked as a local non-shipping dry run and is never published.

## Core User Journeys (Must Pass)

- First launch and onboarding.
- Identity/profile creation and persistence.
- Peer discovery and connect/disconnect behavior.
- Voice call setup, active call stability, and termination.
- Messaging send/receive with intermittent connectivity.
- File sharing send/receive verification.
- SATNET wallet/voucher/reputation flows (if enabled in build).

## Operational Readiness

- Documented rollback path for the release artifact.
- Release notes updated and versioned.
- Known limitations listed in user-facing documentation.
- Monitoring and support escalation contacts confirmed.
- SATNET rollout stage, security baseline, and regional launch scope reviewed when SATNET features are enabled:
  - `TECHNICAL_ARCHITECTURE.md`
  - `doc/SATNET_COMMUNITY_SUSTAINABILITY_MODEL.md`
  - `doc/SATNET_GLOBAL_DEPLOYMENT_PLAN.md`
  - `doc/SATNET_OPERATIONS_RUNBOOK.md`
  - `doc/SATNET_SECURITY_COMPLIANCE_BASELINE.md`

## Minimum Evidence Bundle

- CI run URL and commit SHA.
- Reference to `.github/workflows/android-ci.yml` job results for the shipped commit.
- Lint report and unit test XML outputs.
- Release APK generation log and checksum output (`app/build/outputs/checksums/release/SHA256SUMS.txt`).
- Device matrix results from `doc/DEVICE_MATRIX_TEMPLATE.md` and soak-test notes from `doc/SOAK_TEST_CHECKLIST.md`.
- Release sign-off entry from `DEPLOYMENT_TESTING_GUIDE.md`.

