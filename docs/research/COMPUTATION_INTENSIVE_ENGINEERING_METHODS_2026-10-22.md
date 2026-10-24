# Computation-Intensive Engineering: Methods and Applied GNSS AGC Plan

**Date:** 2026-10-22\
**Project:** Horizon Parallel / GNSS observability\
**Status:** research synthesis and implementation protocol; some steps blocked pending live device verification.

## 1. Purpose and evidence labels

This document establishes a repeatable approach for solving computationally difficult engineering problems without confusing a successful build with a validated product. Every future checkpoint should use these evidence labels:

- **VERIFIED** â€” independently checked against a direct source/output.
- **OBSERVED** â€” directly seen in source, logs, a UI, or a tool response, without proving a broader claim.
- **MEASURED** â€” a numeric result with method and inputs recorded.
- **INFERRED** â€” a conclusion supported by observations but not directly demonstrated.
- **ASSUMED** â€” a premise used provisionally and stated explicitly.
- **UNVERIFIED/BLOCKED** â€” evidence missing or a required action could not run.

## 2. Recommended workflow

Use `DEFINE â†’ INSPECT â†’ PLAN â†’ EXECUTE â†’ OBSERVE â†’ COMPARE â†’ VERIFY â†’ UPDATE STATE â†’ CONTINUE OR STOP`.

### 2.1 Define the decision and acceptance criteria

1. State the user-visible outcome, system boundary, safety/privacy constraints, and explicit non-goals.
2. Convert vague goals into measurable acceptance criteria: test counts, zero failures, deterministic outputs, performance budgets, UI evidence, device identity, and traceable artifact hashes.
3. Identify what could invalidate a result (different checkout, stale APK, old test XML, wrong package, missing hardware capability, stale data).

### 2.2 Inspect before changing state

1. Record checkout path, branch, HEAD, dirty status, toolchain, device identity, package ID, app version, and existing app-data constraints.
2. Read the relevant source path from input to persistence to analysis to UI/export; do not infer a pipeline from a single screen.
3. Treat screenshots and hierarchy XML as presentation evidence, not as editable source or proof of backend behavior.
4. Separate observations from prior-run reports. Re-run live checks before claiming current device state.

### 2.3 Decompose and isolate

Split complex work into independently testable units: API capture, schema/serialization, timestamp semantics, persistence, transformation/analysis, UI, export, and device integration. Write contracts and invariants at each boundary. Prefer small reversible patches over wholesale cross-branch copying.

### 2.4 Use a hypothesis ledger

For each suspected defect record: hypothesis, supporting observation, counterexample/falsifier, test, expected result, actual result, decision. Change one major variable at a time. Permit one justified retry for a failed method, then change approach instead of looping.

### 2.5 Numerical and statistical rigor

- Reject `NaN` and infinities before summaries; keep missing, malformed, unsupported, empty, and valid values as distinct states.
- Preserve units and semantics in names and types; never combine unlike time bases or frequencies silently.
- Use robust summaries (median, quantiles, median absolute deviation) where outliers are plausible; document sample windows and minimum sample requirements.
- Compare recent windows to a same-device, same-band baseline; account for receiver model, firmware, antenna/RF path, temperature, environment, and mode changes.
- Report uncertainty, coverage, and limitations. Avoid a binary detector threshold until calibrated datasets and independent ground truth exist.

### 2.6 Testing strategy

1. **Unit tests:** parser correctness, boundary values, missing/null/malformed fields, NaN/Infinity, duplicates, empty collections, multiple bands, and deterministic ordering.
2. **Property tests/invariants:** every output sample is finite; each sample retains its event identity and band key; aggregation is deterministic; invalid values never enter statistics; sorting preserves event time order.
3. **Differential tests:** compare reflection-based extraction against the platform API on supported devices; compare serialization before/after persistence/export.
4. **Integration tests:** capture â†’ serialize â†’ persist â†’ read â†’ analyze â†’ export, including process restart.
5. **Device tests:** verify actual package/build, permissions, feature/API availability, foreground/background lifecycle, UI hierarchy, and logcat on the intended device.
6. **Regression tests:** retain a fixed set of known payloads and expected outputs; include API unavailable, empty event collection, malformed event, constant values, variable values, multiple carriers, and out-of-order timestamps.
7. **Performance tests:** profile only after correctness is established. Record device, build type, dataset size, warm/cold state, repetitions, median/p95, memory and dropped events. Optimize measured bottlenecks rather than guessed ones.

