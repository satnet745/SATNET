# Artifact Inventory

- Release version: `0.94-pre-32-ge8941bd6-dirty`
- Commit SHA: `e8941bd653d15cb98bb9c22e983300fc1f9aaa56`
- Release APK path: `app/build/outputs/apk/release/app-release.apk`
- Unsigned or signed: **Signed** (verified locally with `apksigner verify --verbose --print-certs`)
- SHA256 checksum path: `app/build/outputs/checksums/release/SHA256SUMS.txt`
- SHA256 checksum value: `277f3488cafcbe6f4e4267bbce0c2f22e483a8ec5003ea48997a38bec323860f`
- Lint report path: `app/build/reports/lint-results-debug.html`
- Lint summary: `0 errors, 19 warnings (and 9 errors, 460 warnings filtered by baseline lint-baseline.xml)` from `app/build/reports/lint-results-debug.txt`
- Unit test XML path: `app/build/test-results/testDebugUnitTest/`
- Unit test status: `BUILD SUCCESSFUL` for `:app:testDebugUnitTest`
- AndroidTest result path: Pending physical/emulator connected run; no release evidence attached yet
- Additional notes:
  - `:app:assembleRelease`, `:app:writeReleaseChecksums`, `:app:productionGate`, and `:app:releaseReadinessGate` all passed locally.
  - This bundle is local validation evidence only and does not replace CI/device/soak sign-off.

