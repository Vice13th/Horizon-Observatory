# GNSS Interference Detection and Resilient PNT — Research & Implementation Roadmap

**Date:** 2026-10-22  
**Status:** research-backed proposal; implementation and field validation remain open  
**Target:** Android observatory / defensive GNSS diagnostics  
**Safety boundary:** detection, evidence capture, receiver-health reporting, and non-radiating resilience only. No jammer construction, transmission, or offensive countermeasure instructions.

## Executive decision

Build a calibrated, multi-indicator interference/anomaly monitor first, not a binary “jamming detector” driven by one threshold. In parallel, improve acquisition telemetry and backpressure accounting, because the recent field exports show that missing observations can invalidate the evidence used by a detector. Only after detection is independently validated should the app add resilience guidance and a navigation-quality state machine.

No Android app can guarantee persistent satellite lock when received GNSS signals are overwhelmed by effective RF interference. Android application APIs generally expose receiver outputs, not raw RF samples or control of chipset tracking loops. The achievable objective is: detect probable degradation early, preserve trustworthy evidence, report uncertainty and PNT quality, and transition safely to independent or dead-reckoned sources when available.

## Evidence and references

1. **Spens, Lee, Nedelkov & Akos (2022), “Detecting GNSS Jamming and Spoofing on Android Devices,” NAVIGATION 69(3).** Demonstrates Android-native detection methods using GNSS/network location comparison, mock-location flag, GNSS/system-time comparison, and AGC plus carrier-to-noise density. It is a practical baseline, not proof that fixed thresholds generalize to every phone. https://navi.ion.org/content/69/3/navi.537
2. **Android Developers — Detect GNSS jamming and spoofing.** Official Android guidance describes the threat categories and Android-based indicators. https://developer.android.com/develop/sensors-and-location/sensors/gnss-spoof-jam
3. **Android Developers — Raw GNSS measurements.** Documents device/API variation and measurement availability. Raw measurement support is required on Android 10+ devices, while fields and constellations still vary by implementation. https://developer.android.com/develop/sensors-and-location/sensors/gnss
4. **Android API reference — GnssMeasurement.** The per-measurement AGC accessor is deprecated from API 33; use GnssMeasurementsEvent.getGnssAutomaticGainControls() where supported. AGC is only reported with receiver measurement events, and values are hardware-dependent. The docs describe a stronger in-band interference signal as typically driving AGC more negative; missing AGC is not evidence of no interference. https://developer.android.com/reference/android/location/GnssMeasurement
5. **Miralles et al. (2018), “Android Raw GNSS Measurements as the New Anti-Spoofing and Anti-Jamming Solution.”** Explores combining Android measurements, AGC/C/N0, network location, inertial sensors, and pseudorange residuals. https://doi.org/10.33012/2018.15883
6. **Android GNSS Logger / Google GNSS analysis tools.** Useful for reproducible capture and post-processing of raw measurements; treat it as a data-collection aid, not ground truth. https://developer.android.com/develop/sensors-and-location/sensors/gnss

## What can be observed on Android

| Indicator | Value | Main limitation / interpretation |
|---|---|---|
| Event-level AGC by band/frequency | Potentially strong in-band interference indicator | API 33+ path differs; OEM/chipset/antenna/preamp scale and sign conventions must be characterized. May be absent precisely when signals are not tracked. |
| Per-satellite C/N0 | Signal-quality trend and per-constellation/frequency context | Also falls with obstruction, body/hand attenuation, antenna orientation, foliage, and multipath. |
| Tracked/used satellites and constellation diversity | Receiver degradation / correlated loss evidence | Depends on geometry, visibility, chipset and location-engine policy; never interpret a count alone as jamming. |
| Measurement and navigation-message continuity | Tracking/acquisition health | Missing callback versus no satellite measurement are different failure modes. |
| Pseudorange, pseudorange-rate, ADR/carrier phase and receiver clock fields | Residual, clock-jump, and consistency checks when fields are valid | Biases, discontinuities, cycle slips, time semantics and device-specific field support require explicit quality masks. |
| GNSS location versus network/provider location | Independent plausibility comparison | Network location may be stale, coarse, unavailable, or share other dependencies; compare age, uncertainty and coordinate frames. |
| GNSS time versus system/network time | Time anomaly signal | System clock can itself be wrong or manually changed; use monotonic time and uncertainty. |
| IMU, barometer and motion state | Context for attenuation/multipath and short-term dead reckoning | Consumer IMUs drift; they do not recreate satellite observables or guarantee long-term position. |
| Acquisition queue drops and persisted-count reconciliation | Evidence completeness | This is a software/data-integrity metric, not an RF-interference metric. Keep it separate from signal classification. |

## Detector architecture

### 1. Preserve evidence and completeness

