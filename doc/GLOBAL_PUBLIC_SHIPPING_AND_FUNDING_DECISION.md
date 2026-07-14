# Global Public Shipping and Funding Decision

Date: 2026-04-18
Workspace: `C:\Users\Test\AndroidStudioProjects\batphone`

## Executive Summary

**Decision today: NO-GO for global public shipping.**

The repository shows strong progress toward a pilot-ready release, but the current in-repo evidence does **not** support an immediate unrestricted global rollout.

If and when the release gates are completed, the most mission-aligned monetization model is:

1. **Donations / recurring sustainers** as the primary public funding channel
2. **Grants and public-interest funding** as the primary non-user funding channel
3. **Optional support / partner services** only as a secondary layer

This funding model is explicitly aligned with:

- `doc/SATNET_COMMUNITY_SUSTAINABILITY_MODEL.md`
- `app/src/main/java/org/servalproject/satnet/SatnetPolicy.java`
- `app/build.gradle`

## What the Repo Evidence Says

### Positive evidence already present

- CI workflow exists: `.github/workflows/android-ci.yml`
- Local unit tests pass: `current-unit-test-audit.log`
- Local lint audit reports no new issues versus baseline: `current-lint-audit.log`
- Local production gate / dry-run evidence exists:
  - `prod-gate-audit.log`
  - `productionGate-dryrun.log`
- Build policy already blocks disallowed monetization patterns:
  - no custodial mode
  - no mandatory protocol fees
  - no surveillance monetization

### Blocking evidence against global public shipping

1. **Formal scorecard is still NO-GO**
   - `doc/PRODUCTION_READINESS_SCORECARD.md` states: **NO-GO for unrestricted real-world production release**.

2. **Public release criteria are not fully evidenced**
   - `doc/PRODUCTION_ACCEPTANCE_CRITERIA.md` requires green CI on the release commit, release signing, device matrix completion, and security review.

3. **Global SATNET rollout is explicitly staged, not big-bang**
   - `doc/SATNET_GLOBAL_DEPLOYMENT_PLAN.md` says SATNET should launch through controlled stages and **never as an immediate worldwide release**.

4. **Default deployment stage is still pilot**
   - `gradle.properties` sets `satnet.deployment.stage=pilot`.

5. **Required public-release evidence is still missing in repo**
   - no completed CI evidence bundle
   - no completed 5-device physical matrix
   - no completed soak / recovery checklist
   - no completed signed-release evidence bundle

6. **Current release notes still describe the app as experimental / pre-production**
   - `CURRENT-RELEASE.md`

## Readiness Verdict by Gate

| Gate | Status | Notes |
|---|---|---|
| Gate A: Build and CI Integrity | Partial | Workflow exists, but in-repo release-commit CI evidence bundle is not filled out |
| Gate B: Static Quality | Near-pass locally | Local tests and lint audit look much better, but public artifact evidence/sign-off remains incomplete |
| Gate C: Device Verification | Not complete | `doc/DEVICE_MATRIX_TEMPLATE.md` is still a template |
| Gate D: Reliability and Recovery | Not complete | `doc/SOAK_TEST_CHECKLIST.md` is still a template |
| Gate E: Security and Privacy | Not complete | Signed-release evidence and final security sign-off are not attached |

## Shipping Recommendation

### Current recommendation

- **Do not position the app as globally ready for unrestricted public shipping yet.**
- Position it as one of the following until evidence is complete:
  - pilot
  - regional beta
  - invited production pilot

### Minimum exit criteria before global public shipping

1. Complete `:app:releaseReadinessGate` for the exact signed candidate
2. Attach CI run URL, commit SHA, and artifacts for the shipped commit
3. Complete `doc/DEVICE_MATRIX_TEMPLATE.md` with at least 5 physical devices
4. Complete `doc/SOAK_TEST_CHECKLIST.md` with 24h soak and recovery evidence
5. Finish signed-release and security review bundle under `doc/release-evidence-template/`
6. Update release notes so they no longer describe the release as experimental / pre-production
7. Promote rollout in stages consistent with `doc/SATNET_GLOBAL_DEPLOYMENT_PLAN.md`

## Funding Model Recommendation

## 1. Primary model: donations

Recommended public funding channels:

- one-time donations
- recurring monthly sustainers
- sponsor circles for translations, QA, device testing, and documentation
- nonprofit-style public support page

Why this fits:

- keeps core communication and wallet freedoms free
- matches the anti-extraction principles in `doc/SATNET_COMMUNITY_SUSTAINABILITY_MODEL.md`
- avoids incentives to add ads, tracking, or paywalls

## 2. Primary institutional model: grants

Best-fit grant targets:

- digital rights / censorship resistance
- humanitarian communications resilience
- open-source public infrastructure
- security audits and release engineering
- accessibility, localization, and low-end Android support

Best uses of grant funding:

- independent security audits
- device certification and QA
- regional pilot deployment support
- localization and documentation
- operational readiness and incident tooling

## 3. Secondary ethical revenue: optional services

Allowed only if optional and non-coercive:

- deployment support for NGOs / cooperatives
- device certification programs
- partner training for agent / merchant / verifier operations
- hosted relay / directory / analytics services that are not required for basic network freedom

## Revenue models to avoid

Do **not** fund the app through:

- mandatory protocol transaction fees
- surveillance advertising
- behavioral profiling
- custodial float income
- premium locks on basic wallet, recovery, or communication features

These are already inconsistent with project policy and build gates.

## Practical Next Steps

### Before shipping globally

1. Finish the missing release evidence bundle
2. Keep rollout scope at pilot / country beta
3. Prepare a lightweight public funding page with:
   - mission
   - what donations fund
   - grant-safe roadmap themes
   - transparency commitment

### Funding setup that can start now

1. Add a public donations page and recurring sustainer link
2. Publish a short grants one-pager for funders
3. Publish a transparency note explaining:
   - no ads
   - no surveillance monetization
   - no protocol tax
   - donations and grants fund security, QA, localization, and support

## Bottom Line

**Today:** not ready for unrestricted global public shipping.

**Best funding strategy once ready:**

- **mostly donations**
- **then grants**
- **optional support/services only as a secondary layer**

That is the strongest fit for both the current repo policy and the project's stated mission.
