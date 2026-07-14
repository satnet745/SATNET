# Soak Test Checklist

Use this checklist to satisfy Gate D in `DEPLOYMENT_TESTING_GUIDE.md` before any public rollout.

## Session Metadata
- Release version:
- Commit SHA:
- Device(s):
- Network setup:
- Start time:
- End time:
- Test owner:
- Evidence folder / link:

## 24-Hour Baseline Soak
- [ ] Fresh install or upgrade completed successfully
- [ ] App reaches stable idle state without crash loop
- [ ] Mesh services remain available for the full soak window
- [ ] Wallet screen opens after prolonged idle period
- [ ] Agent role still generates voucher after prolonged idle period
- [ ] Merchant role still creates invoice QR after prolonged idle period
- [ ] Verifier role still loads pending items after prolonged idle period
- [ ] Voucher redemption flow still opens scanner and manual entry fallback
- [ ] No fatal native crash from Rhizome / sqlite temp directory paths
- [ ] No foreground ANR observed

## Recovery / Resilience Drills
### Connectivity
- [ ] Airplane mode ON/OFF recovery
- [ ] Wi-Fi drop / reconnect recovery
- [ ] Mobile data drop / reconnect recovery
- [ ] Offline-first messaging behavior remains graceful
- [ ] Exchange-rate refresh failure degrades safely without blocking core use

### Process and Lifecycle
- [ ] App background / foreground loop repeated 10x
- [ ] Process death after wallet or role setup recovers safely
- [ ] Process death during voucher redemption does not corrupt ledger
- [ ] Screen rotation or multi-window behavior checked where supported
- [ ] Low-memory relaunch does not lose critical SATNET role state

### Camera / Scanner
- [ ] Camera permission grant path works
- [ ] Camera permission deny path works
- [ ] QR scanner cancel path returns cleanly to voucher screen
- [ ] Scanner relaunch after cancel works
- [ ] Real QR payload scan works on at least one physical device

## Evidence to Attach
- [ ] `adb logcat` capture for the full session or sampled checkpoints
- [ ] Crash / ANR count summary
- [ ] Screenshots or video for scanner, merchant invoice QR, and verifier flows
- [ ] Any bug IDs created for failures
- [ ] Final GO / NO-GO recommendation

## Failure Triage Notes
| Time | Scenario | Expected | Actual | Severity | Bug / Owner |
|---|---|---|---|---|---|
|  |  |  |  |  |  |

## Final Sign-off
- Gate D complete: YES / NO
- Blocking issues remaining:
- Recommended release decision: GO / NO-GO

