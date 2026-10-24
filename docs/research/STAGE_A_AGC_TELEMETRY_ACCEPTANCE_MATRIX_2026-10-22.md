# Stage A GNSS AGC Telemetry Acceptance Matrix
Date: 2026-10-22
Status: executable acceptance plan; not a report of completed tests. No RF experiment was performed.

## Objective and boundary

Establish that the GNSS automatic-gain-control (AGC) observation path preserves source, time, band/constellation identity, missing-data semantics, malformed-entry diagnostics, persistence/export integrity, and consistency between analysis and UI before any controlled RF evaluation. App installation or a constant AGC trace is not an interference-detection validation.

## Current implementation observed

| Layer | Observed implementation | Acceptance implication |
|---|---|---|
| Capture | `app/src/main/kotlin/horizon/observatory/acquisition/gnss/GnssClockCapture.kt` uses reflection for `getGnssAutomaticGainControls()`, stores `agcApi` and `agcEvents`, and retains valid items when another item fails. | Verify unsupported method, empty collection, valid collection, throwing getters and partial failures. Capture-time dropped entries are currently not given their own explicit counter. |
| Parsing | `analysis/integrity/IntegrityInputs.kt` reads `GNSS_CLOCK.eventExtras.agcEvents`; missing/non-finite levels are not converted to zero; diagnostics count events, missing arrays, empty arrays, malformed entries, invalid levels and valid controls. | Exercise every diagnostic category and verify count conservation across persisted rows. |
| Indicator | `analysis/integrity/GnssIntegrityIndicators.kt` uses per-constellation / carrier-MHz groups and segments on clock discontinuities/full-tracking transitions. It explicitly calls the thresholds uncalibrated and says outputs do not establish integrity. | Verify group isolation, segment cuts, minimum sample gates, confounder annotations and no threat attribution. |
| UI | `ui/MainActivity.kt` prefers event-level values, falls back to legacy measurement AGC, creates per-group plots and reports telemetry availability/state. | Compare UI source/state/counts to parsed evidence, including mixed event-level/legacy records. |
| Summary analysis | `analysis/SessionAnalysisEngine.kt` currently derives `AnalysisSnapshot.agcSampleCount/min/max/spread/state` from `GNSS_RAW_MEASUREMENT.automaticGainControlLevelDb` (legacy per-measurement field). | **Review gap:** with only event-level AGC present, integrity/UI can have values while the general session summary remains unavailable. Define whether this divergence is intended or unify the data contract with tests. |
| Group-key consistency | The indicator helper rounds carrier frequency to nearest MHz; the UI currently truncates MHz using `toInt()`. | **Review gap:** frequencies near half-MHz boundaries can map differently. Add parity vectors or use a shared band-key function. |

## Test matrix

