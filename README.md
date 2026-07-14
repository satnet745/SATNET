SATNET README
=============
[SATNET][], refreshed April 2026

[SATNET][] is a local-first Android app for resilient communication and SATNET Bitcoin-enabled field tools.
The current repository build targets Android 4.4 (API 19) and above, with modern Android compatibility work for recent platform versions.

SATNET combines two major areas in one app:

- communication over local and mesh-friendly links
- self-custodial Bitcoin tools for wallet, voucher, verifier, and merchant workflows where enabled in the build

SATNET is [free software][] produced from the original upstream mesh codebase. The
Java/XML source code of SATNET is licensed to the public under the [GNU
General Public License version 3][GPL3]. The [serval-dna][] component of
SATNET is licensed to the public under the [GNU General Public License
version 2][GPL2]. All repository documentation is licensed to the public
under the [Creative Commons Attribution 4.0 International license][CC BY 4.0].

All source code and technical documentation are available from the SATNET
repository and related dependencies on [GitHub][].

Current App Overview
--------------------

### Communication hub
SATNET currently exposes the following user-facing communication flows from the main screen:

- **Call**: place SATNET calls to reachable peers
- **Messages**: MeshMS conversations and broadcast-capable messaging flows
- **Contacts**: peer discovery and Android contact integration
- **Share Files**: Rhizome-based file publishing, browsing, and saved-content access
- **Connect**: enable SATNET services and manage Wi-Fi, hotspot, Bluetooth, and legacy ad hoc networking
- **Help**: built-in guides, privacy summary, release summary, permissions help, and support information
- **Share SATNET**: help another user receive the app through local sharing methods

### SATNET financial tools
The main screen also includes a dedicated **SATNET** entry for Bitcoin-enabled workflows.
Depending on startup state, registered role, and build flags, current SATNET flows include:

- **role setup** for user, agent, merchant, and verifier paths
- **Bitcoin wallet setup** and wallet access
- **voucher issuance** for agent workflows
- **voucher redemption** into a self-custodial wallet
- **verifier dashboard** for review and settlement workflows
- **merchant Lightning tools** when Lightning support is enabled in the build

### SATNET Maps
SATNET Maps is part of the current app feature set and focuses on local-first field use:

- manual coordinates or one-time foreground location lookup
- temporary markers that remain local unless explicitly saved
- encrypted on-device bookmark storage
- optional encrypted Rhizome bookmark exchange
- role-aware overlays and local privacy controls

### Identity, privacy, and safety
- each installation has a durable SATNET identity (SID)
- display name and phone number/alias remain user-configurable
- wallet recovery material is user-controlled and should be stored offline
- Bluetooth, camera, notification, and location permissions are handled according to modern Android requirements
- SATNET remains local-first by design, but data you deliberately share can still leave the device

### Network and relay defaults
- default routing keeps direct, multi-hop, and internet paths first
- Tor and I2P relay settings are preconfigured so anonymous fallback routes are ready when needed
- SMS relay and Rhizome sneakernet remain last-resort fallbacks after censorship or connectivity failure is detected

Warnings
--------

SATNET is **EXPERIMENTAL SOFTWARE**. It has not yet reached version 1.0,
and is intended for pre-production, pilot, and demonstration use. It may not
work as advertised, it may lose or alter messages and files that it carries, it
may consume a lot of space, speed and battery, and it may crash unexpectedly.

The SATNET **Connect** screen can place your device into networking modes that
affect normal Wi-Fi behavior. If you join another user&apos;s hotspot or enable your
own hotspot, normal Wi-Fi client access may be interrupted and nearby devices may
consume your mobile data plan.

Legacy **Ad Hoc Mesh** support is especially risky. On some devices it may request
root permission and attempt low-level Wi-Fi behavior that modern Android does not
officially support.

SATNET telephony is a “best effort” service, primarily intended for when
conventional telephony is not possible or cost effective, and **MUST NOT BE
RELIED UPON** for emergencies in place of carrier-grade communications systems.

