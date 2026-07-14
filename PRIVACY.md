SATNET Privacy Policy
=====================
Last updated: 2026-04-18

This policy describes how the open-source `SATNET` Android app in this repository handles personal data, identifiers, files, messaging data, and SATNET-related wallet/voucher data.

This document applies to the software shipped from this repository. If a distributor, partner, NGO, enterprise operator, or hosted service adds servers, analytics, support tooling, identity checks, or regional integrations, that operator must publish its own additional privacy notice.

## Summary

- Core identity, messaging, file-sharing, and SATNET data are designed to be stored primarily on the device.
- The app exchanges data with peers over mesh, local-network, Bluetooth, Wi-Fi, and other connectivity paths required for app features.
- If SATNET exchange-rate or directory features are enabled, the app may contact configured external HTTPS providers.
- Files or messages you intentionally share may be copied to other participating devices and may persist beyond your own device.
- The project does not intentionally include advertising SDKs or surveillance-based monetisation in the default app configuration.

## Data the app may process

Depending on enabled features and how you use the app, the app may process:

### Identity and profile data

- your chosen display name
- your chosen phone number or alias, if entered
- your SATNET ID / subscriber identifier (SID)
- contact mappings created on your device

### Communications and mesh data

- call metadata needed to route voice sessions
- MeshMS message content and message state
- peer identifiers and reachability state
- local network status used to operate mesh features

### Shared file and Rhizome data

- files you intentionally share
- file names, MIME types, manifests, bundle identifiers, and related metadata
- locally cached copies of files opened or exported through the app

### SATNET, wallet, and voucher data

If SATNET or Bitcoin-related features are enabled in your build or deployment, the app may process:

- wallet seed or encrypted wallet material stored locally
- wallet addresses and transaction metadata
- voucher payloads, issuance/redemption state, and verifier/merchant role data
- configured settlement network information (for example testnet vs mainnet)
- exchange-rate lookup requests to configured external providers

### Diagnostic and security data

- local logs necessary for debugging, crash investigation, and security review
- build integrity metadata such as checksums, version identifiers, and release evidence

## Where data is stored

By default, app data is primarily stored on the device running the app.

- Android backup is disabled in the app manifest.
- Android data extraction is restricted by app configuration.
- Some cached or temporary files may be created while opening, importing, exporting, or sharing content.
- If you uninstall the app, locally stored app data is generally removed by Android, except where you explicitly exported/shared data or other devices retained copies.

## What data leaves the device

Data may leave the device in the following cases:

1. **Peer-to-peer communication**  
   When you use calling, messaging, peer discovery, or file-sharing, identifiers and content required for those features are exchanged with peers and intermediate nodes as part of network operation.

2. **Rhizome file distribution**  
   Files and manifests you intentionally share may be replicated to other devices. Those copies may persist outside your control.

3. **SATNET external providers**  
   If SATNET exchange-rate or relay/directory features are enabled, the app may make HTTPS requests to configured providers. Those providers may receive your device IP address, request timestamps, and requested resources.

4. **App-to-app sharing**  
   If you export, view, open, or share files using other apps, those apps receive the data you chose to share and are governed by their own privacy policies.

## Permissions and why they may be used

The app requests a broad set of Android permissions because it includes mesh networking, calling, messaging, file sharing, QR, Bluetooth/Wi-Fi, contacts, and optional SATNET functionality. Depending on device version and enabled features, these may include:

- camera access for QR scanning and image capture flows
- microphone/audio access for calling features
- Bluetooth and Wi-Fi permissions for peer discovery and mesh operation
- contacts permissions for contact linking/import/export features
- notifications for call/message/update status
- storage/media access for opening and sharing files
- network access for mesh coordination, updates, and optional external SATNET services

Users and distributors should disable features they do not need and only grant runtime permissions required for intended use.

## Security posture

- The app is intended to use signed release artifacts for public distribution.
- Core communications and wallet-sensitive code paths are expected to be reviewed and regression-tested before release.
- Physical access to the device, device compromise, malicious side-loaded apps, unsafe backups outside app control, or insecure partner deployments can still expose user data.
- Users remain responsible for device passcodes, screen-lock settings, update hygiene, and secure handling of exported recovery material.

## Ads, analytics, and monetisation

The default project policy is:

- no advertising SDKs
- no protocol-fee coercion
- no custodial-by-default wallet mode
- no surveillance-based monetisation

If a downstream build adds analytics, telemetry, hosted services, customer support tooling, or financial integrations, that deployment must disclose them separately.

## Retention

- Profile, messaging, wallet, and voucher data remain on-device until deleted, reset, rotated, overwritten, or removed by uninstall.
- Shared Rhizome content may remain on other devices indefinitely once replicated.
- Release evidence, audit notes, and signed-artifact metadata may be retained by maintainers or operators for compliance, incident response, and supply-chain integrity.

## Children

This app is not designed as a child-directed service. Operators should not knowingly use it to solicit personal information from children without appropriate legal authority and disclosures.

## International and operator-specific deployments

Community, humanitarian, enterprise, or commercial deployments may have additional legal requirements involving financial services, telecommunications, sanctions screening, export controls, consumer protection, or regional privacy law. Those obligations are the responsibility of the deploying operator.

## Changes to this policy

This policy may be updated as features, integrations, or release practices change. Public releases should version this document and include the updated copy in release evidence.

## Contact and repository source

- Source repository privacy document: `PRIVACY.md`
- Current release notes: `CURRENT-RELEASE.md`

-----
**Copyright 2026 SATNET contributors**  
![CC-BY-4.0](./cc-by-4.0.png)
This document is available under the [Creative Commons Attribution 4.0 International licence][CC BY 4.0].

[CC BY 4.0]: ./LICENSE-DOCUMENTATION.md
