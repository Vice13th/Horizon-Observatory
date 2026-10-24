# Technical Architecture and Methods â€” 2026-10-18

## Runtime boundary
This package is the horizon.observatory.parallel.debug Parallel Debug variant. The current Parallel flavor does not package orbitcore-bridge; operational SGP4/SDP4 prediction is not claimed. An unavailable-backend state must not be presented as an orbit solution.

## Data path and export
Raw Android GNSS and system observations enter the observation/session storage path. Export reads persisted session data and produces JSON, CSV and SQLite-backed content with manifest/checksum metadata. Export correlation processes source entities incrementally and applies row/payload budgets to reduce overlap of large raw JSON references. This is a memory-risk reduction, not peak-memory or endurance certification.

## Time and scientific semantics
Keep source timestamps, ingestion timestamps, clock domain, unit and sequence identity distinct. Do not silently reorder or replace source times. Label raw, derived, predicted and unavailable data explicitly. Preserve GNSS clock and measurement validity state where available. Satellite identity requires authoritative time-valid mapping.

## Recorded release evidence
APK SHA-256 EE8204D1BD0D56FAD00C3C4D4D659AE5FDDB7FE4164A8E4189D0CFA29A00B4FE; 10,900,108 bytes. Recorded JVM suite: 352 tests, 0 failures, 0 errors and 3 skipped. Persisted-session export with 93,754 observations passed ZIP CRC, embedded hashes, JSON/CSV/SQLite count reconciliation, sequence continuity and SQLite integrity.

## Unresolved gates
Source timestamp regressions are 44,938/93,754 (~47.93%). Memory/endurance, prior LOW_MEMORY root cause, SGP4 reference vectors, Room v5/v6 baselines, connected instrumentation, outdoor GNSS ground truth, controlled interference/multipath, SVID-to-NORAD provenance, SAF save path and clean-machine reproducibility remain open. This is an engineering/field-test candidate, not scientifically release-closed.


## Supplemental timestamp provenance contract - 2026-10-22

The GNSS measurement callback selects the receiver clock's elapsed-realtime timestamp only on API 29+ and only when `GnssClock.hasElapsedRealtimeNanos()` is true. For API 26-28 or when the receiver clock reports the field unavailable, the acquisition layer indexes the event using Android callback-receipt monotonic time. `eventTimestampSource` is persisted into both the `GNSS_CLOCK` payload and each related `GNSS_RAW_MEASUREMENT` payload. The fallback represents callback receipt, not the receiver's measurement epoch; analyses that require receiver-time semantics must honor that provenance label.

The target has partial event-level AGC acquisition through reflective access to `GnssMeasurementsEvent.getGnssAutomaticGainControls()`, persisted in `GNSS_CLOCK.eventExtras.agcEvents` with `levelDb`, `constellationType`, and `carrierFrequencyHz`. The current GNSS-domain statistics and AGC history chart still consume legacy per-measurement AGC. A typed event-level analysis/chart path, empty-vs-not-exposed state handling and Room/export round-trip tests remain unfinished.

Verification after the timestamp change: Primary unit tests and assemble passed (356 tests, 0 failures, 0 errors, 3 skipped; 53 XML suites). APK: 10,908,390 bytes; SHA-256 `A2D3E864B2E0CF8898728427E6587721E3B1AB8E9DCBA1B05CB1967726F85D86`. This is software verification only, not physical API-26 compatibility or GNSS/RF validity evidence.
