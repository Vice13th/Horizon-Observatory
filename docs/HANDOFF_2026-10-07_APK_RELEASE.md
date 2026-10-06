# HORIZON Observatory — Release Handoff — 2026-10-07

## Goal
Preserve the first public, device-verified APK milestone so the next session can continue from repository and release truth without confusing artifact publication with scientific closure.

## Decisions
- The first APK is distributed through GitHub Release v1.0.0-observatory rather than committed into the repository because generated APK binaries are release artifacts, not source history.
- The release is anchored to commit a6181fb6259f463e1314f5103d0bfd84ed7cf024 because that is the verified source checkpoint used for the published APK.
- The exact APK identity is defined by filename Horizon-Observatory-v1.0.0-observatory.apk plus SHA-256 81CE8B59491525A7EC0DA91BDA3C5C0DBDEDDAAB310EC0FB93E311FE3F474A45.
- Publication does not close NORAD, UTC, OrbitCore reference accuracy, physical orientation, or K8 validation gates.
- Historical receipts remain historical; new release evidence is additive and does not rewrite earlier device receipts.

## Verified
- GitHub Release exists at https://github.com/Vice13th/Horizon-Observatory/releases/tag/v1.0.0-observatory.
- Release tag is v1.0.0-observatory and targets a6181fb6259f463e1314f5103d0bfd84ed7cf024.
- GitHub asset ID 616919082 has the exact real filename Horizon-Observatory-v1.0.0-observatory.apk.
- Published GitHub asset digest equals the local APK SHA-256 exactly.
- Main repository source remains separate from the APK artifact; no APK was committed.

## Next steps
- Keep main as the source-of-truth branch and treat the release tag as the artifact anchor.
- Continue observatory UI work only within the existing evidence-preserving architecture firewall.
- Resolve NORAD identity only through authoritative, time-valid provenance.
- Exercise UTC/device edge cases and K8 long-duration/interference validation as separate evidence gates.
- Validate OrbitCore against independent reference vectors before making numerical accuracy claims.
- Validate physical orientation only with trustworthy sensor evidence.

## Touched artifacts in this documentation milestone
- README.md
- docs/VERIFICATION_MATRIX_CURRENT.md
- docs/CHANGELOG_CHECKPOINT.md
- docs/index.html
- docs/releases/HORIZON_V1.0.0_APK_RELEASE_2026-10-07.md
- docs/checkpoint/HORIZON_CHECKPOINT_2026-10-07_APK_RELEASE.md
- docs/HANDOFF_2026-10-07_APK_RELEASE.md
