# Deployment Testing Guide

This guide defines the minimum evidence required before any public production rollout.

## 1) Release Decision Policy

- A release is **GO** only if all critical gates in this document pass.
- Any failed critical gate is an automatic **NO-GO**.
- Waivers are temporary and must include owner, rationale, risk, and expiry date.

## 2) Critical Gates (Must Pass)

### Gate A: Build and CI Integrity

- `android-ci` workflow passes on the release commit.
- `validate-wrapper`, `quality-gates`, and `release-sanity` jobs are green.
- No local-only patches are required to build from clean checkout.

Evidence:
- CI run URL and commit SHA.

### Gate B: Static Quality

- Lint report for debug variant is generated in CI and reviewed.
- No new critical lint regressions are introduced versus the current baseline debt.
- Unit tests pass with zero failures.
- Signed release APK assembles successfully.
- `:app:productionGate` and `:app:releaseReadinessGate` both pass for the exact public artifact.

Evidence:
- `app/build/reports/lint-results-debug.html` plus reviewer sign-off notes.
- `app/build/test-results/testDebugUnitTest` XML reports.
- `app/build/outputs/checksums/release/SHA256SUMS.txt` for signed release candidates.
- Collected release bundle material staged under `doc/release-evidence-template/`.

### Gate C: Device Verification

- Validate on at least 5 physical devices (low-end, mid-range, high-end, different Android versions).
- Test matrix includes install, upgrade, and uninstall/reinstall flows.
- Core flows (calling, messaging, file sharing, wallet/voucher flows if enabled) pass.

Evidence:
- Completed `doc/DEVICE_MATRIX_TEMPLATE.md` with OS version, chipset, result, owner, and date.
- Connected SATNET smoke output from `scripts/run-connected-smoke.ps1` where applicable.

### Gate D: Reliability and Recovery

- 24-hour soak test with no critical crash loops.
- Airplane-mode/network-flap tests confirm graceful recovery.
- App restart and process-death recovery validated for active user sessions.

Evidence:
- Completed `doc/SOAK_TEST_CHECKLIST.md` plus soak logs, crash/ANR counts, and reproducible failure notes (if any).

### Gate E: Security and Privacy

- Release signing process is documented and repeatable.
- Secrets are not present in source control.
- Security-sensitive paths (key storage, encryption, PIN handling) are regression-tested.

Evidence:
- Signed release checklist.
- Security regression test notes.
- Checksum evidence from `app/build/outputs/checksums/release/SHA256SUMS.txt`.

## 3) Recommended (Non-blocking) Gates

- Basic performance profile on representative devices (startup time, memory, battery trend).
- Accessibility smoke checks for critical screens.
- Rollback drill for release management.

## 4) Local Verification Commands

Use from repo root.

```powershell
.\gradlew.bat :app:lintReportDebug :app:testDebugUnitTest :app:assembleRelease --no-daemon --stacktrace
```

or run the consolidated gate:

```powershell
.\gradlew.bat :app:productionGate --no-daemon --stacktrace
```

For local developer validation only, you may build an unsigned non-shipping artifact with:

```powershell
.\gradlew.bat --project-prop allow.unsigned.release=true :app:assembleRelease --no-daemon --stacktrace
```

For a signed release candidate, prefer the strict gate:

```powershell
.\gradlew.bat :app:releaseReadinessGate --no-daemon --stacktrace
```

If this Windows machine is constrained by paging-file limits during unit tests, reduce test concurrency explicitly:

```powershell
.\gradlew.bat --project-prop test.maxParallelForks=1 --project-prop test.maxHeap=512m :app:testDebugUnitTest --no-daemon --stacktrace
```

## 5) Sign-off Template

- Release version:
- Commit SHA:
- Release manager:
- Date:
- Gate A status:
- Gate B status:
- Gate C status:
- Gate D status:
- Gate E status:
- Open risks:
- Decision (GO / NO-GO):

## 6) Helpful Local Helpers

- Connected SATNET scanner / voucher smoke:

```powershell
.\scripts\run-connected-smoke.ps1
```

- Release evidence bundle starter:
  - `doc/release-evidence-template/`