- Instrument callback → collector → queue/admission → persistence counters per source and per time window.
- Record callback receipt time, monotonic elapsed time, GNSS measurement time, queue depth, drop reason, and persistence acknowledgement separately.
- Maintain fail-closed session completeness: a clean stored sequence is not proof that upstream callbacks were all admitted.
- Add bounded backpressure and an explicit DROPPED/OVERLOADED state; do not silently discard or relabel a capture as complete.
- Detector output must be UNKNOWN/INSUFFICIENT_DATA when event rate, coverage, or measurement freshness is below its declared minimum.

### 2. Build a feature window, not a threshold-only alert

For each rolling window (initially 5–10 seconds, tunable by evaluation), derive robust baselines per device, RF band/frequency, and constellation where data permits:

- median and robust spread (MAD/quantiles) of AGC and C/N0; deltas relative to a stable local baseline;
- fraction of tracked satellites lost, rate of C/N0 decline, and correlation of losses across constellations;
- AGC/C/N0 joint behavior by band; never pool incomparable hardware scales without normalization;
- measurement-age, callback gaps, event-level AGC availability, queue-loss rate and completeness flags;
- location/time consistency residuals only where independent reference age and uncertainty are known;
- motion/obstruction context from IMU and user-visible environmental state, where available.

Prefer robust per-device baselines and a transparent rule-based score initially. A statistical classifier may follow only after a labeled dataset exists. An anomaly score is not a probability unless calibrated on held-out, device-separated data.

### 3. Separate states and confidence

Recommended output state machine:

- NORMAL: evidence is fresh, coverage adequate, no meaningful multi-indicator anomaly.
- DEGRADED_SIGNAL: signal quality/visibility is falling, cause not attributable.
- INTERFERENCE_SUSPECTED: independent signal-quality indicators agree (for example, abnormal AGC movement plus broad C/N0 decline) over a sustained window.
- SPOOFING_SUSPECTED: separate consistency alarms (location/time/measurement residuals) disagree; do not infer this from AGC alone.
- RECEIVER_OR_PIPELINE_DEGRADED: receiver events or application admission/persistence are incomplete.
- INSUFFICIENT_DATA: capabilities/coverage/baseline do not support a conclusion.
- RECOVERING: evidence is improving but not yet stable.
- PNT_UNAVAILABLE: no usable trusted fix/source; preserve last-known fix only with age and uncertainty clearly shown.

Expose indicators, time window, capability availability, confidence/calibration status, and reason codes. Do not collapse “suspected interference,” “spoofing suspicion,” “poor sky visibility,” and “software dropped data” into one red status.

## Resilient connectivity and PNT

A phone app cannot force satellite tracking to continue through sufficient RF denial. The resilience target is graceful degradation and trustworthy state, not guaranteed lock.

1. **Receiver/environment first:** give actionable, non-technical advice to move to open sky, avoid covering the antenna region, change handset orientation, and reduce local obstruction; measure time-to-recovery rather than promise improvement.
2. **Use available diversity:** report the actual supported constellation/frequency mix; keep independent bands separate where the receiver exposes them. Multi-constellation helps only if affected signals/paths differ and the chipset provides them.
3. **Assisted GNSS/network:** use Android's normal location providers and report provider, age, uncertainty, and source independence. Network location is not a GNSS substitute with guaranteed integrity.
4. **Short-term dead reckoning:** optionally fuse accelerometer/gyroscope and step/motion models with explicit drift growth and expiry. Do not label extrapolated positions as fresh GNSS fixes.
5. **Quality-aware PNT manager:** source priority should depend on integrity, age, uncertainty and continuity, not merely availability. On GNSS anomaly, flag the fix as degraded, preserve last trusted fix with timestamp, use independent sources if present, and recover only after a stability dwell period.
6. **Holdover/time quality:** distinguish GNSS-derived time from system time and estimated holdover; surface increasing uncertainty.
7. **No unsafe “lock preservation” claims:** never suppress an interference alert merely to keep the UI green, fabricate satellite observations, or continue presenting stale coordinates as current.

## Evaluation plan and acceptance gates

### Stage 0 — capability and data-contract audit

- Inventory API levels 26–36, GnssCapabilities, raw-measurement flags, event-level AGC, frequency coverage and vendor/device identifiers (privacy-preserving).
- Implement feature-level availability masks and unit tests for absent, NaN, stale, out-of-order and constant readings.
- Add source admission/drop counters and verify callback-to-persistence reconciliation under load.
- **Gate:** zero silent drops; every rejected event has a count/reason; integrity failures remain explicit.

### Stage 1 — baseline characterization

Collect multiple sessions per device in open sky, urban canyon, foliage, indoor/near-window, handset orientation changes, body/hand attenuation, and ordinary motion. Record device/OS/build, antenna orientation, weather/environment notes, session duration, source completeness and sensor/AGC capabilities. Avoid raw precise location publication.

- **Gate:** estimate per-device/per-band baseline variance and missing-data rates; document which devices cannot support each feature.
- Include both good and poor signal conditions that are not jamming to reduce false positives.

