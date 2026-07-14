# Device Matrix Template

Use this template for every release candidate, pilot rollout, and production sign-off review.

## Release Metadata
- Release version:
- Commit SHA:
- Build variant:
- Test owner:
- Test window:
- Notes / artifact bundle:

## Coverage Targets
- Minimum 5 physical devices
- Include low-end, mid-range, and high-end hardware
- Include at least 3 Android API bands (19-23, 24-29, 30+ when available)
- Include at least one device with a working rear camera for QR scanner smoke
- Include at least one dual-SIM or vendor-customized device when regional deployment requires it

## Execution Matrix

| Device | OEM / Model | Android | ABI / Chipset Tier | Install | Upgrade | Reinstall | Mesh startup | Messaging | Wallet setup | Agent role | Merchant role | Verifier role | Voucher redeem | QR scanner / camera | Notes | Tester | Date |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 |  |  |  | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL |  |  |  |
| 2 |  |  |  | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL |  |  |  |
| 3 |  |  |  | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL |  |  |  |
| 4 |  |  |  | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL |  |  |  |
| 5 |  |  |  | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL | PASS / FAIL |  |  |  |

## Required Flow Notes Per Device
- Install source (fresh APK / upgrade from prior version)
- Startup time and first-run behavior
- Any Rhizome recovery or native crash containment observations
- Wallet unlock / PIN behavior
- Voucher scan result quality under real lighting
- Camera permission behavior and cancel/retry flow
- Merchant invoice render / share / QR display behavior
- Verifier pending-item and dispute tooling behavior

## Evidence Attachments
- Photos or screen recordings of QR scanner launch on at least one device
- `adb logcat` snippets for any FAIL entries
- Crash / ANR references, if observed
- Signed reviewer acknowledgement

## Sign-off
- Device matrix complete: YES / NO
- Blocking defects found: YES / NO
- Release recommendation from device validation owner: GO / NO-GO