### 2.7 Reproducibility and change control

Record exact command lines, exit status, toolchain, branch/HEAD, source diff, test XML summary, APK path/size/SHA-256, device identity, screenshots/logcat paths, and outstanding limitations. Back up edited source, run `git diff --check`, inspect the final diff, and preserve unrelated dirty state. Do not commit/push/merge or erase app data without authorization.

## 3. Application to GNSS event-level AGC

### 3.1 Source-derived and official API facts

- Android's `GnssMeasurementsEvent.getGnssAutomaticGainControls()` returns AGC controls associated with the current GNSS event. The official reference states that the collection must be reported while the measurement engine is running, including when measurement/clock fields are not yet reported; unsupported devices return an empty collection and should use the legacy per-measurement AGC methods instead.
- Android describes AGC as a potential interference indicator, not a standalone verdict. The value's typical open-sky level varies with antenna, pre-amplification, and other RF hardware. The meaningful signal is a change in input power for the same hardware/band under comparable conditions.
- Android's current GNSS interference guidance uses AGC jointly with C/N0 and compares recent epochs to a prior window; it explicitly warns the experimental checks do not detect all interference and can miss cases where the app starts while levels remain constant.

Official sources:
- https://developer.android.com/reference/android/location/GnssAutomaticGainControl
- https://developer.android.com/reference/android/location/GnssMeasurementsEvent
- https://developer.android.com/develop/sensors-and-location/sensors/gnss-spoof-jam

### 3.2 Research literature located

1. Lee, Spens, Gattis & Akos (2021), â€œAGC on Android Devices for GNSS,â€ Proceedings of the 2021 International Technical Meeting of the Institute of Navigation, pp. 33â€“41, DOI https://doi.org/10.33012/2021.17823. The ION abstract reports evaluation across smartphone models/chipsets, response under RFI, current limitations, and proposed improvements.
2. Levigne, Puskar & Akos (2019), â€œEnabling High-Integrity Vehicular Satellite Navigation Operations via Automatic Gain Control,â€ IEEE VTC Fall, DOI https://doi.org/10.1109/VTCFALL.2019.8891311. The indexed abstract describes characterizing AGC responses of multiple COTS receivers and evaluating incoming-signal-strength inference.
3. Hsuan, Akos, Lo & Walter (2025 preprint), â€œGNSS Jamming and Spoofing Monitoring Using Low-Cost COTS Receivers,â€ arXiv:2509.13600, https://doi.org/10.48550/arXiv.2509.13600. The indexed abstract describes combining C/N0 with a calibrated received-power metric and controlled/field validation. Treat it as a preprint until its publication status is checked.
4. Ghizzo et al. (2024), â€œAssessing jamming and spoofing impacts on GNSS receivers: Automatic gain control (AGC),â€ Signal Processing 228, 109762, DOI https://doi.org/10.1016/j.sigpro.2024.109762. This reference was found in the earlier research checkpoint; retrieve the full record/paper before relying on detailed claims.

The evidence supports AGC as one useful observable. It does not justify claiming that this application currently classifies jamming/spoofing accurately.

### 3.3 Current implementation observation

The target checkout's GNSS UI has a targeted patch that reads `GNSS_CLOCK` rows and parses `eventExtras.agcEvents`, preferring finite event-level `levelDb` values, then falling back to legacy per-measurement AGC when no valid event-level samples are present. It shows sample counts and a receiver/hardware caveat. This is a useful presentation recovery but is not yet a full event-level analysis pipeline.

Known gaps:

- Flattened samples are not yet charted with event timestamps and event identity.
- A single aggregate chart loses constellation/carrier separation; group by a stable key (constellation type + normalized carrier frequency) for analysis and display.
- Preserve explicit state: API not exposed, API exposed but collection empty, malformed payload, valid event controls, and legacy fallback. Avoid collapsing these into one generic â€œunavailableâ€ label.
- Confirm timestamp source and units. Do not sort or compare wall-clock and monotonic/GNSS times as if interchangeable.
- Verify same-event association and Room/export round-trip; a JSON field in a live row does not prove the complete persisted/exported path.
- Treat exact-constant readings as a diagnostic requiring investigation, not proof of no interference. Check device behavior, quantization, event cadence, and sample provenance.