Rhizome file sharing is distributed storage, not a private encrypted messenger.
Files you share may be copied to other participating devices.

Support the Project (Bitcoin)
-----------------------------

SATNET support is Bitcoin-first. The in-app **Help -> About SATNET and how to support it in Bitcoin** page can now show a configured on-chain donation address.

Set donation details at build time:

    ./gradlew assembleDebug -Psatnet.donations.btc.address=bc1qyouraddresshere

Optional explicit URI:

    ./gradlew assembleDebug -Psatnet.donations.btc.address=bc1qyouraddresshere -Psatnet.donations.btc.uri=bitcoin:bc1qyouraddresshere

The support page URL is also configurable with `-Psatnet.support.url=...`.

Push Code to GitHub
-------------------

Use your fork as `origin` and push your branch:

    git checkout -b feature/your-change
    git add .
    git commit -m "Describe your change"
    git push -u origin feature/your-change

Then open a pull request from your fork branch to `servalproject/batphone`.

Documentation
-------------

 * [CURRENT-RELEASE.md](./CURRENT-RELEASE.md)  Current repository snapshot notes and release posture.

 * [INSTALL.md](./INSTALL.md)  Instructions for building the Android APK from
   source and installing manually.

 * [DEVELOP.md](./DEVELOP.md)  Tips for contributing to the software.

 * [PRIVACY.md](./PRIVACY.md)  Privacy policy for the repository build.

 * [DONATIONS_AND_GRANTS.md](./DONATIONS_AND_GRANTS.md)  Bitcoin-only support,
   grants, and sponsorship guidance.

 * [DOCUMENTATION_INDEX.md](./DOCUMENTATION_INDEX.md)  Navigation guide for the
   current documentation set.

 * [DEPLOYMENT_TESTING_GUIDE.md](./DEPLOYMENT_TESTING_GUIDE.md)  Production
   deployment test and sign-off gates.

 * [doc/PRODUCTION_ACCEPTANCE_CRITERIA.md](./doc/PRODUCTION_ACCEPTANCE_CRITERIA.md)  Required criteria for public production release.

 * [doc/RELEASE_RUNBOOK.md](./doc/RELEASE_RUNBOOK.md)  Release process and rollback checklist.

 * [doc/CI_FAILURE_RUNBOOK.md](./doc/CI_FAILURE_RUNBOOK.md)  CI triage and failure response guide.

 * [doc/GLOBAL_PUBLIC_SHIPPING_AND_FUNDING_DECISION.md](./doc/GLOBAL_PUBLIC_SHIPPING_AND_FUNDING_DECISION.md)  Current global shipping and funding decision memo.

 * [doc/SHIP_READINESS_PUNCH_LIST.md](./doc/SHIP_READINESS_PUNCH_LIST.md)  Punch list to move from pilot readiness to safer public launch.

 * [CREDITS.md](./CREDITS.md)  Individuals and organisations who have contributed to the software.

 * [`doc/`](./doc)  Technical documentation folder, including release evidence and supporting material.

 * [GitHub Issues][]  Tracking of bug reports and tasks.

-----
**Copyright 2014 original upstream authors; SATNET documentation refresh 2026.**  
![CC-BY-4.0](./cc-by-4.0.png)
This document is available under the [Creative Commons Attribution 4.0 International licence][CC BY 4.0].

[SATNET]: https://satnet.app
[GPL3]: ./LICENSE-SOFTWARE.md
[GPL2]: http://www.gnu.org/licenses/gpl-2.0.html
[CC BY 4.0]: ./LICENSE-DOCUMENTATION.md
[serval-dna]: https://github.com/servalproject/serval-dna
[GitHub]: https://github.com/servalproject
[free software]: http://www.gnu.org/philosophy/free-sw.html
[GitHub Issues]: https://github.com/servalproject/batphone/issues
