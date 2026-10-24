# Knowledge extraction from the supplied R&D specifications (2026-10)

**Sources (user-supplied):** *Unified Scientific & Computational Methods* (UNI), *Resilient GNSS/PNT Computational R&D Spec* (RES),
*Emergency Module Agent Specification* (EMG, uploaded twice, byte-identical), *Advanced Orbital Observation & Orbit Determination* (ORB),
*Technical Architecture Specification: Resilient PNT & Anti-Jamming/Spoofing Framework* (TAS, the source architecture).
**Precedence:** UNI and EMG (which contains an explicit audit of TAS) override TAS where they conflict. ORB was reviewed at heading level and for its
SGP4/frame rules only. Status words: IMPLEMENTED (code written, **not yet built/run by me**), PRESENT (already in the repo), PROPOSED, BLOCKED (platform),
REJECTED (claim not adopted).

## 1. Claim audit of the source architecture (TAS)
| TAS claim | Verdict in UNI/EMG | App decision | Status |
|---|---|---|---|
| Î”AGC > 8 dB â‡’ jamming (fixed rule) | Not universal; AGC can be absent and is receiver-specific; "never hard-code an 8 dB rule" | Robust step vs the session's own baseline; gate is a parameter; AGC absence = UNAVAILABLE; sign not interpreted | IMPLEMENTED (AGC indicator) |
| C/N0 < 15 dB-Hz â‡’ jamming | "Do not ship universal defaults" | Level shift + cross-satellite dispersion shift per constellation/band, self-baselined | IMPLEMENTED (C/N0 indicator) |
| TCXO drift second-derivative alarm | Valid only on a continuity-segmented, uncertainty-aware series | Drift acceleration outliers inside continuity segments; discontinuity changes reported separately; needs raw clock capture | IMPLEMENTED (+ `GNSS_CLOCK` capture, **UNVERIFIED on device**) |
| Single-emitter residual-covariance collapse | Needs validated PVT residuals/geometry; not a standalone invariant | Not built (no WLS residual pipeline yet) | PROPOSED |
| Codeâ€“carrier divergence flags spoofing | Preconditions: valid ADR state, no resets, model; else NOT_AVAILABLE/INVALID | Built the weaker, always-computable check: ADR displacement vs integrated range-rate (code-minus-carrier needs pseudorange reconstruction) | IMPLEMENTED (ADR indicator); CCD proper PROPOSED |
| OSNMA gives zero-false-positive rejection | Incorrect: authenticates eligible nav-message data, not RF/range/position; needs key/trust/time lifecycle; not guaranteed on Android | Not built; typed status vocabulary reserved; raw nav messages are already recorded | PROPOSED (protocol-gated) |
| Solution-separation FDE with ISB | Candidate method; start from a validated WLS baseline | Not built | PROPOSED |
| InEKF on SEâ‚‚(3) / GTSAM factor graph | Unproven here; adopt only if evaluation justifies | Not built (existing dead-reckoning bridge unchanged) | PROPOSED |
| 5G TDOA/RTT via TelephonyManager | BLOCKED BY PLATFORM: no generic public API for network-side TDOA/RTT | Not built | BLOCKED |
| UWB/Wi-Fi mesh, NPU CNN, VIO | Optional research modules; need datasets and device support | Not built | PROPOSED |
| Zero-copy JNI/C++ ring buffer | Not required; Kotlin pipeline is adequate at 1 Hz | Not built | REJECTED (for now) |

## 2. Rules adopted from UNI/EMG/ORB (applied in code)
1. Missing data is *unavailable*, never zero (`IndicatorStatus.UNAVAILABLE`; nullable samples).
2. Thresholds are parameters of a named, versioned profile (`IntegrityProfile`), labelled experimental/uncalibrated; no false-alarm rate is claimed.
3. Features are robust (median/MAD), per constellation/band, with sample counts and window lengths on every metric.
4. Series are segmented at clock discontinuities / full-tracking transitions; nothing is differentiated across a reset.
5. The output vocabulary has no NOMINAL: the best outcome is `NOT_ESTABLISHED`; `SUSPECT` only means "a measured departure from the session baseline".
6. No attacker identification; benign confounders are listed beside every indicator; indicators are declared non-independent.
7. Frames/epochs explicit and SGP4 only for GP/OMM-like inputs (ORB Â§3, Â§5): already satisfied by the existing prediction pipeline.

## 3. Findings about the existing code (not changed here)
- `InterferenceEvidenceEngine` hard-codes thresholds (10 dB C/N0 collapse, 50 % satellite drop, Doppler RMS 3.0, ADR RMS 1.0) and emits `JAM_LIKELY` / `SPOOF_LIKELY`; UNI/EMG advise parameterised, calibrated thresholds and non-attributing statuses.
- `InterferenceSnapshot.dopplerResidualRms`, `adrResidualRms`, `measurementContinuityLost`, `crossSourceDisagreement` are **never populated** by `ResilienceRuntime` (always null). The new ADR/clock indicators could feed the first and third once a bounded live accumulator exists.
- Before this change the receiver clock (`GnssClock`: drift, discontinuity count, bias, time) and event-level AGC were **not persisted**, which made clock diagnostics and any offline pseudorange/WLS replay impossible.

## 4. What was implemented
`GNSS_CLOCK` capture (`acquisition/gnss/GnssClockCapture.kt`), `analysis/integrity/*` (indicators, adapter, tests), report section **Integrity indicators**.
Math: `docs/architecture/SCIENTIFIC_METHODS.md` Â§16. Verification state: `docs/verification/VERIFICATION_MATRIX_CURRENT.md`.

## 5. Open items (need data or a toolchain)
Run on a device and calibrate the profile (record benign scenarios: indoor/outdoor, motion, airplane-mode toggles); pseudorange reconstruction + WLS residuals;
live bounded accumulator feeding `InterferenceSnapshot`; OSNMA feasibility on the target receiver; ORB phases (IOD, batch OD) are research-stage and untouched.
