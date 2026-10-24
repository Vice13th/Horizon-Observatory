# Documentation repair checkpoint — 2026-10-24

This note records current evidence for the documentation-only repair. It does not change the application, build configuration, tests, database schema, or release signing.

## APK signature verification (fresh rerun)

The eight APKs in `app/build/outputs/apk/` were present at the measured migration checkout. Their byte lengths and SHA-256 hashes match the pre-existing artifact matrix. Android SDK Build Tools 36.0.0 `apksigner verify --verbose --print-certs` was rerun against each of the four existing debug APKs. Each returned `Verifies`, exit code `0`, and `Verified using v2 scheme (APK Signature Scheme v2): true`; each reported certificate SHA-256 `1e14d3b1acb546e0caadb8504e9872fe5242909bfee6c45e4ba4abb4859db5e6`. These are fresh verification results against the currently present bytes, not recovered historical log files. No APK was rebuilt and no artifact hash was changed. The four release APKs remain unsigned; this is not production signing or device acceptance.

## Current GitHub checkpoint

At the time checked on 2026-10-24, PR #9 was open, ready for review (`draft=false`), not merged, and the review-submission list was empty. Existing CI run 40125938239 completed successfully on previous head `400a5a29ac97b0cbaa0440c452304d0145b96326`; it does not validate any subsequent commit. Destination `main` was `37cecf9d874052b598a1cb64a706119838377b99` at the last remote read.

## Limits

The previous raw apksigner log files were not found during this inspection, so the table is justified by the fresh direct rerun rather than a claim that historical raw receipts were recovered. Scientific/release gates remain open as documented elsewhere.