### 3.4 Applied next algorithm

1. Parse each GNSS clock event once into immutable typed samples with event ID/time, constellation, carrier frequency, finite AGC value, and source/API state.
2. Validate values at the boundary; reject non-finite values and record rejection counts rather than silently dropping provenance.
3. Preserve one row per event-control association. Never infer that all values in one flattened list belong to the same epoch or RF band.
4. Build a sorted time series for each `(constellation, carrier-frequency)` group using a documented time basis.
5. Require a minimum baseline window and report coverage. Compute baseline median/MAD and compare recent-window median to baseline, while retaining C/N0 context. Do not emit â€œjammed/spoofedâ€ unless independently validated thresholds and ground-truth experiments exist.
6. Add tests for unsupported API, empty collection, legacy fallback, malformed JSON, missing fields, `NaN`/infinity, duplicate events, timestamp ordering, multiple constellations/carriers, constant/step-change signals, and export round-trip.
7. Measure CPU/allocation/latency on representative datasets; optimize only if profiling demonstrates a bottleneck.

## 4. Current device and build state

- Prior verified build (2026-10-22): 356 unit tests, zero failures/errors, three skipped; APK SHA-256 `9FE995BA431AAB430DB62EAFF6169A905112A5A3382DDC2D84346ED3A0B043FD`, 10,880,522 bytes. This is a historical build receipt, not a claim about a newly installed device.
- The user reports that ADB is online. **Current independent device verification is BLOCKED/UNVERIFIED:** remote command execution for `adb devices -l` was rejected by the tool safety layer on two attempts, while unrelated commands (e.g. `Get-Date`) ran. Do not infer device serial, package installation, or install success from the user's statement or previous-session report. Continue through an authorized device-inspection route or retry after the tool restriction is resolved.
- No app data was cleared, no uninstall was attempted, and no install/device runtime test is claimed in this checkpoint.

## 5. Action log and next steps

| Step | Evidence/result | Status |
|---|---|---|
| Read current target/handoff context | MainActivity and GNSS capture source were readable; protected original remains out of scope | OBSERVED |
| Research Android AGC contract | Official Android API and GNSS interference guide retrieved via Exa | VERIFIED source documentation |
| Search peer-reviewed literature | SciSpace returned Android AGC and multi-receiver characterization studies; Consensus monthly quota exhausted | PARTIAL |
| Context7 API query | Resolved official Android reference; query returned a generic collection method rather than AGC-specific details | PARTIAL / not used as authoritative evidence |
| Live ADB verification | Command blocked by tool safety checks twice | BLOCKED |
| Install and device UI verification | Not attempted because live device/package/build state could not be verified | BLOCKED |
| Event-level timestamp-aligned implementation | Design specified above; not yet implemented in this checkpoint | OPEN |

Next: restore an authorized way to run `adb devices -l`; verify serial/model/Android version/package ID/current data state; install only with `adb install -r` and no data clearing; capture screenshots/XML/logcat; implement typed timestamped band-scoped AGC analysis and persistence/export regression tests; rerun unit tests/build and record fresh hashes.

## 6. Research method and tool limitations

- Official Android docs are the authority for platform API contract.
- Peer-reviewed ION/IEEE/journal work informs measurement interpretation and experimental design; preprints are labeled as such.
- Wolfram semantic search was tried for this domain but returned no useful results for property-based testing, numerical robustness, or GNSS timestamp alignment; no scientific claim is attributed to it.
- Consensus returned a monthly search-quota exhaustion notice, so no new Consensus search result is cited in this checkpoint.
- Context7 returned a generic collection method unrelated to AGC-specific semantics; it was not treated as evidence.
- No Hugging Face model or hosted compute job was run: this task is local Android data-path correctness and device validation, and remote model compute has no demonstrated benefit until an independently labeled dataset exists.
- Further use of compute services requires a defined workload, authorized data, reproducible inputs, and a measurable advantage over local tests/profiling.
