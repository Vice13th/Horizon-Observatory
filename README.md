# Horizon Observatory

Horizon Observatory is a local-first Android scientific observation platform. The canonical implementation now incorporates the approved Horizon Parallel codebase at the repository root; it is not nested under `source/Horizon-Parallel`.

## Current engineering status — 2026-10-24

**Classification: engineering / field-test candidate. Not production-ready and not scientifically release-closed.** The migration does not close scientific, persistence, device, or release gates.

- Source repository: [Vice13th/Horizonparallell](https://github.com/Vice13th/Horizonparallell)
- Source code/build provenance: `0cb51ed64540c4ec12079d1fe6c68c6b6b6769ed`
- Source checkpoint publication: `498cbdf28ec4a06734b172b8ade84d28a441265a`
- Destination migration base: `37cecf9d874052b598a1cb64a706119838377b99`
- Current migration branch: `migration/horizon-parallel-canonical-2026-10-24`

## Installation variants

| Variant | Release application ID | Debug application ID | Status |
|---|---|---|---|
| `primary` | `horizon.observatory` | `horizon.observatory.debug` | Legacy identity preserved |
| `ts` | `horizon.observatory.ts` | `horizon.observatory.ts.debug` | Preserved |
| `t1` | `horizon.observatory.t1` | `horizon.observatory.t1.debug` | Preserved; OrbitCore bridge dependency remains T1-only |
| `parallel` | `horizon.observatory.parallel` | `horizon.observatory.parallel.debug` | Approved Parallel identity integrated |

The effective IDs are derived from Gradle flavor/build-type configuration and must be checked against generated APK metadata. Debug signing is not production signing.

## Architecture and evidence policy

The implementation keeps raw measurements, derived analysis, predictions, and dead-reckoned estimates distinguishable. Missing values remain missing, timestamps from source and ingestion remain separate, and export integrity is not evidence of scientific accuracy. The Parallel UI includes the approved 3D sky-dome experience; migration must not silently regress to the prior 2D-only view.

## Latest public GNSS checkpoint

Two controlled live sessions were recorded on 2026-10-24 in the source project. Session 1 exported 6,273 observations and reported 63 bounded-channel-full rejections. Session 2 exported 6,740 observations and reported no channel-full or channel-closed rejections. Both archive integrity and accepted-to-persisted reconciliation checks passed for those sessions. The historical accepted-to-persisted mismatch (27 raw rows and 1 status row) was not reproduced; its cause remains unverified and [issue #3](https://github.com/Vice13th/Horizonparallell/issues/3) remains open. These are scoped engineering observations, not RF ground-truth or interference-detector validation.

The raw session archives and device screenshots/XML are not published in this repository. See the curated [two-session audit](docs/research/LIVE_GNSS_TWO_SESSION_AUDIT_2026-10-24.md), [receipt](docs/research/LIVE_GNSS_TWO_SESSION_RECEIPT_2026-10-24.md), and [migration provenance](docs/migration/MIGRATION_PROVENANCE.md).

## Build and verification

Requirements: JDK 17, Android SDK with platform/build-tools required by the Gradle configuration, and network access for dependency resolution.

```powershell
.\gradlew.bat :app:testPrimaryDebugUnitTest :app:testParallelDebugUnitTest :propagation-contract:test :orbitcore-bridge:testDebugUnitTest --no-daemon
.\gradlew.bat :app:assemblePrimaryDebug :app:assembleParallelDebug --no-daemon
.\gradlew.bat :app:compileTsDebugKotlin :app:compileT1DebugKotlin --no-daemon
```

The exact results of fresh migration-branch commands are recorded in the migration report. `UP-TO-DATE`, `NO-SOURCE`, and skipped tests must not be described as freshly executed test bodies. No device installation, app-data clearing, live GNSS capture, RF transmission, or production signing is part of this migration.

## Documentation index

- [Engineering status and release gates](ENGINEERING_STATUS.md)
- [Master handoff](HANDOFF_MASTER.md)
- [Migration report](docs/migration/DOCUMENTATION_MIGRATION_REPORT.md)
- [Per-file documentation/configuration audit matrix](docs/migration/DOCUMENTATION_AUDIT_MATRIX.csv)
- [Migration provenance and rollback](docs/migration/MIGRATION_PROVENANCE.md)
- [Scientific methods](docs/architecture/SCIENTIFIC_METHODS.md)
- [Installation identity table](docs/architecture/IDENTITY_TABLE.md)
- [Historical checkpoint index](docs/history/README.md)
- [Security policy](SECURITY.md)
- [Contribution guide](CONTRIBUTING.md)

## License and attribution

The repository's MIT license remains in effect for project-owned material. Third-party datasets, scientific reference vectors, icons, and dependencies retain their own provenance and license obligations. See individual provenance records; do not infer that the repository license supersedes third-party terms.

## APK artifact matrix

All eight generated debug and unsigned release artifacts, measured hashes, package IDs, version metadata and signing status are recorded in [APK artifact matrix](docs/migration/APK_ARTIFACT_MATRIX.csv). Release APKs are intentionally unsigned until production signing is configured and approved.
