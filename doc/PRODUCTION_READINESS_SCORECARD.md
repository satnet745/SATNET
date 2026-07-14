# Production Readiness Scorecard

Date: 2026-04-15
Workspace: `C:\Users\Test\AndroidStudioProjects\batphone`
Decision scope: Local production-readiness audit against `DEPLOYMENT_TESTING_GUIDE.md`

## Executive Verdict

**Overall decision: NO-GO for unrestricted real-world production release.**

Current best classification:
- **Engineering maturity:** strong pilot / pre-production candidate
- **Local build quality:** substantially improved and locally reproducible
- **Operational production readiness:** **not yet complete**
- **SATNET financial mainnet readiness:** **NO-GO**

The repo now includes an in-repo CI workflow, signed-by-default ship gates, SATNET role smoke coverage, unit-test coverage, and release evidence tasks. However, the five-gate release policy in `DEPLOYMENT_TESTING_GUIDE.md` is still **not fully satisfied** until CI run evidence, device-matrix evidence, soak/recovery evidence, and signed-release/security sign-off evidence are attached for the exact release candidate being shipped.

---

## Evidence Collected In This Pass

### Local command results

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.servalproject.satnet.ui.MerchantLightningActivityTest" --tests "org.servalproject.satnet.ui.VerifierDashboardActivityTest" --tests "org.servalproject.satnet.ui.VoucherRedemptionActivityTest" --tests "org.servalproject.satnet.ui.QRScannerActivityTest" --tests "org.servalproject.satnet.ui.SatnetRoleRoutingTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease --console=plain
.\gradlew.bat :app:lintReportDebug --console=plain
.\gradlew.bat :app:productionGate --console=plain
```

### Result summary

- `:app:testDebugUnitTest` ✅ PASS
- Focused SATNET Robolectric smoke suite ✅ PASS
- `:app:assembleRelease` ✅ PASS
- `:app:productionGate` ✅ PASS
- `app/build/reports/lint-results-debug.html` generated ✅
- `app/build/reports/lint-results-debug.txt` generated ✅

### New smoke coverage added in this pass

- `app/src/test/java/org/servalproject/satnet/ui/SatnetRuntimeTestHelper.java`
- `app/src/test/java/org/servalproject/satnet/ui/VoucherRedemptionActivityTest.java`
- `app/src/test/java/org/servalproject/satnet/ui/QRScannerActivityTest.java`

### Shared SATNET test setup extracted

Runtime setup duplication was reduced by centralizing Robolectric SATNET application/runtime preparation in:
- `app/src/test/java/org/servalproject/satnet/ui/SatnetRuntimeTestHelper.java`

---

## Gate-by-Gate Scorecard

| Gate | Requirement | Current Status | Verdict |
|---|---|---|---|
| Gate A | Build and CI Integrity | CI workflow exists in repo, but verified CI run evidence is still required for the release commit | **NO-GO** |
| Gate B | Static Quality | Unit tests pass, release assembles, lint report exists, but lint report still has 2 active errors | **NO-GO** |
| Gate C | Device Verification | No in-repo 5-device physical validation evidence | **NO-GO** |
| Gate D | Reliability and Recovery | No in-repo 24h soak, network-flap, or process-death evidence | **NO-GO** |
| Gate E | Security and Privacy | Crypto/unit coverage exists, but release is unsigned and no signed-release checklist evidence is attached | **NO-GO** |

---

## Gate A — Build and CI Integrity

### Required by policy
From `DEPLOYMENT_TESTING_GUIDE.md`:
- `android-ci` workflow passes on the release commit
- `validate-wrapper`, `quality-gates`, and `release-sanity` are green
- build reproducible from clean checkout

### Evidence available
- Local Gradle verification passed
- `:app:productionGate` passed locally
- No local-only code patching was required during this pass

### Missing evidence
- `.github/workflows/android-ci.yml` exists in the workspace
- No CI run URL
- No CI commit SHA
- No archived CI artifacts

### Verdict
**NO-GO**

### To pass Gate A
- Add or point to the `android-ci` workflow
- Produce CI run URL and commit SHA
- Confirm green runs for:
  - `validate-wrapper`
  - `quality-gates`
  - `release-sanity`

---

## Gate B — Static Quality

### Required by policy
- Lint report generated and reviewed
- No new critical lint regressions
- Unit tests pass
- Release APK assembles successfully

### Evidence available
- `app/build/reports/lint-results-debug.html` generated
- `app/build/reports/lint-results-debug.txt` generated
- `:app:testDebugUnitTest` passed
- `:app:assembleRelease` passed
- `:app:productionGate` passed

### Important findings
`lint-results-debug.txt` currently reports:
- **2 errors**
- **72 warnings**
- plus baseline-filtered historical debt

Active lint **errors** observed:
1. `app/src/main/java/org/servalproject/rhizome/MeshMS.java`
   - `UnsafeImplicitIntentLaunch`
2. `app/src/main/java/org/servalproject/ServalBatPhoneApplication.java`
   - `UnsafeImplicitIntentLaunch`

These are not just cosmetic warnings; lint explicitly notes this may crash on an upcoming Android version.

### Verdict
**NO-GO**

### To pass Gate B
- Fix or explicitly review and sign off the 2 active lint errors
- Re-run:
  ```powershell
  .\gradlew.bat :app:lintReportDebug :app:testDebugUnitTest :app:assembleRelease --console=plain
  ```
- Capture reviewer sign-off for the lint report

---

## Gate C — Device Verification

### Required by policy
- At least 5 physical devices
- low-end, mid-range, high-end coverage
- install / upgrade / uninstall-reinstall flows
- core flows pass

### Evidence available
- One instrumentation smoke test exists:
  - `app/src/androidTest/java/org/servalproject/AppLaunchSmokeTest.java`
- Additional Robolectric SATNET smoke coverage now exists for:
  - merchant
  - verifier
  - voucher redemption
  - QR scanner
  - role routing

### Missing evidence
- No physical device matrix artifact
- No install/upgrade/reinstall log bundle
- No signed owner/date matrix

### Verdict
**NO-GO**

### To pass Gate C
Create and attach a device matrix containing at minimum:
- Device model
- Android version
- ABI/chipset tier
- Install result
- Upgrade result
- Reinstall result
- Core flow result
- Owner and date

---

## Gate D — Reliability and Recovery

### Required by policy
- 24-hour soak test
- airplane-mode / network-flap recovery
- restart and process-death recovery validation

### Evidence available
- Startup hardening has been added in code
- Native Rhizome containment work exists
- SATNET role startup crash-hardening is substantially improved
- Unit/integration tests cover several finance paths

### Missing evidence
- No 24-hour soak logs
- No ANR/crash trend report
- No restart/process-death test evidence
- No network flap recovery report

### Verdict
**NO-GO**

### To pass Gate D
Produce:
- 24h soak log set
- crash/ANR counts
- process-death recovery notes
- offline/airplane-mode/network-flap validation notes

---

## Gate E — Security and Privacy

### Required by policy
- Documented repeatable release signing
- No secrets in source control
- security-sensitive paths regression-tested

### Evidence available
- Security-sensitive unit/integration coverage exists for wallet and encryption flows
- Build policy blocks custodial mode, surveillance monetization, and protocol-fee violations
- Testnet-first and stage-aware safety gating remain active

### Blocking concerns
- Public ship gates now require release signing unless an explicit local dry-run override is used
- No signed-release checklist artifact is attached
- No evidence bundle proving production signing process execution
- `network_security_config.xml` keeps a loopback-only cleartext exception that must remain justified and documented

### Verdict
**NO-GO**

### To pass Gate E
- Configure and verify controlled release signing
- Capture signed-release checklist
- Review cleartext traffic policy and justify or tighten it
- Attach security sign-off notes for key storage, PIN handling, encryption, and logs

---

## SATNET-Specific Readiness Notes

### What improved in this pass
- Shared SATNET runtime test harness extracted
- New smoke coverage added for:
  - `VoucherRedemptionActivity`
  - `QRScannerActivity`
- Existing smoke coverage preserved for:
  - `MerchantLightningActivity`
  - `VerifierDashboardActivity`
  - SATNET role routing

### What that means
This materially improves confidence in:
- SATNET role startup gating
- voucher intake UI behavior during warm-up
- QR scanner launch/cancel flow
- valid voucher payload display path

### What it does not prove
It does **not** prove:
- real camera behavior on physical devices
- OEM-specific scanner camera stack stability
- wallet redemption behavior under real process death/restart
- field reliability across unstable mesh/network conditions
- production operational readiness

---

## Immediate Blockers List

1. **No CI evidence attached**
2. **2 active lint errors remain**
3. **Release is unsigned**
4. **No 5-device physical test matrix**
5. **No 24h soak / recovery evidence**
6. **No signed security/release sign-off bundle**

---

## Recommended Next Actions

### Highest priority
1. Fix the 2 `UnsafeImplicitIntentLaunch` lint errors
2. Set up real release signing and validate a signed artifact
3. Produce a 5-device matrix for install / upgrade / core flows
4. Run a 24-hour soak and recovery test pass
5. Attach CI evidence or add in-repo workflow definitions

### Useful follow-up engineering work
- Add instrumented smoke coverage for scanner behavior on a real/emulated camera-capable device
- Add process-death recovery coverage for SATNET wallet/redeem flows
- Review and reduce outstanding lint debt beyond the baseline

---

## Final Decision

**GO / NO-GO: NO-GO**

Reason:
The app is now in a much stronger **pilot-ready engineering state**, but it still does **not** satisfy all five release gates required by `DEPLOYMENT_TESTING_GUIDE.md` for real-world production deployment.

