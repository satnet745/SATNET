# Ship-Readiness Punch List
Date: 2026-04-18
Scope: Move from pilot to real public launch
Status: Working checklist
## Goal
Use this checklist to move from local/pilot confidence to an evidence-backed public launch.
Primary references:
- `DEPLOYMENT_TESTING_GUIDE.md`
- `doc/PRODUCTION_ACCEPTANCE_CRITERIA.md`
- `doc/PRODUCTION_READINESS_SCORECARD.md`
- `doc/RELEASE_RUNBOOK.md`
- `doc/SATNET_GLOBAL_DEPLOYMENT_PLAN.md`
- `doc/SATNET_OPERATIONS_RUNBOOK.md`
- `doc/release-evidence-template/`
## Exit condition
Only declare public launch readiness when:
- Gates A-E are complete for the exact signed artifact
- rollout stage is intentionally promoted beyond pilot
- public docs no longer describe the release as pre-production
- operations, rollback, and ownership are in place for the launch scope
## Phase 0 - Close the current evidence gap
### Gate A - Build and CI integrity
- [ ] Run `android-ci` successfully on the exact release commit
- [ ] Capture CI run URL and commit SHA
- [ ] Confirm green jobs: `validate-wrapper`, `quality-gates`, `release-sanity`
- [ ] Archive CI artifacts under `doc/release-evidence-template/ci/`
- [ ] Fill out `doc/release-evidence-template/ci/CI_RUN_EVIDENCE.md`
### Gate B - Static quality and signed artifact
- [ ] Configure controlled release signing
- [ ] Run `:app:releaseReadinessGate` for the exact candidate
- [ ] Confirm `app/build/outputs/checksums/release/SHA256SUMS.txt`
- [ ] Attach lint review notes and unit test references
- [ ] Confirm no critical/high unresolved defects remain in core journeys
```powershell
.\gradlew.bat :app:releaseReadinessGate --no-daemon --stacktrace
```
### Gate C - Device verification
- [ ] Complete `doc/DEVICE_MATRIX_TEMPLATE.md`
- [ ] Test at least 5 physical devices
- [ ] Cover low-end, mid-range, high-end, and multiple Android bands
- [ ] Include at least one camera-capable QR-scanner device
- [ ] Validate install, upgrade, reinstall, onboarding, messaging, file sharing, calling, wallet, voucher, merchant, and verifier flows
- [ ] Attach evidence for failures and record tester/date/final recommendation
### Gate D - Reliability and recovery
- [ ] Complete `doc/SOAK_TEST_CHECKLIST.md`
- [ ] Run 24-hour soak on representative device(s)
- [ ] Validate airplane-mode and network-flap recovery
- [ ] Validate process-death recovery after wallet/role setup
- [ ] Validate QR permission grant/deny/cancel/relaunch on physical device
- [ ] Record crash/ANR counts and attach evidence bundle
### Gate E - Security and privacy
- [ ] Verify signing inputs are controlled and repeatable
- [ ] Confirm no secrets are committed to source control
- [ ] Attach signed-release checklist under `doc/release-evidence-template/signing/`
- [ ] Add security regression notes for key storage, encrypted seed handling, PIN handling, and sensitive logging
- [ ] Reconfirm cleartext policy justification and record security sign-off
## Phase 1 - Make the release candidate launchable
### Core user-journey sign-off
- [ ] First launch and onboarding
- [ ] Identity/profile creation and persistence
- [ ] Peer discovery and connect/disconnect behavior
- [ ] Voice call setup, stability, and termination
- [ ] Messaging under intermittent connectivity
- [ ] File sharing verification
- [ ] SATNET wallet/voucher/reputation flows if enabled
### Release package completeness
- [ ] Update `CURRENT-RELEASE.md`
- [ ] Remove/revise pre-production wording for any truly public launch artifact
- [ ] Document known limitations honestly
- [ ] Archive rollback artifact and checksums
- [ ] Complete release manager / approver decision record
### Operational readiness
- [ ] Name owners for engineering, operations, security, compliance/partner ops, and support
- [ ] Confirm incident escalation contacts
- [ ] Confirm crash/ANR monitoring path
- [ ] Confirm rollback procedure works quickly
- [ ] Confirm support coverage matches target region and language needs
## Phase 2 - Promote via staged rollout, not big-bang launch
- [ ] Stage 0: internal lab complete
- [ ] Stage 1: pilot region complete
- [ ] Stage 2: country beta complete
- [ ] Stage 3: regional production complete before broad expansion
- [ ] Stage 4: global production only after multi-region ownership, failover, and compliance cadence exist
### Stage-specific checks
- [ ] rollout region explicitly defined
- [ ] legal/regulatory model selected for that region
- [ ] local operational owner assigned
- [ ] support language coverage confirmed
- [ ] payment corridor/liquidity model approved where relevant
- [ ] partner readiness sign-off recorded
- [ ] region-specific kill switches and rollback controls verified
## Phase 3 - Public launch messaging and trust
- [ ] Public docs match actual readiness state
- [ ] Store/website copy does not overclaim capabilities
- [ ] Privacy and safety language is current
- [ ] Known limitations are disclosed clearly
- [ ] Launch announcement explains rollout scope honestly
- [ ] Publish `DONATIONS_AND_GRANTS.md`
## Phase 4 - Funding that supports safe launch
- [ ] Publish donations/grants strategy page
- [ ] Define what funding is for: audits, QA, localization, operations, release engineering
- [ ] Publish transparency commitments
- [ ] Keep monetization aligned with anti-custodial, anti-surveillance, anti-protocol-tax rules
## Suggested owners
| Area | Suggested owner type |
|---|---|
| CI and release gates | release engineer |
| Device matrix | QA lead |
| Soak and recovery | QA + Android engineer |
| Security sign-off | security reviewer |
| Release notes and public messaging | release manager / product lead |
| Regional rollout and support | operations / partner lead |
## Final sign-off table
| Item | Owner | Evidence | Status |
|---|---|---|---|
| Gate A complete |  |  | TODO |
| Gate B complete |  |  | TODO |
| Gate C complete |  |  | TODO |
| Gate D complete |  |  | TODO |
| Gate E complete |  |  | TODO |
| Release notes current |  |  | TODO |
| Rollback verified |  |  | TODO |
| Launch scope approved |  |  | TODO |
| Final decision |  |  | GO / NO-GO |
## Bottom line
The path from pilot to public launch is mostly about evidence, repeatability, device confidence, recovery behavior, honest public messaging, and staged rollout discipline.
