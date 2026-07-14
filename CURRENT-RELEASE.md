Current Release Notes for SATNET
================================
[SATNET][], refreshed April 24, 2026

This document describes the current repository snapshot rather than the old historical 0.94 public release announcement.

Summary
-------

SATNET currently presents a unified Android app with:

- a communication hub for calling, messaging, contacts, file sharing, connectivity, and built-in help
- a SATNET financial hub for Bitcoin wallet setup/access, role registration, voucher flows, verifier tools, and build-dependent merchant Lightning tooling
- SATNET Maps for local-first mapping, secure bookmarks, and encrypted Rhizome bookmark exchange

The [SATNET Privacy Policy][] describes how the repository build handles
personal and other sensitive information.

Current platform posture
------------------------

From the current Gradle configuration in this repository:

- minimum supported Android level is API 19 (Android 4.4)
- target SDK is API 34 (Android 14)
- the app remains pre-1.0 and should still be treated as pilot / pre-production software

What changed in this refresh
----------------------------

The April 2026 documentation refresh aligns the built-in help and top-level documentation with the current app structure.

That refresh includes:

- updated help pages for the main screen, settings, sharing, accounts, connect, security, and permissions
- fixed in-app help links that now resolve to valid local help pages
- current SATNET feature descriptions for wallet, voucher, verifier, merchant, and maps flows
- Bitcoin-only wording for donations, grants, and sponsorship support guidance
- removal of stale legacy non-Bitcoin support wording from user-facing strings

Current release posture
----------------------

SATNET is **EXPERIMENTAL SOFTWARE**. It has not yet reached version 1.0 and is intended for pilot, field testing, and staged rollout work rather than unrestricted public launch.

The repository&apos;s own readiness documents still indicate that additional release evidence and validation are needed before a broader public shipping posture is appropriate.

See:

- [DONATIONS_AND_GRANTS.md](./DONATIONS_AND_GRANTS.md)
- [doc/GLOBAL_PUBLIC_SHIPPING_AND_FUNDING_DECISION.md](./doc/GLOBAL_PUBLIC_SHIPPING_AND_FUNDING_DECISION.md)
- [doc/SHIP_READINESS_PUNCH_LIST.md](./doc/SHIP_READINESS_PUNCH_LIST.md)

Operational cautions
--------------------

- SATNET telephony is a best-effort system and must not be relied upon for emergency communication.
- Hotspot and mesh networking modes can affect normal Wi-Fi behavior and may expose your mobile data plan to nearby devices.
- Legacy ad hoc Wi-Fi behavior remains high risk and device-specific.
- Rhizome file sharing is distributed storage, not a private encrypted messenger.
- Wallet recovery phrases and related Bitcoin secrets must be handled as highly sensitive offline material.

Current high-level feature set
------------------------------

### Communication
- voice calling to reachable peers
- MeshMS conversations and related messaging flows
- contacts and peer discovery
- Rhizome file sharing
- Wi-Fi / hotspot / Bluetooth connectivity management

### SATNET tools
- role setup for user, agent, merchant, and verifier paths
- Bitcoin wallet setup and wallet access
- voucher issuance and redemption
- verifier dashboard
- merchant Lightning support when enabled in the build

### SATNET Maps
- local-only temporary markers
- encrypted saved bookmarks
- one-time device location lookup when requested
- encrypted Rhizome bookmark export/import review

Historical note
---------------

Older release announcements and legacy device notes existed in earlier versions of this file. The current version is now focused on the present repository snapshot and linked readiness material.

-----
**Copyright 2026 SATNET contributors**  
![CC-BY-4.0](./cc-by-4.0.png)
This document is available under the [Creative Commons Attribution 4.0 International licence][CC BY 4.0].

[SATNET]: https://satnet.app
[SATNET Privacy Policy]: ./PRIVACY.md
[CC BY 4.0]: ./LICENSE-DOCUMENTATION.md
