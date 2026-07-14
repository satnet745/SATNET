# Release Evidence Bundle — 0.94-pre-32-ge8941bd6-dirty (Local Validation)

This bundle captures locally verified release-readiness evidence for commit `e8941bd653d15cb98bb9c22e983300fc1f9aaa56`.

## Scope
- Local Gradle gate validation on Windows
- Local release signing validation using the developer-local keystore configured in `release-signing.local.properties`
- Artifact checksum capture
- Local lint and unit-test output references

## Important limitation
This is **not** a production sign-off bundle yet.

The signing evidence in this folder uses a **local validation keystore** (`build/local-release-validation.jks`) rather than a controlled production signing environment. Per `doc/PRODUCTION_ACCEPTANCE_CRITERIA.md` and `DEPLOYMENT_TESTING_GUIDE.md`, the following are still required before public release:
- CI run URL and workflow status for the shipped commit
- 5-device physical validation matrix
- soak / recovery evidence
- reviewer / release-manager sign-off
- controlled-production keystore validation

## Local commands verified
```powershell
.\gradlew.bat :app:verifyReleaseSigningConfig --console=plain --no-daemon
.\gradlew.bat :app:assembleRelease :app:writeReleaseChecksums --console=plain --no-daemon
.\gradlew.bat :app:productionGate --console=plain --no-daemon
.\gradlew.bat :app:releaseReadinessGate --console=plain --no-daemon
```