### Stage 2 — controlled benign perturbations

Use non-radiating tests first: attenuation/obstruction, orientation, motion, and replayed datasets. Any RF simulator or conducted RF test must be in a properly shielded/authorized lab under applicable law; do not transmit interference over the air.

- **Gate:** detector correctly reports generic degradation for ordinary attenuation/multipath; it must not call every loss of satellites “jamming.”
- Use independently controlled ground truth and record instrument calibration.

### Stage 3 — detector validation

Split by device and session, not random rows, to avoid leakage. Evaluate event-level and window-level precision/recall, false alarms per hour, detection latency, missed-event rate, calibration, abstention rate and performance under missing AGC. Report confidence intervals and device-specific results. Test baseline drift, thermal changes, firmware/OEM variation, API 32/33 AGC transition and API 26–28 field availability.

Proposed initial acceptance targets (to be agreed before test execution): false alarms below 1 per 10 device-hours in the defined benign test matrix; at least 90% recall for predeclared controlled anomaly classes; 95th-percentile detection latency under 10 seconds; explicit INSUFFICIENT_DATA when required channels are missing. These are engineering targets, not achieved results or universal safety thresholds.

### Stage 4 — resilience state machine

Test GNSS loss, network provider loss/staleness, permission denial, app backgrounding, process restart, IMU unavailability, clock changes, and recovery from intermittent signal. Ensure stale fix age/uncertainty increases visibly and that no fallback is mislabeled as GNSS.

- **Gate:** no stale fix appears current; source transitions and integrity warnings are auditable; recovery requires a stable dwell window and adequate data.

### Stage 5 — field validation

Only after lab/dataset gates: diverse open-sky and urban field sessions with an independent reference receiver or survey-grade truth, controlled logs, and preregistered test cases. No deliberate over-the-air jamming. Archive redacted summary metrics and hashes, not precise tracks or private raw DB/WAL/SHM.

- **Gate:** independent reviewer can reproduce metrics from the redacted dataset/analysis script; known failure cases and device limits are published.

## Priority implementation sequence

**P0 — Evidence completeness (first):** instrument callbackFlow/backpressure and queue admission; fix loss accounting and test overload/recovery. This is a prerequisite because a detector fed incomplete data can generate misleading alarms.

**P1 — Capability-aware signal telemetry:** modern event-level AGC path (API 33+), legacy fallback behind API guards, per-band C/N0/AGC summaries, measurement availability and stale-data handling. Unit-test sign, units, missingness and hardware-specific baseline semantics.

**P2 — Explainable anomaly engine:** rolling robust baselines, independent indicators, sustained-window/hysteresis logic, explicit unknown state and structured reason codes. Keep detection separate from presentation and from final session integrity.

**P3 — PNT quality manager:** age/uncertainty-aware GNSS/network/IMU source states, safe stale-fix semantics, optional short-horizon dead reckoning and holdover quality.

**P4 — Dataset and validation harness:** versioned schema, privacy-redacted fixtures, device/session split, controlled anomaly labels, metric report and reproducible thresholds.

**P5 — Optional ML research:** only after adequate labeled diversity; compare against transparent baselines, evaluate out-of-device generalization, calibrate probabilities, and preserve abstention. Do not ship a black-box classifier without reproducible evidence.

## Risks / explicit unknowns

- Recent local field audit measured flat 6 dB AGC across three groups on one Samsung device. This is not evidence of a universal three-band limit or of a calibrated AGC response.
- AGC availability and semantics differ by API level, vendor, antenna and RF front-end; event-level AGC may not be available on every target.
- Signal degradation can arise from obstruction, multipath, antenna orientation, receiver behavior, or application data loss. Classification requires independent context.
- Android raw GNSS does not generally expose raw RF samples, receiver correlator state, or controls that can guarantee tracking under interference.
- No independent ground-truth interference dataset or controlled RF result is currently claimed for Horizon.
- The current local field-fix candidate is a separate no-Git source copy; this document is a proposed path, not evidence that detector/resilience code has been implemented in the canonical remote repository.

## Decision

Proceed with P0 and P1 immediately, then P2 with a transparent baseline detector. Do not start ML-first work or promise “persistent satellite lock.” Keep integrity accounting, interference detection, spoofing suspicion, and PNT fallback as distinct state machines with explicit provenance and uncertainty.

## Source links

- Android GNSS raw measurements: https://developer.android.com/develop/sensors-and-location/sensors/gnss
- Android jamming/spoofing guidance: https://developer.android.com/develop/sensors-and-location/sensors/gnss-spoof-jam
- GnssMeasurement API and AGC deprecation: https://developer.android.com/reference/android/location/GnssMeasurement
- Spens et al., 2022, NAVIGATION: https://navi.ion.org/content/69/3/navi.537
- Miralles et al., 2018, DOI: https://doi.org/10.33012/2018.15883
