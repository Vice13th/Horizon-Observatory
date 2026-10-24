# Horizon Parallel â€” Scientific AGC/RFI Validation Execution Plan
Date: 2026-10-22
Status: PLAN ONLY â€” no new RF experiment conducted; detector performance remains UNVERIFIED.

## 1. Objective and claim boundary

Evaluate whether Horizon's per-device, per-band GNSS telemetry and current alert logic can distinguish labelled RF-interference episodes from common non-interference causes of signal change. The current app build and device startup are software evidence only. Do not infer sensitivity, specificity, false-alarm rate, or detection latency from a successful build, natural reception, a constant AGC level, or synthetic unit tests.

The test object must be the exact APK hash recorded in `APK_BUILD_AND_SCHEMA_RECOVERY_RECEIPT_2026-10-22.md`. Keep firmware/API level, device model/chipset, GNSS constellation/band, app commit, thresholds, and test-script versions in every record.

## 2. Evidence and implications

| Evidence | Observed design/result | Consequence for Horizon |
|---|---|---|
| Android Developers, [Detect GNSS jamming and spoofing](https://developer.android.com/develop/sensors-and-location/sensors/gnss-spoof-jam) | AGC and C/N0 changes are indicators; the example checks use moving epoch windows; Android labels the feature experimental and warns that it does not catch all cases. | Treat alerts as candidates for testing, not as definitive threat labels. |
| Spens et al. (2022), [Detecting GNSS Jamming and Spoofing on Android Devices](https://doi.org/10.33012/navi.537) | Multiple Android-native checks, including AGC/C/N0. AGC levels/thresholds vary by device; some phones share AGC across multiple constellation/band channels. | Calibrate per device and model shared-AGC dependencies; do not count shared readings as independent sensors. |
| Larsen et al. (2022), [Assessment of RFI Geolocation Using Modern Android Smartphones](https://doi.org/10.33012/2022.18571) | Seven phones and 13 tests, including four outdoor benchmarks and nine anechoic-chamber jamming tests; combined AGC/C/N0 method had no false detections in that study's benign benchmark. | Valuable precedent for a controlled test matrix, but the small study result is not transferable as Horizon's error rate. |
| Axell et al. (2015), [Jamming Detection in GNSS Receivers: Performance Evaluation of Field Trials](https://doi.org/10.1002/navi.74) | Receiver detectors differ by jammer characteristics; C/N0-only phone indicators can confound dynamics with interference. | Include normal motion, obstruction and multipath as separate negative controls, and stratify results by receiver/device. |
| Tegedor et al. (2026), [Monitoring Radio Frequency Interference Affecting GNSS Using Android Smartphones](https://www.mdpi.com/2673-4591/126/1/4) | Lab/field smartphone AGC monitoring; small AGC variations may not uniquely indicate GNSS interference and device models differ materially. | Record model-specific baselines and include ambiguous/noisy sessions rather than forcing every change into an RFI label. |

These sources provide study design context, not validated performance claims about Horizon.

## 3. Staged protocol

### Stage A â€” Instrumentation and telemetry integrity
1. Freeze and hash the APK, source commit, runtime settings, threshold/configuration values and test harness.
2. For every supported device/API, verify AGC values, C/N0, satellite counts, timestamps, event ordering, missing/empty/malformed state, constellation/band mapping and whether two channels share one physical AGC value.
3. Confirm that missing/unsupported telemetry is recorded as unavailable rather than replaced with zero or silently treated as benign.
4. Validate the alert-to-log trace end-to-end using deterministic software fixtures before collecting RF-labelled data.

### Stage B â€” Benign and confounder baseline
Collect independently labelled sessions spanning:
- stable open-sky stationary reception;
- normal walking/vehicle motion when permitted;
- changes in handset orientation;
- partial obstruction, indoor/outdoor transition and multipath-rich settings;
- satellite geometry, weak-but-benign reception, and receiver reacquisition;
- thermal/battery state or ordinary device changes where relevant.

Record duration, environment, movement, approximate sky/obstruction description, device temperature if available, and start/end markers. This set estimates benign false alarms; it must not be reused for final tuning after the held-out test is defined.

### Stage C â€” Controlled interference and ground truth
Run only under an institutionally authorized, calibrated shielded enclosure or conducted GNSS/RF setup. Do not radiate test signals in ordinary public environments. Use an independently logged reference receiver/spectrum measurement and calibrated signal source to label onset, offset, signal condition and test level. Randomize trial order and include sham/benign trials so the analyst cannot infer labels from run order. Keep jamming, spoofing/meaconing, benign attenuation/blockage and recovery/reacquisition as distinct labels only where the testbed can produce and verify them.

A reference instrument and calibrated stimulus establish ground truth; Android AGC/C/N0 must not be its own ground truth.

### Stage D â€” Locked, held-out validation
Split development and evaluation data by complete session and preferably by device model/chipset. Freeze thresholds before evaluating held-out data. Do not distribute adjacent epochs from a single session across training and testing. Evaluate on at least one device/model and condition not used during threshold tuning. Report degraded/no-data behavior separately.

## 4. Outcomes and statistics

Primary metrics, reported per class and per device/band stratum:
- event-level sensitivity/recall = TP/(TP+FN);
- specificity = TN/(TN+FP);
- precision/positive predictive value;
- false alarms per independent benign session and per device-hour;
- missed-event rate;
- onset-to-alert latency and recovery-to-clear latency;
- telemetry coverage, unsupported/missing data, and unusable-session fraction.

Use exact binomial or Wilson confidence intervals for independent session-level proportions; use session/device-clustered resampling if multiple correlated observations contribute to an episode. Do not count epochs as independent trials. Report confusion matrices and the denominator alongside every percentage; do not report a zero false-alarm rate merely because none occurred in a small sample.

For zero false positives among n independent benign sessions, the exact one-sided 95% upper bound is (1-0.05^{1/n}): n=59 gives 4.9508%, n=299 gives 0.9969%, and n=598 gives 0.4997%. These are sample-size planning calculations, not app measurements. A target bound must be chosen before testing by the responsible product/safety owner; the experiment should then be sized to that target and to expected event sensitivity.

## 5. Analysis guardrails

- Establish baseline/normalization per physical device and relevant band; published dB thresholds are starting hypotheses only, not universal constants.
- Track shared AGC channels and missing/unsupported API fields so correlated values are not double-counted.
- Treat dynamics, blockage, multipath, satellite geometry and recovery transients as possible confounders.
- Compare AGC jointly with C/N0 and available integrity signals; evaluate jamming and spoofing separately.
- Pre-register smoothing/window lengths, thresholds, alert-clear hysteresis, exclusions and score selection before held-out evaluation.
- Archive raw labelled event data, calibration records, test order randomization, analysis code/version, output hashes and device metadata in a controlled evidence bundle.
- Have an independent reviewer audit labels and analysis before any external performance claim.

## 6. Stop/go gates

1. **Telemetry gate:** complete device/band/API mapping and explicit missing-state tests.
2. **Benign gate:** a pre-specified confidence-bound target for false alarms is met on held-out benign sessions.
3. **Interference gate:** pre-specified sensitivity and latency targets are met on labelled held-out interference episodes, with uncertainty intervals and separate results per class/device.
4. **Generalization gate:** performance is reported on unseen device models/OS levels and varied benign conditions; limitations are explicit.
5. **Release gate:** no detector-performance claim until Gates 1â€“4 are reviewed. Production packaging also requires a configured and verified owner-controlled release keystore; current release variants are unsigned.

## 7. Immediate next actions

1. Recover authentic Room schema 5/6 exports from a trusted historical checkout, CI artifact, archived database/build, or owner-controlled backup; record source commit/path/hash before using them. The current history search did not recover either file. Never synthesize historical schema JSON.
2. Resolve or formally accept the release-signing blocker; do not repurpose the debug key for public distribution.
3. Build an instrumented test matrix and evidence schema for Stage A before planning any controlled RF session.
4. Keep detector metrics UNVERIFIED until independently labelled evidence is collected and reviewed.

## Literature cross-check ï¿½ 2026-10-22

- Official Android GNSS spoof/jam guidance describes AGC and C/N0 as potential indicators, but labels the feature experimental and says it does not catch every case: https://developer.android.com/develop/sensors-and-location/sensors/gnss-spoof-jam
- Spens et al. (2022), DOI 10.33012/navi.537, evaluates Android-native checks including AGC and C/N0. Axell et al. (2015), DOI 10.1002/navi.74, evaluates commercial receiver detectors and AGC statistics. Neither establishes a transferable threshold or performance claim for Horizon.
- Discovery only: Pervysheva et al. (2025 preprint), DOI 10.21203/rs.3.rs-7864272/v1, reports up to 84.6 percent three-class accuracy in cross-testing on Jammertest 2024 data; the result belongs to that dataset/model and requires independent replication.
- Protocol refinement: hold out complete sessions and unseen devices, preserve calibration/ground-truth provenance, report class-specific confusion matrices and uncertainty, and do not treat adjacent epochs as independent trials.
- **NEW PRIMARY SOURCE (2026-02-04):** Tegedor, Gioia, Barbero, Luzardi & Folloni, [Monitoring Radio Frequency Interference Affecting GNSS Using Android Smartphones](https://www.mdpi.com/2673-4591/126/1/4), published in *Engineering Proceedings*. Their proof-of-concept calibrated the no-interference maximum AGC separately per constellation/frequency band, applied a 15-second moving average and used a 5 dB colour-change trigger in that application; lab devices included Asus ROG 7 and Google Pixel 7, with CW and 1 MHz wideband stimuli generated by a Keysight E8257D source. The authors also report model-to-model differences and ambiguous small AGC changes. **These parameter values are not adopted as Horizon thresholds**; they are evidence for per-device/band calibration and independent, controlled validation only.
- **Android clock guard:** `hardwareClockDiscontinuityCount` changes require resetting continuity assumptions about `GnssClock.timeNanos` and the clock-bias offset; callback `SystemClock.elapsedRealtimeNanos()` is a monotonic receipt-time reference, not the GNSS receiver's measurement epoch. Keep those source/time domains distinct and segment before calculating across-row derivatives. See [GnssClock](https://developer.android.com/reference/android/location/GnssClock) and [SystemClock](https://developer.android.com/reference/android/os/SystemClock).
- **Capture-loss/accounting issue â€” follow-up 2026-10-24:** the current local dirty worktree has introduced checked `trySend` outcomes and session-scoped per-type attempted/accepted/rejected/full/closed counters. A successful `trySend` means the flow channel accepted the item; it does **not** establish Room persistence. The app-recorded archive audit passed ZIP checksums, JSON/CSV/SQLite total counts, sequence continuity and SQLite integrity, but found 27 fewer persisted raw-measurement rows than accepted deliveries and 1 fewer persisted status row. `GNSS_CLOCK` and navigation rows reconcile. **Root cause UNVERIFIED.** `RoomObservationPersistenceQueue` enqueues to a Room-backed pending table, then transactionally inserts the evidence row and deletes the pending row; the service drains during collection and at stop. The archive validator checks total counts and diagnostics serialization but not accepted-to-persisted per-type parity. A row-level audit of the private ZIP could not yet be repeated from the Windows workstation because the uploaded ZIP is not present there and ADB is disconnected. This is **PARTIAL / RELEASE-BLOCKING**, not repaired. Next: distinguish enqueued/consumed/intentionally filtered/persisted/failed counts; inspect exact sequence IDs and queue state for the 28 rows; add deterministic channel/queue/stop regression tests; enforce a documented accepted-to-persisted invariant in export validation; then run full tests/build and verify a fresh device export. Keep callback event counts separate from per-type row counts (one measurement event emits one clock row plus N raw-measurement rows), and preserve channel-full vs closed classification. No source fix, new build, install, commit or code push occurred during this inspection. See repository `CHECKPOINT-2026-10-24.md` and GitHub issue #3.
- **Acceptance IDs added:** A15â€“A20 in `STAGE_A_AGC_TELEMETRY_ACCEPTANCE_MATRIX_2026-10-22.md` cover receiver timestamp availability paths, API 26â€“28 fallback, discontinuity segmentation, loss accounting contract, and per-source timestamp regression auditing. They define tests and are not completed runtime results.
