# Signed Release Checklist

- Release version: `0.94-pre-32-ge8941bd6-dirty`
- Commit SHA: `e8941bd653d15cb98bb9c22e983300fc1f9aaa56`
- Signing environment: Local Windows validation machine using `release-signing.local.properties`
- Keystore owner: Local validation keystore only (`build/local-release-validation.jks`) — **not a controlled production keystore**
- Key alias: `batphone-local-release`
- Signed APK path: `app/build/outputs/apk/release/app-release.apk`
- SHA256 checksum file: `app/build/outputs/checksums/release/SHA256SUMS.txt`
- Verification command/output:
  - Command: `apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk`
  - Result: `Verifies`
  - v1 signing: `true`
  - v2 signing: `true`
  - Signer DN: `CN=SATNET Local Release Validation, OU=Engineering, O=SATNET, L=Kampala, ST=Central, C=UG`
  - Signer certificate SHA-256: `11fb1552d23f6b280ba8f217f877fc93f3a6bf113a1f00f316edc4752b6b6f03`
- Gate verification:
  - `:app:verifyReleaseSigningConfig` — passed locally
  - `:app:productionGate` — passed locally
  - `:app:releaseReadinessGate` — passed locally
- Reviewer sign-off: Pending
- Notes:
  - The APK is locally signed and verifiable.
  - `apksigner` emitted META-INF entry protection warnings; these should be reviewed during release hardening but did not prevent signature verification.
  - Production release remains blocked until the same steps are repeated in the controlled signing environment with reviewer sign-off.

