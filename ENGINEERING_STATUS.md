# Engineering status and release gates

**As of 2026-10-24: engineering / field-test candidate. Production and scientific release are blocked.** Migration/build success is not scientific validation.

## Verified evidence at bounded scope

- **VERIFIED:** Source code provenance is `0cb51ed64540c4ec12079d1fe6c68c6b6b6769ed`; the source checkpoint publication commit is `498cbdf28ec4a06734b172b8ade84d28a441265a`. They are different identities.
- **VERIFIED:** Two 2026-10-24 live-session exports passed archive CRC and all 13 embedded file-hash checks, manifest/JSON/CSV/SQLite count reconciliation, sequence continuity, and accepted-to-persisted accounting for the two sessions tested.
- **MEASURED:** Session 1: 6,273 observations, 63 explicit bounded-channel-full rejects, no accepted-to-persisted gap. Session 2: 6,740 observations, no channel-full/channel-closed rejects, no accepted-to-persisted gap.
- **OPEN:** Historical session `[historical session ID omitted]` recorded 27 accepted raw-measurement deliveries and 1 accepted status delivery not represented in persisted rows. Root cause remains unverified; source GitHub issue #3 remains open.
- **VERIFIED:** Source and destination share MIT license text. The imported build uses root project configuration and retains `app`, `propagation-contract`, and `orbitcore-bridge` modules.

Historical receipts describe their original dates and scope. Older totals/APK hashes are not current results unless independently repeated. See `docs/history/README.md` and `docs/migration/DOCUMENTATION_MIGRATION_REPORT.md`.

## Open engineering gates

1. **OPEN:** Root cause and regression reproduction for the historical accepted-to-persisted mismatch; keep issue #3 open.
2. **OPEN:** Sustained high-load, queue pressure, shutdown race, process death, and repeated capture/export/resume testing.
3. **OPEN:** Authentic Room v5/v6 schema provenance and migration coverage. The available authentic schema file is v7; synthetic fixtures do not establish authentic v5/v6 history.
4. **OPEN:** Physical API 26–28 device coverage and clean-machine reproducible builds.
5. **OPEN:** Memory/endurance profiling, including historical low-memory exits.
6. **UNVERIFIED:** Calibrated GNSS interference/jamming/spoofing detector sensitivity, specificity, false-alarm rate, latency, independent ground truth, and controlled RF validation.
7. **UNVERIFIED:** AGC hardware capability and scientific calibration. Constant observed AGC values do not establish detector performance.
8. **OPEN:** Skipped SGP4/SDP4 reference-vector tests, time-scale/frame validation, and authoritative time-valid satellite-identity provenance.
9. **OPEN:** Production signing, package provenance, and release reproducibility. Existing APK evidence is debug-signed and must not be represented as a production release.
10. **OPEN:** Cross-platform CI validation and review of third-party data/source-vector licensing.

## Variant and compatibility contract

`primary` preserves the `horizon.observatory` release identity; `ts` and `t1` preserve their existing suffixes and manifest placeholders; `parallel` is added with its own launcher resources/theme/authorities. Effective IDs must be verified from generated metadata for all relevant build types. OrbitCore remains a T1-only dependency unless a separately reviewed change establishes otherwise.

## Interpretation boundaries

- C/N0 and AGC observations alone are not proof of jamming, spoofing, or calibrated detection.
- A successful export/integrity audit proves only the tested archive and accounting properties.
- Source timestamps and ingestion timestamps are distinct; source-time regressions remain diagnostic unless independently resolved.
- No RF transmission or interference was injected for the two-session checkpoint.
- Physical-device state was not changed during the canonical migration; device acceptance for the migrated tree is **UNVERIFIED**.

## Migration verification status

The baseline destination-main build/test result and the post-import migration results are recorded in `docs/migration/DOCUMENTATION_MIGRATION_REPORT.md`. The migration remains a review candidate until the documented acceptance gates and public-disclosure checks pass.
