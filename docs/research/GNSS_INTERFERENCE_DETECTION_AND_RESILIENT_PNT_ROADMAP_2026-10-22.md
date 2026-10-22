# Horizon GNSS Interference & Resilient PNT Roadmap (Implementation Contract)

## Current state — 2026-10-22 checkpoint

Canonical `main` used unchecked `trySend` in GNSS callbacks. The isolated branch `feat/gnss-acquisition-loss-accounting-2026-10-22` now wraps those admission paths with source-local accounting. It still persists legacy `GnssMeasurement.automaticGainControlLevelDb` and does not yet capture API-33 event-level AGC. The unversioned `C:\Projects\Horizon` copy contains different instrumentation and reflected event-level AGC; it was not transplanted wholesale.

**VERIFIED build gate:** a forced canonical-checkout run completed `:app:testPrimaryDebugUnitTest :app:assemblePrimaryDebug --rerun-tasks` successfully (46 tasks executed). **MEASURED test XML:** 168 tests, 0 failures, 0 errors, 3 existing skips.

## P0 — implemented on the isolated branch

- Count GNSS measurement callback batches and expected output fan-out (N measurement rows, matching canonical source behavior); record callback processing time.
- Count observation construction, send attempts, admitted rows, capacity rejections, closed-channel rejections, constructor failures, with per-observation-type breakdown.
- Apply the same non-blocking accounting to status (one row), navigation (one row), antenna callback collections and antenna snapshot enumeration.
- Preserve source payloads, timestamps, output cardinality and session-integrity behavior. Counters are currently an in-memory source snapshot only; they are not yet persisted or integrated with session-level queue/persistence audit.
- This observes only events delivered to the app and must not be interpreted as receiver/Android callback completeness.

`app/src/test/kotlin/horizon/observatory/acquisition/base/AcquisitionDiagnosticsTest.kt` covers accepted sends, bounded-channel rejection, closed-channel rejection, construction failure, and mixed batch fan-out. All 5 focused tests passed after the expected compile-time RED failure.

## P1 — not implemented

Canonical source still persists legacy `GnssMeasurement.automaticGainControlLevelDb`. On API 33+, the planned path uses `GnssMeasurementsEvent.getGnssAutomaticGainControls()` and preserves each item's level dB, constellation type and carrier frequency Hz. Unsupported hardware may return an empty collection; distinguish unsupported, empty, and legacy fallback states without interpreting missing AGC as absence of interference. Continue legacy per-measurement AGC under API 26–32 compatibility guards. Do not infer no interference from missing or stable AGC. Defer P1 until P0 is reviewed and merged/tested.

## P0 — admission-loss accounting design (implemented on branch; not merged)

The implementation records only app-observable activity: callback invocation, expected output fan-out, observation construction, non-blocking trySend attempt, and result. It cannot claim how many events Android or receiver hardware generated before a callback was delivered. Capacity rejection remains distinct from channel closure and construction exceptions. Raw payload fields, timestamp semantics, callback order, persistence queue, and incomplete-session gate are unchanged. Callback threads continue to use non-suspending admission.

**Canonical fan-out is authoritative:** one GNSS measurement callback currently emits N raw-measurement rows (one per eventArgs.measurements entry); it does **not** emit a clock row in this source. The status and navigation callbacks emit one row each. Antenna callbacks/snapshot retrieval may emit a collection. Expected row count follows that actual code instead of assuming a clock row.

Counters are thread-safe and available via a source-local snapshot: callbacks seen/in-flight, expected rows, constructions, send attempts, admitted rows, rejected-capacity, rejected-closed, constructor failures, callback duration, last failure, and per-ObservationType counts. These counters are in-memory only: downstream queue/persistence observability and session-level accounting remain separate follow-up work.

## P1 — AGC telemetry

On API 33+, use `GnssMeasurementsEvent.getGnssAutomaticGainControls()` and preserve each entry's level dB, constellation type and carrier frequency Hz. For unsupported devices an empty collection is valid; preserve `EVENT_LEVEL_EMPTY` versus `LEGACY_FALLBACK` versus `NOT_EXPOSED` only if those meanings can be represented without confusing unsupported versus zero samples. Continue legacy per-measurement AGC under API 26–32 compatibility guards. Do not infer no interference from missing or stable AGC. Defer P1 until P0 is reviewed and merged/tested.

Official sources:
- https://developer.android.com/reference/android/location/GnssMeasurementsEvent
- https://developer.android.com/reference/android/location/GnssAutomaticGainControl.html
- https://developer.android.com/reference/android/location/GnssMeasurement
- https://developer.android.com/develop/sensors-and-location/sensors/gnss-spoof-jam

## Research basis and implications

- Spens et al. (2022), *Detecting GNSS Jamming and Spoofing on Android Devices*, NAVIGATION: combine AGC/C/N0, GNSS/network location, mock-location and GNSS/system-time checks.
- Tegedor et al. (2026), *Monitoring Radio Frequency Interference Affecting GNSS Using Android Smartphones*, European Navigation Conference 2025: AGC changes vary by model; small shifts may not indicate interference.
- Tegedor et al. (2026), *Field Detection of GNSS Interference Using Smartphones at Jammertest 2025*: controlled static/dynamic/airborne evaluation uses per-band AGC as complementary evidence.
- Rustamov, Minetto & Dovis (2023), IEEE Open Journal of the Communications Society: cross-satellite temporal/spatial C/N0 correlation can aid spoofing awareness, distinct from jamming indication.
- Gogoi et al. (2018), Electronics: smartphone raw measurements differ by device and real versus controlled environment; duty-cycling affects observable quality.

Paper records:
- https://consensus.app/papers/detecting-gnss-jamming-and-spoofing-on-android-devices-spens-lee/d2fde456ccf35769bced63db7618fc21/
- https://consensus.app/papers/monitoring-radio-frequency-interference-affecting-gnss-tegedor-gioia/bcf0988079ea5e35b6c7c4eadfda8785/
- https://consensus.app/papers/field-detection-of-gnss-interference-using-smartphones-at-tegedor-piccolo/7781aa91975353b59802702923c43c9a/
- https://consensus.app/papers/improving-gnss-spoofing-awareness-in-smartphones-via-rustamov-minetto/af8d00b23b2652f6a59fa6a0d7a38883/
- https://consensus.app/papers/a-controlledenvironment-quality-assessment-of-android-gogoi-minetto/c25559219d2b5c99b0c3a9a3b40cdc5b/

## Evaluation gates

1. Run clean baseline on canonical source and note all pre-existing failures/warnings.
2. TDD each admission path (bounded capacity, closed channel, construction error, zero-measurement event, mixed event with N measurements, collection fan-out) with a failing test observed before implementation.
3. Verify payload equivalence and no queue/sequence/session-lifecycle behavior changes.
4. Run focused tests, then full `:app:testPrimaryDebugUnitTest` and `:app:assemblePrimaryDebug`; `git diff --check`; review diff.
5. Do not claim scientific detector performance until device/session-separated baseline/controlled anomaly datasets and independent ground truth exist. Proposed false alarm/recall/latency thresholds remain targets, not results.

## Safety and claims

This is defensive observation/diagnostics. Do not build or transmit a jammer. Any RF tests require authorization and shielded lab conditions. Software cannot guarantee persistent satellite tracking or positioning under effective RF denial. Keep `INTERFERENCE_SUSPECTED`, `SPOOFING_SUSPECTED`, `RECEIVER_OR_PIPELINE_DEGRADED`, `INSUFFICIENT_DATA`, `RECOVERING` and `PNT_UNAVAILABLE` conceptually separate. Preserve fail-closed session integrity.
