# Documentation migration report — 2026-10-24

## Disposition

**VERIFIED merged:** PR [#9](https://github.com/Vice13th/Horizon-Observatory/pull/9) was merged into canonical `main` on 2026-10-24. Merge commit: `e6dd76b1ee99e3767c08eed2ce7a41f65732d9a6`; merged PR head: `48a4cb4b5a7ba522a3d97d11bdc51aeeca71a1e8`. The canonical project source is at the repository root. This is engineering integration, not scientific or production release approval.

- **VERIFIED local:** Primary/Parallel unit tests and OrbitCore bridge tests passed after restoring immutable SGP4 bytes. Fresh XML: primary 380 tests / 0 failures / 0 errors / 3 skipped; Parallel 380 / 0 / 0 / 3; bridge 8 / 0 / 0 / 0. `propagation-contract` has no test sources.
- **VERIFIED local:** All four debug and four release APK variants assembled. All four debug APKs pass `apksigner verify` with APK Signature Scheme v2 true. Release APKs remain unsigned until production signing.
- **VERIFIED local:** Filtered public-disclosure scan returned 0 hits; relative Markdown link check found 0 broken links; `git diff --check` passed.
- **VERIFIED CI:** Run [40125524916](https://github.com/Vice13th/Horizon-Observatory/actions/runs/40125524916) completed successfully on commit `d8285af426b820fc4b18cd208c0742089deac749`: Android SDK setup, package checks, primary/Parallel/core tests, primary/Parallel debug assembly, and TS/T1 compilation all succeeded. The workflow fixes the obsolete SDK package request and preserves immutable SGP4 raw bytes.

This migration is not production or scientific release approval. Known GNSS persistence, authentic Room v5/v6, device matrix, RF validation, and production signing gates remain open.
## Historical pre-merge checkpoint — 2026-10-24

- **VERIFIED / OBSERVED:** Migration branch `migration/horizon-parallel-canonical-2026-10-24`; checked head `400a5a29ac97b0cbaa0440c452304d0145b96326`.
- **VERIFIED:** Source application/build commit `0cb51ed64540c4ec12079d1fe6c68c6b6b6769ed`; source checkpoint publication commit `498cbdf28ec4a06734b172b8ade84d28a441265a`. These are distinct Git identities.
- **VERIFIED:** Destination `main` base at preflight/checkpoint `37cecf9d874052b598a1cb64a706119838377b99`.
- **OBSERVED at that historical checkpoint:** PR [#9](https://github.com/Vice13th/Horizon-Observatory/pull/9) was open, Ready for review (`draft=false`), not merged, with no submitted reviews returned by the review API. This was superseded by the merge recorded above.
- **VERIFIED CI:** [Run 40125938239](https://github.com/Vice13th/Horizon-Observatory/actions/runs/40125938239) completed successfully (`conclusion=success`) for head `400a5a29ac97b0cbaa0440c452304d0145b96326`. This run validates only that exact head; it must not be represented as validating any later documentation commit. Earlier runs remain listed in the chronological CI history below.
- This is a dated checkpoint, not a prediction of future PR status. Following commits, review submissions, or a merge require a new remote read and a new checkpoint.
## Verified repository identities and method

- Source: https://github.com/Vice13th/Horizonparallell
- Source main: 498cbdf28ec4a06734b172b8ade84d28a441265a
- Source build/source commit for the approved APK: 0cb51ed64540c4ec12079d1fe6c68c6b6b6769ed
- Destination: https://github.com/Vice13th/Horizon-Observatory
- Destination main/base: 37cecf9d874052b598a1cb64a706119838377b99
- Migration branch: migration/horizon-parallel-canonical-2026-10-24
- Source complete-package ZIP: 10,500,605 bytes; measured SHA-256 E2BC83DB39B6E83A61A202F058A79B51D96225AC500B4902C925EAFBEDDD711E.
- VERIFIED: source build SHA and source checkpoint publication SHA are different. App, modules, Gradle wrapper/configuration, and root build settings were byte/tree-compared and found unchanged between source build commit and source main. Source main adds the two-session audit/receipt documents after the APK build commit.
- Method: curated tree import from source main into a clean clone of destination main on a dedicated migration branch. No source .git directory or source commit history was imported. Destination history remains the parent chain; source origin is recorded explicitly rather than falsely claiming common ancestry.

## Inventory and reconciliation counts

The audit matrix contains 818 rows reconciled to preflight inventory snapshots:

| Inventory | Count | Revision/state |
|---|---:|---|
| Source tracked project files | 400 | source main 498cbdf... |
| Source relevant local untracked items | 92 | preserved in source workspace; not published |
| Destination tracked files | 306 | destination main 37cecf9... |
| Separate dirty destination feature-workspace status entries | 20 | preserved untouched; not used for migration |

Disposition totals in DOCUMENTATION_AUDIT_MATRIX.csv:

| Disposition | Items |
|---|---:|
| UPDATE | 393 |
| PRESERVE_HISTORICAL | 162 |
| MOVE | 38 |
| KEEP_PRIVATE | 122 |
| EXCLUDE_DUPLICATE | 103 |
| Total | 818 |

These are file-level decisions, not claims that every historical document was rewritten. Current docs were reconciled; dated evidence retains its historical scope. The matrix records path, source/destination revision, file class, audience, initial state, migration impact, disposition, reason, and verification.

## Tree changes

- Imported active app/, propagation-contract/, and orbitcore-bridge/ implementation and tests into the destination root, plus Gradle wrapper/configuration and selected build/setup/verification utilities.
- Preserved primary, ts, and t1 configuration; integrated the parallel flavor launcher resources, theme, labels, and provider authority placeholders.
- Added .gitattributes rules using -text and -whitespace for four SGP4 reference fixtures so exact raw bytes remain unchanged across Windows and Linux; recorded SHA-256 provenance assertions pass.
- Updated root README, changelog, engineering status, handoff, documentation index, identity table, migration provenance, and CI workflow. CI names primary/Parallel tests, both debug assemblies, core-module tests, and TS/T1 compile tasks.
- Moved 24 root .txt receipts to docs/history/build-receipts/, three root checkpoint JSON files to docs/history/checkpoints/, and eleven dated root Markdown checkpoint notes into the history area. Text normalization/redaction removes workstation paths and device serials. Prior Git history remains intact.
- Removed the unreferenced root orbit-core-android-0.1.0-sources.jar because redistribution provenance/license was not established. Source orbit-src/ and its JAR were excluded for the same reason; no tracked build references to the root JAR were found.
- Excluded source artifacts/release/ APKs, screenshots/XML, generated test outputs, duplicate research exports, internal recovery/device-install notes with machine-specific details, and unreviewed mutation/device-specific scripts. Destination research dataset files were preserved; SHA-256 values matched the corresponding source research files byte-for-byte.
- The protected historical checkout was not modified. The existing dirty destination feature workspace was not modified; all edits are in a separate clean migration clone.

## Build and test evidence

### Destination-main baseline

VERIFIED: on a clean destination-main clone, the following command exited 0 and reported BUILD SUCCESSFUL with 61 actionable tasks executed:

    .\gradlew.bat :app:testPrimaryDebugUnitTest :app:assemblePrimaryDebug :propagation-contract:test :orbitcore-bridge:testDebugUnitTest --no-daemon --stacktrace

Baseline XML outputs were subsequently removed from that clone when the tracked source app tree was mirrored into the migration workspace. Exact baseline test XML totals are therefore unavailable from that workspace and are not inferred from the success line. A separate clean baseline rerun remains necessary if exact baseline totals are required for comparison.

### Fresh migration-branch verification

VERIFIED: the following fresh command exited 0, reported BUILD SUCCESSFUL, and executed 141 actionable tasks (not merely up-to-date outputs):

    .\gradlew.bat :app:testPrimaryDebugUnitTest :app:testParallelDebugUnitTest :propagation-contract:test :orbitcore-bridge:testDebugUnitTest --no-daemon --rerun-tasks

| Task family | XML suites | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| :app:testPrimaryDebugUnitTest | 58 | 380 | 0 | 0 | 3 |
| :app:testParallelDebugUnitTest | 58 | 380 | 0 | 0 | 3 |
| :orbitcore-bridge:testDebugUnitTest | 2 | 8 | 0 | 0 | 0 |
| :propagation-contract:test | No test sources | 0 | — | — | — |

TS/T1 debug Kotlin compilation tasks executed successfully. Fresh assembly of the remaining debug/release artifacts completed successfully: 326 actionable Gradle tasks, 278 executed and 48 up-to-date. All eight APK files were inventoried with SHA-256; effective package IDs and labels were checked using aapt. All four debug APKs were verified with apksigner verify --verbose --print-certs: exit 0, top-level result Verifies, and APK Signature Scheme v2 true. All four use Android Debug certificate SHA-256 1e14d3b1acb546e0caadb8504e9872fe5242909bfee6c45e4ba4abb4859db5e6. All release APKs are unsigned. Full per-APK hashes/identity/signing results are in docs/migration/APK_ARTIFACT_MATRIX.csv and docs/migration/APK_ARTIFACT_MATRIX.md. The Parallel debug byte hash matches the approved source checkpoint, and v2 signature verification passed.

### Debug APK byte and identity checks

| Variant | Package ID observed | Version | Size | SHA-256 |
|---|---|---|---:|---|
| parallelDebug | horizon.observatory.parallel.debug | 1.0.0-observatory / code 1 | 10,913,306 bytes | 81B7079CE4ADA8C5D1EB469C5E72B2E984158EB44BEECCCBA253ED8D880A2992 |
| primaryDebug | horizon.observatory.debug | 1.0.0-observatory / code 1 | 10,913,290 bytes | D43734D3758FA5ED6FFAE5BA798F971600254E2243A5DADC3B1CCBF906DA4EEB |

The Parallel debug APK hash matches the source checkpoint hash against actual generated bytes. All four debug APKs were verified with apksigner verify --verbose --print-certs: exit code 0, top-level result Verifies, and APK Signature Scheme v2 reported true. All four debug APKs use the Android Debug certificate SHA-256 1e14d3b1acb546e0caadb8504e9872fe5242909bfee6c45e4ba4abb4859db5e6. The four release APKs are unsigned, as expected. The historical output text `DOES NOT VERIFY` was attributed to a PowerShell native-stderr capture artifact in the earlier receipt. The original raw log files were not found in this inspection. On 2026-10-24, `apksigner verify --verbose --print-certs` was rerun against the four currently present debug APK files; all exact SHA-256 hashes matched the matrix and each fresh run returned `Verifies`, exit code 0, and v2=true. The Markdown matrix is corrected to reflect this fresh direct verification, not a claim that historical raw logs were recovered. TS/T1 debug and all release variant hashes/IDs are in docs/migration/APK_ARTIFACT_MATRIX.csv and docs/migration/APK_ARTIFACT_MATRIX.md.

### Warnings and limitations

- Gradle wrapper 8.11.1 emits a deprecation warning; Kotlin indicates the minimum supported Gradle will increase to 8.14.4 for Kotlin 2.5.0.
- Android SDK tooling warns that installed tools encounter SDK XML version 4 while understanding up to version 3.
- Kotlin compiler warnings remain in imported production/test source (deprecated Android GNSS properties, nullable/platform-type warnings, non-exhaustive when expressions, and unnecessary null assertions). These did not fail the fresh verification run.
- No physical-device install, app-data clear, live GNSS capture, RF transmission, or production signing was performed. Migrated-tree device acceptance is UNVERIFIED.


## Artifact signature verification checkpoint — 2026-10-24

The eight APK files currently present in `app/build/outputs/apk/` were measured at this checkout. Byte lengths and SHA-256 hashes match all eight rows in `APK_ARTIFACT_MATRIX.csv`. Android SDK Build Tools 36.0.0 `apksigner verify --verbose --print-certs` was rerun against each of the four existing debug APK files. Every run returned `Verifies`, exit code `0`, and `Verified using v2 scheme (APK Signature Scheme v2): true`; all reported signer-certificate SHA-256 `1e14d3b1acb546e0caadb8504e9872fe5242909bfee6c45e4ba4abb4859db5e6`. This is fresh verification of the exact byte hashes, not recovered historical raw output. No APK was rebuilt, and no hashes were changed. Release APKs remain unsigned; verification is not device acceptance, production signing, or release approval.
## Documentation, privacy, and licensing

- Root README no longer points at the stale 2026-10-18 APK/hash or old 352-test total as current. Current checkpoint counts are dated and linked.
- Source build, source publication, destination base, migration, and eventual merge identities remain separate.
- Older test receipts/manifests remain historical; their original claims and dates are not represented as current.
- The final filtered public-disclosure scan excluding generated build output, Git metadata, and the audit matrix returned 0 hits for known workstation paths, device identifiers, private-key headers, and common token patterns.
- Source and destination MIT license texts match. SGP4 reference-vector provenance cites the MIT-licensed sgp4-2.27 source distribution. The unreferenced third-party OrbitCore source JAR was not republished because its license/provenance was not established.
- Public payload excludes private raw GNSS session ZIPs, device screenshots/XML, and unreviewed source history. Existing destination Git history is not rewritten.

## Open gates

- PASS: All four debug and four release APK assemblies/identity inspections completed. Debug APK signature verification passed; release signing remains intentionally unconfigured.
- PASS: Final filtered disclosure scan 0 hits; relative Markdown link scan 0 broken links; git diff --check exit 0. Matrix is reconciled to captured preflight inventories.
- PASS CI: Run 40124804743 initially failed during Android SDK setup because android-actions/setup-android@v3 attempted the obsolete sdkmanager tools package. Runs 40125011649 and 40125250370 reached tests but failed the SGP4 provenance-hash test under EOL normalization. Commit d8285af restores exact raw fixture bytes and sets -text; run 40125524916 completed successfully with all workflow steps green. Production signing and scientific gates remain open.
- OPEN: Authentic Room v5/v6 schema history; only authentic Room v7 schema is present. No synthetic schema was promoted as authentic.
- OPEN: Historical accepted-to-persisted discrepancy; source issue #3 remains open. Two 2026-10-24 sessions passed reconciliation but did not establish the old root cause.
- OPEN: Sustained-load, queue-pressure, shutdown-race, process-death, repeated capture/export/resume, memory/endurance, API 26–28 physical-device coverage, and clean-machine reproducibility.
- UNVERIFIED: Calibrated GNSS interference/jamming/spoofing performance, independent ground truth, controlled RF validation, AGC capability/calibration, satellite-identity provenance, and remaining skipped SGP4 reference vectors.
- OPEN: Production signing and release approval.

## Rollback

Destination pre-migration base: 37cecf9d874052b598a1cb64a706119838377b99. Before merge, record migration branch head and PR URL. After merge, revert migration commit(s) with ordinary git revert on a new branch and submit a rollback PR. Do not reset or rewrite main; keep the source repository and its history intact.


## Post-merge verification — 2026-10-24

- **VERIFIED:** GitHub reports PR #9 `merged=true`, state `closed`, merge timestamp `2026-10-24T20:26:05Z`, merge commit `e6dd76b1ee99e3767c08eed2ce7a41f65732d9a6`.
- **VERIFIED:** Fetching `app/build.gradle.kts` from `main` confirms namespace and base application ID `horizon.observatory`, with the `primary` flavor label `Horizon Observatory`; `settings.gradle.kts` keeps root project name `HorizonObservatory`.
- **UNVERIFIED:** No workflow run was returned by the connector for the merge commit SHA at the time of this check. The PR-head CI passed on `48a4cb4b5a7ba522a3d97d11bdc51aeeca71a1e8` (run 40128890937). Do not interpret the absence of a merge-commit run as a failure or as a pass.
- Scientific, device, production-signing, and clean-machine reproducibility gates remain open.
