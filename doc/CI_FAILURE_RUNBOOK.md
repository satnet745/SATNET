# CI Failure Runbook
Use this runbook to triage and fix failures in `.github/workflows/android-ci.yml`.
## 1) Identify Failing Job
- `validate-wrapper`: wrapper tamper/corruption or checksum mismatch.
- `quality-gates`: lint, unit test, or debug assembly failure.
- `release-sanity`: release assembly failure.
## 2) Reproduce Locally
```powershell
.\gradlew.bat :app:lintReportDebug :app:testDebugUnitTest :app:assembleDebug --no-daemon --stacktrace
```
If release-only failure:
```powershell
.\gradlew.bat :app:assembleRelease --no-daemon --stacktrace
```
## 3) Common Fix Paths
- Wrapper validation: regenerate wrapper from trusted environment and review diff.
- Lint failures: fix issues in changed files first, then rerun lint.
- Unit test failures: isolate deterministic failure and add regression test coverage.
- Release failures: verify signing config behavior and variant-specific resources.
## 4) Evidence to Attach to PR
- Failing CI URL.
- Local reproduction command output.
- Root cause summary.
- Patch link and proof of green rerun.
## 5) Escalation
Escalate immediately if failure indicates:
- Potential supply-chain compromise.
- Security-sensitive regression.
- Release artifact integrity problem.
