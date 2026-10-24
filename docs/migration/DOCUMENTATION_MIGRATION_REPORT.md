# Documentation migration report — 2026-10-24

## Disposition

Migration candidate on a dedicated branch; not yet merged. The approved source tree is integrated at the destination repository root. Fresh primary/Parallel JVM tests, OrbitCore bridge tests, all eight debug/release APK assemblies, filtered disclosure scan, relative Markdown links, and whitespace checks passed. A draft PR is published; PR CI/review remain pending. This does not grant production or scientific release approval.

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
- Added .gitattributes rules for SGP4 reference fixtures to preserve deterministic line endings across Windows and Linux. This fixes the test failure caused by Windows core.autocrlf transforming immutable reference bytes.
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

    .\gradlew.bat :app:testPrimaryDebugUnitTest :app:testParallelDebugUnitTest :app:assemblePrimaryDebug :app:assembleParallelDebug :app:compileTsDebugKotlin :app:compileT1DebugKotlin :propagation-contract:test :orbitcore-bridge:testDebugUnitTest --no-daemon --rerun-tasks

| Task family | XML suites | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| :app:testPrimaryDebugUnitTest | 58 | 380 | 0 | 0 | 3 |
| :app:testParallelDebugUnitTest | 58 | 380 | 0 | 0 | 3 |
| :orbitcore-bridge:testDebugUnitTest | 2 | 8 | 0 | 0 | 0 |
| :propagation-contract:test | No test sources | 0 | — | — | — |

TS/T1 debug Kotlin compilation tasks executed successfully. Fresh assembly of the remaining debug/release artifacts completed successfully: 326 actionable Gradle tasks, 278 executed and 48 up-to-date. All eight APK files were inventoried with SHA-256; effective package IDs and labels were checked using aapt. The four debug APKs report certificate SHA-256 1e14d3b1acb546e0caadb8504e9872fe5242909bfee6c45e4ba4abb4859db5e6, but apksigner verify returned DOES NOT VERIFY for all four, a hard blocker. All four release APKs are unsigned. Full per-APK hashes/identity/signing results are in docs/migration/APK_ARTIFACT_MATRIX.csv` and `docs/migration/APK_ARTIFACT_MATRIX.md. The Parallel debug byte hash matches the source checkpoint; that does not override signature-verification failure.

### Debug APK byte and identity checks

| Variant | Package ID observed | Version | Size | SHA-256 |
|---|---|---|---:|---|
| parallelDebug | horizon.observatory.parallel.debug | 1.0.0-observatory / code 1 | 10,913,306 bytes | 81B7079CE4ADA8C5D1EB469C5E72B2E984158EB44BEECCCBA253ED8D880A2992 |
| primaryDebug | horizon.observatory.debug | 1.0.0-observatory / code 1 | 10,913,290 bytes | D43734D3758FA5ED6FFAE5BA798F971600254E2243A5DADC3B1CCBF906DA4EEB |

The Parallel debug APK hash matches the source checkpoint hash against actual generated bytes. Both debug APKs report signer certificate SHA-256 1e14d3b1acb546e0caadb8504e9872fe5242909bfee6c45e4ba4abb4859db5e6, but apksigner verify returned DOES NOT VERIFY for all four debug APKs. The debug signing state is therefore not accepted as valid; production signing is not configured. TS/T1 debug and all release variant hashes/IDs are in docs/migration/APK_ARTIFACT_MATRIX.csv` and `docs/migration/APK_ARTIFACT_MATRIX.md.

### Warnings and limitations

- Gradle wrapper 8.11.1 emits a deprecation warning; Kotlin indicates the minimum supported Gradle will increase to 8.14.4 for Kotlin 2.5.0.
- Android SDK tooling warns that installed tools encounter SDK XML version 4 while understanding up to version 3.
- Kotlin compiler warnings remain in imported production/test source (deprecated Android GNSS properties, nullable/platform-type warnings, non-exhaustive when expressions, and unnecessary null assertions). These did not fail the fresh verification run.
- No physical-device install, app-data clear, live GNSS capture, RF transmission, or production signing was performed. Migrated-tree device acceptance is UNVERIFIED.

## Documentation, privacy, and licensing

- Root README no longer points at the stale 2026-10-18 APK/hash or old 352-test total as current. Current checkpoint counts are dated and linked.
- Source build, source publication, destination base, migration, and eventual merge identities remain separate.
- Older test receipts/manifests remain historical; their original claims and dates are not represented as current.
- The final filtered public-disclosure scan excluding generated build output, Git metadata, and the audit matrix returned 0 hits for known workstation paths, device identifiers, private-key headers, and common token patterns.
- Source and destination MIT license texts match. SGP4 reference-vector provenance cites the MIT-licensed sgp4-2.27 source distribution. The unreferenced third-party OrbitCore source JAR was not republished because its license/provenance was not established.
- Public payload excludes private raw GNSS session ZIPs, device screenshots/XML, and unreviewed source history. Existing destination Git history is not rewritten.

## Open gates

- PASS: All four debug and four release APK assemblies/identity inspections completed. Signature verification is a blocker for the debug APKs; release signing remains intentionally unconfigured.
- PASS: Final filtered disclosure scan 0 hits; relative Markdown link scan 0 broken links; git diff --check exit 0. Matrix is reconciled to captured preflight inventories.
- BLOCKED: Branch is not yet published. GitHub rejected draft PR creation (422 invalid head) because the remote head branch was absent. Finish explicit staged diff review, push the branch, verify remote SHA, create draft PR and inspect CI. No merge while signature verification, production signing or scientific gates remain open.
- OPEN: Authentic Room v5/v6 schema history; only authentic Room v7 schema is present. No synthetic schema was promoted as authentic.
- OPEN: Historical accepted-to-persisted discrepancy; source issue #3 remains open. Two 2026-10-24 sessions passed reconciliation but did not establish the old root cause.
- OPEN: Sustained-load, queue-pressure, shutdown-race, process-death, repeated capture/export/resume, memory/endurance, API 26–28 physical-device coverage, and clean-machine reproducibility.
- UNVERIFIED: Calibrated GNSS interference/jamming/spoofing performance, independent ground truth, controlled RF validation, AGC capability/calibration, satellite-identity provenance, and remaining skipped SGP4 reference vectors.
- OPEN: Production signing and release approval.

## Rollback

Destination pre-migration base: 37cecf9d874052b598a1cb64a706119838377b99. Before merge, record migration branch head and PR URL. After merge, revert migration commit(s) with ordinary git revert on a new branch and submit a rollback PR. Do not reset or rewrite main; keep the source repository and its history intact.
