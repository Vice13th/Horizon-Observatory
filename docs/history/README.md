# Historical records index

This directory preserves dated receipts, checkpoint manifests, and build logs that were previously scattered across the repository root. Files are retained as historical records, not current acceptance evidence. Redactions in migrated text files remove local Windows paths and device serials; they do not change the reported engineering result. Original destination Git history remains intact.

## Archived groups

- build-receipts/: root-level Gradle/compiler/build/test receipts moved under a stable archive path. Most files describe September–October 2026 checkpoints and are superseded by fresh migration-branch results.
- checkpoints/: old root checkpoint manifests, inventories, portability reports, and dated status/handoff notes.
- docs/research/ and docs/receipts/: dated engineering and scientific-method records retained in their existing folders.
- horizon Datasets/: previously published research-source bundles retained as historical datasets. These are research-source records, not the two raw live-device session archives from 2026-10-24.

Do not overwrite old results to make them look current. Current status lives in the root README, ENGINEERING_STATUS.md, and the migration report. See docs/migration/DOCUMENTATION_AUDIT_MATRIX.csv for per-file disposition.