| ID | Test fixture / action | Pass criterion | Existing evidence / state |
|---|---|---|---|
| A01 | API lacks `getGnssAutomaticGainControls`; reflective lookup throws | Capture records unavailable/not-exposed, not an empty-but-supported measurement; no callback crash | Partial path exists; dedicated test still required |
| A02 | Method returns empty collection | `agcApi=EVENT_LEVEL`, empty array; parser records an empty collection event | Parser diagnostics exist; end-to-end assertion required |
| A03 | Collection has two valid controls and one malformed/throwing control | Valid controls survive; rejected-control count is observable; malformed item does not erase siblings | Valid siblings are retained; explicit capture-drop count is a gap |
| A04 | Level is missing, NaN, infinite, string or null | Rejected or flagged invalid; never silently replaced with 0 dB | Parser rejects missing/non-finite level; all capture representations need fixture coverage |
| A05 | Frequency missing, non-finite, zero, negative or valid | Invalid frequency remains unknown; valid frequencies map to stable, documented constellation/band keys | Unknown handling exists; boundary and cross-layer parity tests required |
| A06 | Payload has no `eventElapsedRealtimeNanos` but has observation monotonic time; then both are absent | First row uses monotonic fallback; second is excluded and counted as skipped/untimed; never align using wall clock | Timestamp fallback observed in parser; loss/skip count needs explicit assertion |
| A07 | Two constellations / bands shift independently with otherwise similar levels | Analysis series and UI groups remain separate; a shift in group X does not contaminate group Y | Existing unit coverage includes `eventLevelAgcDoesNotPoolDistinctCarrierBands`; extend to UI grouping parity |
| A08 | Carrier MHz values on both sides of a rounding boundary | UI and integrity analyser choose the same band key | **Gap:** current UI truncates while analyser rounds |
| A09 | Clock discontinuity count changes or full-tracking transitions | Robust scan flushes/separates segments; no derivative/window crosses the boundary | Segmenting code observed; explicit boundary vectors required |
| A10 | Event-level values exist but legacy per-measurement AGC is null; then reverse | UI provenance and summary provenance are explicit and consistent with the chosen contract | UI prefers event-level; general summary still reads legacy values; decision/test required |
| A11 | Persist, close/reopen, JSON/SQLite export and import | Event timestamp, level, constellation, carrier, API/source state and diagnostics survive round-trip; row/event counts reconcile | Existing `ExportEngineAgcRoundTripInstrumentedTest` passed on Android 16; extend coverage to diagnostics/source-state if not already present |
| A12 | Baseline shorter than minimum; unsupported AGC; all values constant; malformed-only event series | Return unavailable/insufficient-history/constant-within-baseline as appropriate, never interpret missing data as clean RF | Some statuses exist; complete acceptance assertions required |
| A13 | Android 26-32 legacy path versus Android 33+ event-level path, including API 26-28 receiver-clock timing | Correct supported path is selected on physical devices; timestamps remain monotonic and source-labelled | Android 16 device test only; API 26-28/32 physical matrix remains open |
| A14 | Benign obstruction, phone orientation, motion, multipath and reacquisition (independently labelled, no intentional RF disturbance) | Cases are labelled as confounders and reported separately; no accuracy claim unless independent truth is available | Protocol defined; not executed in this build turn |

## Timestamp and delivery-loss acceptance additions â€” 2026-10-22

These cases refine A06/A09/A13; they are **acceptance criteria**, not claims of completed runtime validation.

| ID | Fixture / action | Pass criterion | Evidence state |
|---|---|---|---|
| A15 | Receiver `GnssClock.elapsedRealtimeNanos` is advertised unavailable on API 29+ | Use callback `SystemClock.elapsedRealtimeNanos`; persist `CALLBACK_RECEIPT_CLOCK_FIELD_UNAVAILABLE`; do not call the unavailable receiver accessor | Existing pure selector tests cover this selection; framework runtime still open |
| A16 | API 26â€“28 callback | Never access API-29 receiver elapsed-realtime accessors; use callback receipt monotonic time and persist `CALLBACK_RECEIPT_API_UNAVAILABLE` | Existing pure selector test at API 26 and API 28; physical API 26â€“28 testing open |
| A17 | API 29+ receiver elapsed-realtime is advertised available | Use the receiver elapsed-realtime value and persist `GNSS_CLOCK_ELAPSED_REALTIME`; preserve its distinct provenance from callback receipt time | Existing pure selector test; OEM runtime validation open |
| A18 | Two adjacent `GNSS_CLOCK` rows differ in `hardwareClockDiscontinuityCount` | Record the change, segment continuity/drift analysis before computing cross-row deltas, and never infer a clock anomaly from a derivative crossing the reset | Unit coverage has one count transition; add explicit segment metrics/count assertions if downstream analysis relies on the count |
| A19 | Flow/channel is closed or cannot accept callback-derived observations (clock, each raw measurement, status, navigation, antenna) | Check every `trySend` result without throwing from Android callbacks; count attempted/accepted/rejected rows per observation type, distinguishing channel-full/backpressure from closed/cancelled flow; retain callback-event count separately because one measurements event emits 1 clock row + N measurement rows; finalize counters after callback unregistration, attach immutable session-scoped diagnostics, and export them in the manifest, text log, and SQLite session record with round-trip tests. Reconcile per type: attempted = accepted + rejected. Separately reconcile accepted/enqueued versus consumer/Room persistence; do not name a `trySend` success â€œpersisted.â€ | **PARTIAL / ACCOUNTING GATE OPEN:** per-type attempt/accepted/rejected/full/closed counters, session metadata persistence, manifest/log/SQLite serialization, and archive counter-invariant validation exist in the local dirty worktree. `accepted` currently means successful `trySend`/flow-channel acceptance, not proven Room persistence. The uploaded app session audit found 27 accepted raw rows and 1 accepted status row absent from persisted SQLite observations. Archive validation checks total rows and diagnostics-object parity but not per-type accepted-to-persisted parity. Exact loss stage is **UNVERIFIED**. See `CHECKPOINT-2026-10-24.md` and GitHub issue #3. Do not close A19 until accepted/enqueued, consumed, filtered, persisted and export semantics are separately specified and the 28-row gap is reproduced/reconciled. |
| A20 | Source time regresses while callback-receipt monotonic time increases, plus interleaved GNSS status/nav rows | Audit per observation source/type and event identity; never use a cross-type aggregate ordering count as proof of same-stream clock regression | Protocol requirement; not executed against fresh handset data in this checkpoint |

