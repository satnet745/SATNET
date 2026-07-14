# Release Evidence Bundle Template

Use this folder as the canonical handoff bundle for a release candidate or production sign-off review.

## Folder guide
- `ci/` — workflow URLs, commit SHA, and archived CI artifact references
- `signing/` — signed release checklist and signing verification notes
- `device/` — completed device matrix and soak / recovery notes
- `artifacts/` — artifact inventory, checksum references, and generated report locations

## Suggested usage
1. Copy this folder for the specific release candidate
2. Fill each markdown file with links and evidence
3. Attach the completed bundle to the final GO / NO-GO review
