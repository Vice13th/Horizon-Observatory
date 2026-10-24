# Horizon Parallel â€” Live Device Session Analysis

**Date:** 2026-10-24
**Overall disposition:** PASS for two end-to-end session exports and accepted-to-persisted reconciliation; intermittent ingress channel saturation observed in session 1. Historical issue #3 remains open.

## 1. Environment and preservation

- App: `horizon.observatory.parallel.debug`, version `1.0.0-observatory`, versionCode 1; debug-signed, not a production release. Installed APK SHA-256: `81B7079CE4ADA8C5D1EB469C5E72B2E984158EB44BEECCCBA253ED8D880A2992`.
- Device: Samsung SM-A075F, Android 16 / API 36, ADB serial `[device serial redacted]`.
- Repository/worktree: `[local source path omitted]`.
- Source checkout at test start: branch/HEAD `test/final-review-handover-2026-10-22` / `0cb51ed64540c4ec12079d1fe6c68c6b6b6769ed`.
- The protected `[local path omitted]` snapshot was not modified. The device-test execution made no app-source or APK-input changes and did not clear app data or uninstall the app. This report and the updated receipt are documentation-only artifacts; any subsequent publication is separate from device execution.
- Both test sessions were started and stopped through the app's own UI. No RF transmitter or artificial GNSS interference was used. Raw session data remained local to the authorized workstation and was not uploaded to research services.

## 2. Sessions and export artefacts

| Measurement | Session 1 | Session 2 |
|---|---:|---:|
| Session ID | `6c831fdc-f86c-417e-9405-d60731d2e0d4` | `8844e39c-af07-409c-b618-6b7ed69305a9` |
| Duration | 123.786 s | 120.719 s |
| Manifest observations | 6,273 | 6,740 |
| System events / errors | 43 / 0 | 34 / 0 |
| App/SQLite state | COMPLETED | COMPLETED |
| Delivery diagnostics | FINALIZED; invariants true | FINALIZED; invariants true |
| Export ZIP bytes | 1,409,576 | 1,468,895 |
| Export ZIP SHA-256 | `9A45B3CE97D42A493C8EC2DC916F8DBBF363627D4F9562234DD298191F66E8E4` | `C8B32EAA217CFD3564A3BE5BFC9FDD995502C006562C483562DAB85D2EECFDA0` |

Local exports:
- `[local source path omitted]`
- `[local source path omitted]`

## 3. Independently verified archive integrity

For **each** archive:
- ZIP CRC scan passed; all 14 expected entries are present, including `dataset_manifest.json`, `observations.json`, `observations.csv`, `session_events.json`, `session_events.csv`, `session_log.txt`, `integrity_audit.json`, `capabilities.json`, `permissions.json`, `gnss_domain_snapshot.json`, `satellite_evidence.csv`, `gnss_antenna_evidence.json`, `checksums.sha256`, and `horizon_session.sqlite`.
- All **13/13** entries listed by `checksums.sha256` independently verified, with no missing files or hash mismatches.
- Observation totals agree across the manifest, JSON, CSV, and SQLite: 6,273 for session 1; 6,740 for session 2.
- SQLite lifecycle is `COMPLETED`; its integrity-audit row matches the manifest: sequence starts at 1, ends at the total observation count, and is contiguous.
- Manifest, SQLite session metadata, and session log diagnostics are consistent. Both archives mark diagnostics `FINALIZED`, `countsComplete=true`, and `invariantsHold=true`.
- Ingestion timestamps are complete and nondecreasing in both sessions. Source timestamp regressions were 2,608 and 2,466, respectively; the app labels these as diagnostics, not integrity failures, because asynchronous producers can yield source timestamps out of order. The ingestion sequence remains the integrity clock.

## 4. Accepted-versus-persisted reconciliation

### Session 1 â€” bounded-channel saturation, no post-acceptance loss

| Observation type | Attempted | Accepted | Rejected | Channel full | Persisted in SQLite | Accepted âˆ’ persisted |
|---|---:|---:|---:|---:|---:|---:|
| GNSS_RAW_MEASUREMENT | 2,184 | 2,125 | 59 | 59 | 2,125 | 0 |
| GNSS_STATUS | 122 | 118 | 4 | 4 | 118 | 0 |
| GNSS_CLOCK | 122 | 122 | 0 | 0 | 122 | 0 |
| GNSS_NAVIGATION_MESSAGE | 178 | 178 | 0 | 0 | 178 | 0 |

Total counted channel-full rejections: **63**; channel-closed rejections: **0**. Each delivery invariant holds. The 59 raw rows and 4 status rows were explicitly rejected at the bounded channel, so they were not counted as accepted and do not create an accepted-to-persisted gap.

### Session 2 â€” no observed channel rejection

