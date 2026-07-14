# 📋 SATNET Documentation Index
## Current Documentation Guide

**One app bringing together local-first communication, SATNET Maps, and Bitcoin-enabled SATNET tools**

**Status**: Documentation refreshed for current repository snapshot  
**Date**: April 24, 2026  
**Release posture**: Pilot / pre-production

---

## 📚 Start Here

### 1. `README.md`
**Purpose**: Fast current overview of the app and docs  
**Read time**: 5-10 minutes  
**Use this if**: You want the shortest accurate summary of what SATNET currently does

### 2. `CURRENT-RELEASE.md`
**Purpose**: Current repository snapshot notes  
**Read time**: 10 minutes  
**Use this if**: You want the latest release posture, current feature summary, and caution notes

### 3. `PRIVACY.md`
**Purpose**: Privacy and data-handling policy  
**Read time**: 10-15 minutes  
**Use this if**: You need to understand what stays on-device, what can leave the device, and how SATNET-related data is handled

### 4. `DONATIONS_AND_GRANTS.md`
**Purpose**: Bitcoin-only support, grants, and sponsorship guidance  
**Read time**: 10 minutes  
**Use this if**: You are updating public support pages, partner outreach, or in-app help copy

---

## 🧭 Product and Feature Documentation

### 5. `SATNET_GLOBAL_README.md`
**Purpose**: Larger SATNET feature narrative  
**Read time**: 1-2 hours  
**Key topics**:
- Bitcoin wallet and role flows
- voucher lifecycle
- merchant and verifier concepts
- rollout thinking and operating model

### 6. `UNIFIED_APP_SUMMARY.md`
**Purpose**: Short integrated view of communication + Bitcoin tooling  
**Read time**: 10 minutes  
**Key topics**:
- unified architecture
- integration examples
- product positioning

### 7. `FEATURE_AUDIT_REPORT.md`
**Purpose**: Feature-by-feature audit status  
**Read time**: 30 minutes  
**Key topics**:
- implementation status
- documentation coverage
- feature verification notes

### 8. `AUDIT_FINAL_REPORT.md`
**Purpose**: Final audit summary  
**Read time**: 15 minutes  
**Key topics**:
- audit conclusion
- coverage metrics
- overall quality summary

---

## 🛠️ Build, Release, and Testing

### 9. `DEPLOYMENT_TESTING_GUIDE.md`
**Purpose**: Build, test, and deployment guidance  
**Read time**: 1-3 hours depending on depth  
**Key topics**:
- environment setup
- debug/release builds
- testing phases
- troubleshooting

### 10. `doc/PRODUCTION_ACCEPTANCE_CRITERIA.md`
**Purpose**: Criteria required before broader public release  
**Read time**: 10-15 minutes

### 11. `doc/RELEASE_RUNBOOK.md`
**Purpose**: Release execution and rollback checklist  
**Read time**: 15-20 minutes

### 12. `doc/CI_FAILURE_RUNBOOK.md`
**Purpose**: CI triage and failure response guide  
**Read time**: 10-15 minutes

### 13. `doc/GLOBAL_PUBLIC_SHIPPING_AND_FUNDING_DECISION.md`
**Purpose**: Current shipping and funding posture memo  
**Read time**: 10 minutes

### 14. `doc/SHIP_READINESS_PUNCH_LIST.md`
**Purpose**: Action checklist to move from pilot readiness toward safer public launch  
**Read time**: 10-15 minutes

---

## 🧱 Architecture and Sustainability

### 15. `TECHNICAL_ARCHITECTURE.md`
**Purpose**: Developer-oriented implementation details  
**Read time**: 2-3 hours  
**Key topics**:
- architecture layers
- wallet and voucher design
- security architecture
- data models

### 16. `doc/SATNET_COMMUNITY_SUSTAINABILITY_MODEL.md`
**Purpose**: Community-aligned sustainability model  
**Read time**: 20-30 minutes  
**Key topics**:
- no protocol tax / no surveillance monetization
- optional service-layer funding model
- Bitcoin-aligned support posture
- stewardship and governance principles

### 17. `VISUAL_SUMMARY.md`
**Purpose**: Diagrams and visual references  
**Read time**: 30 minutes

---

## 🎯 Suggested Reading Paths

### Product / operations
1. `README.md`
2. `CURRENT-RELEASE.md`
3. `DONATIONS_AND_GRANTS.md`
4. `doc/GLOBAL_PUBLIC_SHIPPING_AND_FUNDING_DECISION.md`

### Developer
1. `README.md`
2. `CURRENT-RELEASE.md`
3. `UNIFIED_APP_SUMMARY.md`
4. `TECHNICAL_ARCHITECTURE.md`
5. `DEPLOYMENT_TESTING_GUIDE.md`

### Security / privacy review
1. `CURRENT-RELEASE.md`
2. `PRIVACY.md`
3. `FEATURE_AUDIT_REPORT.md`
4. `TECHNICAL_ARCHITECTURE.md`
5. `doc/PRODUCTION_ACCEPTANCE_CRITERIA.md`

### Support / partnership / funding
1. `README.md`
2. `CURRENT-RELEASE.md`
3. `DONATIONS_AND_GRANTS.md`
4. `doc/SATNET_COMMUNITY_SUSTAINABILITY_MODEL.md`

---

## 🔑 Current Key Concepts

### Main app areas
- **Communication card**: call, messages, contacts, maps, file sharing, settings, connect, help, app sharing
- **SATNET card**: role setup plus Bitcoin wallet, voucher, verifier, and optional merchant flows

### Core SATNET roles
- **User**: wallet holder and voucher redeemer
- **Agent**: voucher issuer
- **Merchant**: Lightning invoice creator when enabled in the build
- **Verifier**: review and settlement workflow participant

### Platform facts from the repository
- **Min API**: 19 (Android 4.4)
- **Target API**: 34 (Android 14)
- **Primary language**: Java
- **Build system**: Gradle
- **Bitcoin library family in docs/code**: bitcoinj-based wallet tooling
- **QR**: ZXing-based voucher scanning flows

---

## 🔗 Important Links

- Website: https://satnet.app
- Repository: https://github.com/servalproject/batphone
- Bitcoin reference: https://bitcoin.org
- Lightning reference: https://lightning.network

---

## 📞 Support and Contact Pointers

- Bug reports: GitHub Issues
- Security/privacy references: `PRIVACY.md` and repository security contact material
- Funding/support language: `DONATIONS_AND_GRANTS.md`
- Release posture: `CURRENT-RELEASE.md`

---

## ✅ Quick Commands

**Build debug**
```bash
./gradlew clean assembleDebug
```

**Build release**
```bash
./gradlew assembleRelease
```

**Run tests**
```bash
./gradlew test
```

---

**Last updated**: April 24, 2026  
**Scope**: Current repository documentation refresh
