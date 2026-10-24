# Canonical migration provenance and rollback

## Repository identities

- Source: https://github.com/Vice13th/Horizonparallell
- Source app project path: source/Horizon-Parallel/
- Source build/source commit recorded for the approved APK: 0cb51ed64540c4ec12079d1fe6c68c6b6b6769ed
- Source checkpoint publication commit: 498cbdf28ec4a06734b172b8ade84d28a441265a
- Source main verified during preflight: 498cbdf28ec4a06734b172b8ade84d28a441265a
- Destination: https://github.com/Vice13th/Horizon-Observatory
- Destination main preflight/base commit: 37cecf9d874052b598a1cb64a706119838377b99
- Migration branch: migration/horizon-parallel-canonical-2026-10-24
- Published source checkpoint ZIP: release/Horizon-Parallel-20261024-complete-package.zip
- ZIP length observed on source workstation: 10,500,605 bytes
- ZIP SHA-256 measured against the actual bytes: E2BC83DB39B6E83A61A202F058A79B51D96225AC500B4902C925EAFBEDDD711E
- The package's reported Parallel debug APK hash is 81B7079CE4ADA8C5D1EB469C5E72B2E984158EB44BEECCCBA253ED8D880A2992; this migration does not claim a rebuilt APK has that hash.

## Method and ancestry

The migration uses a curated tree import into a fresh clone of destination main, on a dedicated migration branch. The source repository's .git directory and source commit history are not imported into the public destination. This is intentional: the private source contains local device captures, raw/export evidence, screenshots/XML, machine-specific paths, recovery notes, generated artifacts, and other historical blobs that were not authorized for public reachability. The original private source repository remains intact.

The destination migration commits retain destination ancestry by being ordinary descendants of 37cecf9d874052b598a1cb64a706119838377b99. The migration does not claim source commits are ancestors of the destination. The source code/build SHA, source publication SHA, destination base SHA, migration commit SHA, and eventual merge SHA are separate identities.

The source main checkpoint added the two-session audit/receipt after the reported APK build commit. These are distinct Git identities. VERIFIED by git diff --quiet: source app, modules, Gradle wrapper/configuration, and root build settings are unchanged between the reported build-source commit and source main. Source main adds the two-session audit/receipt documents after the APK build commit; these do not change app/module/build source.

## Imported paths and exclusions

Imported as the active project at repository root: app/, propagation-contract/, orbitcore-bridge/, Gradle wrapper/configuration, root Gradle scripts, selected build/setup/verification scripts, selected research methods and dated receipts, Android resources/tests/reference-vector fixtures, and the applicable .github templates/workflow after adapting task paths for all four flavors.

Not imported into the public active tree: source artifacts/release/ APKs, screenshots, XML dumps, generated test-result archives; orbit-src/ including its third-party source JAR (license/provenance not established and no tracked build references found); duplicate normalized GNSS research records under docs/research/Final/; internal recovery/continuation/device-install notes containing local paths or device serials; and mutation/device-specific scripts that have not passed public-disclosure review. Existing destination research datasets and historical Git objects remain in destination history; the migration does not rewrite published history.

Detailed per-file decisions are in DOCUMENTATION_AUDIT_MATRIX.csv. The matrix must be reconciled against the final source/destination inventories before merge.

## Scientific and release boundary

The two live sessions recorded on 2026-10-24 passed archive CRC/hash checks, JSON/CSV/SQLite reconciliation, sequence continuity, and accepted-to-persisted accounting for the sessions tested. Session 1 had 63 explicit bounded-channel-full rejections; session 2 had none. The earlier 27 raw + 1 status persisted-row discrepancy was not reproduced; root cause remains unverified and source issue #3 remains open. This is not calibrated RF or interference-detector validation.

Authentic Room v5/v6 schema history, sustained-load/shutdown/process-death tests, older Android device coverage, memory/endurance profiling, skipped SGP4 reference vectors, authoritative satellite identity provenance, production signing, and clean-machine reproducibility remain open. The migration must not install on a device, clear app data, run live capture, transmit RF, or production-sign.

## Rollback

Before merge, record the migration branch head and the destination base/merge SHAs in the migration report.

- Before PR merge: close the PR if needed; the branch can be retained for investigation. Do not force-push or delete source evidence.
- After merge: revert the migration commit(s) with a normal git revert on a new branch and submit a rollback PR. Do not reset or rewrite main.
- The destination pre-migration baseline is 37cecf9d874052b598a1cb64a706119838377b99. The source repository and its original history remain the recovery reference.