| Observation type | Attempted | Accepted | Rejected | Channel full | Persisted in SQLite | Accepted âˆ’ persisted |
|---|---:|---:|---:|---:|---:|---:|
| GNSS_RAW_MEASUREMENT | 1,982 | 1,982 | 0 | 0 | 1,982 | 0 |
| GNSS_STATUS | 120 | 120 | 0 | 0 | 120 | 0 |
| GNSS_CLOCK | 120 | 120 | 0 | 0 | 120 | 0 |
| GNSS_NAVIGATION_MESSAGE | 177 | 177 | 0 | 0 | 177 | 0 |

Total counted channel-full rejections: **0**; channel-closed rejections: **0**. Each delivery invariant holds.

**Conclusion:** Both exports pass accepted-to-persisted reconciliation. Session 1 revealed explicit ingress backpressure; session 2 did not. Neither new session reproduced the historical case of rows counted as accepted but missing in SQLite.

## 5. Aggregate GNSS domain and live telemetry

Values below are the app's exported `gnss_domain_snapshot.json` aggregates, not externally calibrated measurements.

| Metric | Session 1 | Session 2 |
|---|---:|---:|
| GNSS-domain sample count | 2,160 | 2,019 |
| Average reported C/Nâ‚€ | 21.217 dB-Hz | 22.600 dB-Hz |
| Minimum / maximum reported C/Nâ‚€ | 0 / 34.034 dB-Hz | 0 / 34.992 dB-Hz |
| Average reported elevation | 52.575Â° | 53.564Â° |
| Average AGC | 5.087 dB | 5.000 dB |
| Minimum / maximum AGC | 5 / 6 dB | 5 / 5 dB |
| Distinct carrier frequencies | 8 | 6 |
| Correlated raw measurements | 2,125 | 1,982 |
| Status-only satellites in export summary | 35 | 37 |
| Raw-only satellites in export summary | 0 | 0 |
| Navigation messages | 178 | 177 |

During session 2, the live GNSS screen showed approximately 52 observed satellites, a live-window C/Nâ‚€ display of 23.8 dB-Hz, and 27 event-level AGC samples across three constellation/carrier groups. Those retained-window values are a live snapshot and need not equal the later session-wide export aggregate. The event-level values shown were 5.00 dB with 0.0000 dB spread in that window.

Capability limitations recorded by the app:
- Raw GNSS measurements and navigation messages: SUPPORTED.
- Antenna information: UNSUPPORTED.
- Rotation-vector sensor: UNAVAILABLE; the sky display therefore reports orientation unavailable and does not claim a live device heading.
- AGC support: recorded as UNVERIFIED in the device capability snapshot, although event-level AGC values were observed in the live UI.

These are observational telemetry values only. Constant AGC or C/Nâ‚€ alone does not establish jamming, spoofing, interference sensitivity, or a calibrated detection threshold. The ION GNSS+ 2019 STRIKE3/Android raw-measurement paper explicitly discusses false-alarm considerations from user dynamics: https://www.ion.org/publications/abstract.cfm?articleID=16984

## 6. Runtime and UI observations

- Both sessions were started and finalized through the Overview Start/Stop controls; the app returned to IDLE/COMPLETED after each.
- GNSS screen navigation worked during session 2. The live page showed signal-history samples, event-level AGC history, constellation/carrier groups, GNSS domain counts, and the observed-sky display.
- No fatal exception, ANR, or NoSuchMethodError matches were found in the sampled logcat windows after the tests. This is smoke evidence, not a long-duration endurance certification.
- UI evidence remains local and untracked in the worktree root, including `current-device-live-session-started.xml/png`, `current-device-live-session-sample.xml/png`, `current-device-live-session-stopped.xml/png`, `current-device-live-session2-started.xml/png`, `current-device-live-session2-gnss.xml/png`, `current-device-live-session2-gnss-scrolled*.xml`, `current-device-live-session2-prestop.xml/png`, and export-state XML/screenshots.

## 7. Interpretation and outstanding work

1. **Accepted-to-persisted behavior:** PASS for both new sessions, with zero gaps for raw measurements, status, clock, and navigation-message observations.
2. **Queue capacity/backpressure:** intermittent. Session 1 counted 63 queue-full rejections; session 2 counted none. This should remain a separate stress/backpressure concern, not be conflated with persistence loss.
3. **Historical issue #3 remains OPEN:** an earlier archive recorded 27 raw-measurement rows and 1 GNSS status row accepted but absent from SQLite. These two sessions do not explain that historical artifact's root cause. Keep the issue open until the old archive/source revision is reconciled or the mismatch is reproduced.
4. **Not tested here:** injected process death or write failure, sustained multi-hour load, RF jamming/spoofing, calibrated detection accuracy, production signing, or API 26â€“28 device validation. No RF was transmitted.
5. No tracked source changes were made. Current Git branch and HEAD remain unchanged.

## References

- Miralles, D.; Moghadam, M. S.; Akos, D. M. â€œGNSS Threat Monitoring and Reporting with the Android Raw GNSS Measurements and STRIKE3.â€ ION GNSS+ 2019, pp. 275â€“289. https://doi.org/10.33012/2019.16984
