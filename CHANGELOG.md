# Changelog

## 2026-10-24 — Canonical Horizon Parallel migration candidate

- Integrated the approved Horizon Parallel Android implementation at the canonical repository root through `migration/horizon-parallel-canonical-2026-10-24`.
- Preserved the `primary`, `ts`, and `t1` identities and added the `parallel` installation flavor with its own resources and provider authority placeholders.
- Retained the existing three Gradle modules and authentic Room v7 schema; did not fabricate Room v5/v6 baselines.
- Added a file-level documentation/configuration audit, migration provenance, public-disclosure notes, and rollback procedure.
- Carried forward the 2026-10-24 two-session GNSS accounting results with the correct scope; issue #3 and scientific/release gates remain open.
- Excluded generated APK/test-output archives, local screenshots/XML, duplicate raw research exports, private recovery traces, and an unverified third-party source JAR from the canonical tree. Their disposition is recorded in the audit matrix; source history and source repository remain intact.

Earlier dated records remain historical and retain their original findings and artifact hashes.
