# Release Runbook
## Preconditions
- Release branch is up to date with required approvals.
- No unresolved blocker defects.
- Production acceptance criteria reviewed in `doc/PRODUCTION_ACCEPTANCE_CRITERIA.md`.
- When SATNET features are enabled, review:
  - `TECHNICAL_ARCHITECTURE.md`
  - `doc/SATNET_COMMUNITY_SUSTAINABILITY_MODEL.md`
  - `doc/SATNET_GLOBAL_DEPLOYMENT_PLAN.md`
  - `doc/SATNET_OPERATIONS_RUNBOOK.md`
  - `doc/SATNET_SECURITY_COMPLIANCE_BASELINE.md`
## Step 1: Local Gate
```powershell
.\gradlew.bat :app:productionGate --no-daemon --stacktrace
```

This gate is now a signed-release gate. For public distribution, do not bypass signing.

For a release candidate that must be signed and evidenced, run the strict gate instead:

```powershell
.\gradlew.bat :app:releaseReadinessGate --no-daemon --stacktrace
```

For local developer validation only, an unsigned release dry run remains available:

```powershell
.\gradlew.bat --project-prop allow.unsigned.release=true :app:assembleRelease --no-daemon --stacktrace
```

Expected outputs:
- `app/build/reports/lint-results-debug.html`
- `app/build/outputs/apk/release/*.apk`
- `app/build/outputs/checksums/release/SHA256SUMS.txt`

If local unit tests are constrained by Windows paging-file or low-memory limits, the build now supports explicit test throttling:

```powershell
.\gradlew.bat --project-prop test.maxParallelForks=1 --project-prop test.maxHeap=512m :app:testDebugUnitTest --no-daemon --stacktrace
```
## Step 2: CI Gate
- Push release candidate commit.
- Confirm all jobs in `.github/workflows/android-ci.yml` are green.
- Archive CI report artifacts.
- Start a release evidence bundle from `doc/release-evidence-template/` and place CI links/exports under `ci/`.
## Step 3: Device Validation
- Fill out `doc/DEVICE_MATRIX_TEMPLATE.md`.
- Execute the device matrix from `DEPLOYMENT_TESTING_GUIDE.md`.
- Confirm no critical regressions in core flows.

For the SATNET connected smoke subset on a local device/emulator, use:

```powershell
.\scripts\run-connected-smoke.ps1
```
## Step 4: Sign and Package
- Use controlled signing environment.
- Verify signature and package metadata.
- Record artifact SHA256 checksums.
- Treat any `--project-prop allow.unsigned.release=true` artifact as non-shipping and discard it before public release.
- Do not commit signing passwords or keystore secrets; pass them via `-Prelease.key.*` properties or `BATPHONE_RELEASE_*` environment variables.
- Put signed-release checklist evidence under `doc/release-evidence-template/signing/`.
## Step 5: Launch and Verify
- Publish gradually when possible.
- Verify install and first-run from production artifact.
- Monitor crash/ANR and user feedback channels.
- Attach `doc/SOAK_TEST_CHECKLIST.md` results before final GO sign-off.
- Store final artifact inventory and checksum references under `doc/release-evidence-template/artifacts/`.
## Step 6: Rollback Trigger
Rollback immediately when any of the following occurs:
- Critical crash/ANR spike.
- Security defect with active exploit path.
- Data loss/corruption in core flows.
Rollback actions:
- Halt rollout.
- Restore previous known-good artifact.
- Open incident with owner and ETA.
