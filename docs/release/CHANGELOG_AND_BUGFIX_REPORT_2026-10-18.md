# Changelog and Bug-Fix Report â€” 2026-10-18

## Included engineering changes
- Export correlation processes raw observation entities incrementally so large JSON references can be collected before the derived snapshot is assembled.
- Explicit source-row and raw-payload budgets remain enforced for export correlation.
- Interference evidence scoring excludes unknown evidence from the denominator; insufficient evidence remains UNKNOWN rather than being treated as negative evidence.
- Export validation preserves cross-format row counts and checksum checks for the tested persisted session.
- Release package includes build/test evidence, UI screenshots/hierarchies, scientific-method limits, architecture notes and open-gate reporting.

## Latest recorded verification
APK SHA-256: EE8204D1BD0D56FAD00C3C4D4D659AE5FDDB7FE4164A8E4189D0CFA29A00B4FE, 10,900,108 bytes; signer SHA-256: 1e14d3b1acb546e0caadb8504e9872fe5242909bfee6c45e4ba4abb4859db5e6.
Recorded JVM suite: 352 tests, zero failures/errors, 3 skipped across 52 XML suites. APK assembly succeeded and installed APK pulled hash matched.
Persisted-session export passed for 93,754 observations; ZIP CRC, 13/13 embedded hashes, JSON/CSV/SQLite counts, contiguous sequence and SQLite integrity passed.
Source-time regressions remain 44,938/93,754 (~47.93%); root cause unresolved. This documentation refresh did not rebuild or reinstall the APK.

## Not fixed / not certified
Source-time chronology, memory/endurance, LOW_MEMORY root cause, SGP4 reference vectors, Room migration baselines/connected instrumentation, outdoor GNSS ground truth, controlled interference/multipath, authoritative satellite identity, SAF save path and clean-machine reproducibility remain open. The release is not scientifically release-closed.


## Supplemental development entry - 2026-10-22 (not a release certification)

- **Fix:** guard access to `GnssClock.elapsedRealtimeNanos` and its availability flag behind API 29+, matching the app's minSdk 26.
- **Fallback:** when the receiver clock field is API-unavailable or not advertised as valid, use the monotonic callback-receipt time and persist `eventTimestampSource` in both `GNSS_CLOCK` and `GNSS_RAW_MEASUREMENT` payloads. This fallback is not asserted to be the receiver's measurement time.
- **Regression tests:** four `GnssEventTimestampTest` cases passed for API 26/28 fallback, API 29 unavailable-field fallback, and valid API 29 receiver-time use.
- **Build/test:** current Primary suite passed, 356 tests, 0 failures/errors, 3 skipped across 53 XML suites. APK 10,908,390 bytes; SHA-256 `A2D3E864B2E0CF8898728427E6587721E3B1AB8E9DCBA1B05CB1967726F85D86`.
- **Not closed:** API 26-28 runtime verification, new device screenshots, Room/export round-trip, AGC event-level chart/analysis integration, controlled RF evidence and release gates remain open. This is a local engineering update, not a commit, published release or scientific detector certification.
- Full trace: `docs/research/DEVELOPMENT_HISTORY_AND_RESEARCH_LEDGER_2026-10-22.md`.