### Timestamp handling design constraints

- Store the original `GnssClock.timeNanos`, `fullBiasNanos`, `biasNanos`, `hardwareClockDiscontinuityCount`, receiver elapsed-realtime when valid, callback receipt elapsed-realtime, and the selected timestamp source without conflating their time domains.
- Android documents `hardwareClockDiscontinuityCount` as a reset boundary: when it changes, continuity assumptions about `TimeNanos` and the bias offset must be reset. This requires segmentation, not silent sorting or interpolation across the boundary. Source: [Android `GnssClock` reference](https://developer.android.com/reference/android/location/GnssClock).
- `SystemClock.elapsedRealtimeNanos()` is monotonic and includes deep sleep; it is appropriate for callback-receipt interval/index timing, but it is not the GNSS receiver's measurement epoch. Source: [Android `SystemClock` reference](https://developer.android.com/reference/android/os/SystemClock).
- Do not add a local drop counter to a single payload and call it complete telemetry: a durable counter must be scoped to the acquisition session, persisted through exports, and reconcile attempts = accepted + rejected/dropped with tests.

## Definition of telemetry completion

Stage A is complete only when:
1. A01-A06 and A09-A12 have deterministic unit/instrumented results, including input/output examples and exact build/test command.
2. A07-A08 are checked across all supported constellation/band group keys and the UI/analyser key function is made consistent or an explicit documented reason is approved.
3. A10 has a documented decision on whether `AnalysisSnapshot` aggregates event-level and legacy values separately or exposes a canonical provenance-tagged series.
4. A13 has device/API evidence; unsupported devices show unavailable rather than zero or a false benign state.
5. Raw inputs, build hash, device model/API, report hashes and failure/skip counts are archived. Skipped gates are reported as skipped, not passed.

## Source-grounded caveats

- Android Developers calls its GNSS spoof/jam feature experimental and says it does not detect every case: https://developer.android.com/develop/sensors-and-location/sensors/gnss-spoof-jam
- Spens et al. (2022), DOI 10.33012/navi.537, evaluate Android-native AGC/C/N0 indicators; thresholds and available AGC channels vary by receiver.
- Axell et al. (2015), DOI 10.1002/navi.74, evaluate jamming detectors under receiver-specific field trials; do not transfer another detector's thresholds to Horizon.
- Horizon AGC/RFI sensitivity, specificity, false alarms and alert latency remain UNVERIFIED until locked held-out tests against independent calibrated RF ground truth. No RF transmission is part of this Stage A plan.

## Implementation result update - 2026-10-22

| Matrix items | Result |
|---|---|
| A07-A08 carrier/band grouping parity | **PASS:** both UI and integrity analysis now call `GnssCarrierBand.bucketMHz`; nearest-MHz, half-MHz boundaries and invalid values have unit tests. |
| A10 session-summary source parity | **PASS:** `SessionAnalysisEngine` prefers finite event-level control levels if present and falls back to finite legacy measurement levels only if event-level samples are absent. `AnalysisSnapshot.agcSource` labels the selected input and is shown in UI/report. |
| Summary/report regressions | **PASS:** tests cover event-level-only, event-level priority over legacy values, legacy-only source labels and GNSS Markdown report source/summary values. |

**Verified current test evidence:** Primary JVM and Parallel JVM reports each show 56 suites, 372 tests, zero failures/errors, 3 skipped. Connected Parallel Android instrumentation HTML shows 22 counted tests, zero failures, 2 skipped. Synthetic migration tests pass; authentic Room v5/v6 baseline tests remain skipped. Remaining matrix items are not claimed complete.
