# HORIZON OBSERVATORY — ADVANCED GNSS ROADMAP
## Research-Driven Engineering Plan — 2026-10-06

**Dataset:** `Horizon_GNSS_Research_Dataset_2026-10-06.json`  
**Baseline:** `HORIZON_CHECKPOINT_2026-10-06_STAGE2_VERIFIED`  
**Operating principle:** Evidence > narrative; Execution > appearance; Verification > confidence.

---

## 0. Mission

Transform Horizon Observatory from an Android GNSS acquisition/visualization application into an **evidence-first scientific GNSS observatory**.

The target system must be able to:

1. capture raw GNSS evidence without destructive normalization;
2. preserve exact provenance;
3. reconstruct time deterministically;
4. process broadcast and precise ephemerides;
5. produce independently testable positioning solutions;
6. quantify residuals and integrity;
7. replay the same evidence through different algorithms;
8. export research-grade datasets;
9. visualize observations rather than merely display a navigation fix;
10. maintain a hard separation between GNSS positioning and Terra orbital propagation.

---

# 1. Epistemic Contract

Every subsystem uses exactly these states:

- `VERIFIED` — directly demonstrated by current evidence.
- `OBSERVED` — directly observed, but not yet generalized.
- `UNVERIFIED` — implementation or behavior exists/appears possible but has not been proven.
- `BLOCKED` — current hardware/software/evidence prevents verification.
- `RESEARCH_TARGET` — supported by external research and selected as a future engineering target.
- `INFERRED` — engineering inference; never present it as empirical fact.

Rules:

- Raw evidence is immutable.
- Derived data never replaces raw data.
- Missing measurements stay missing.
- Unsupported capabilities are not represented as active.
- Every algorithm output carries algorithm/version/provenance.
- Every claim of success requires a reproducible verification artifact.

---

# 2. Current Checkpoint Baseline

## VERIFIED

- Debug build succeeded in the verified checkpoint run.
- Primary APK installed and launched on the target Android device.
- Real GNSS acquisition produced thousands of observations.
- Export package generation succeeded twice.
- Export ZIP was pulled from the device and structurally validated.
- Final checkpoint archive was opened and verified.

## OBSERVED

- GNSS signal history contained C/N0 observations.
- Satellite evidence was populated.
- `nav=0` was observed in the tested runtime.
- AGC was exactly constant over the tested sample window.
- Display rotation was operational.

## UNVERIFIED

- Live physical heading-driven skyplot rotation.
- RTK / PPK / PPP.
- RINEX 4.01 production.
- SSR/HAS integration.
- Tightly coupled GNSS/INS.
- FGO/PDR.
- Real-time spoofing detection.
- Active OrbitCore SGP4/SDP4 backend.

## BLOCKED

- Continuous physical heading rotation on the tested SM-A075F because the observed sensor inventory did not provide a verified usable rotation-vector/gyro/magnetometer path.
- Full post-stop-state proof was not captured.

---

# 3. Target Architecture

```text
Android Sensors / GNSS
        |
        v
+---------------------------+
| Acquisition               |
| GnssMeasurement           |
| GnssClock                 |
| GnssStatus                |
| NavigationMessage         |
| AntennaInfo               |
| Sensors / NMEA            |
+-------------+-------------+
              |
              v
+---------------------------+
| IMMUTABLE RAW EVIDENCE    |
+-------------+-------------+
              |
              v
+---------------------------+
| NORMALIZATION             |
| Canonical Observation    |
| Canonical Signal         |
| Canonical Satellite      |
+-------------+-------------+
              |
              v
+---------------------------+
| TIME ENGINE               |
| Epoch / Clock / Bias      |
+-------------+-------------+
              |
              +--------------------+
              |                    |
              v                    v
       +-------------+       +-------------+
       | EPHEMERIS   |       | ANTENNA     |
       | Broadcast   |       | PCO / PCV   |
       | SP3 / CLK   |       | Gain        |
       | SSR / HAS   |       +-------------+
       +------+------+ 
              |
              v
+---------------------------+
| GEOMETRY ENGINE           |
| ECEF / ENU / Az / El      |
| Range / Doppler Geometry  |
+-------------+-------------+
              |
              v
+---------------------------+
| SOLVER STACK              |
| SPP / WLS                 |
| DGNSS                     |
| RTK / PPK                 |
| PPP / PPP-AR              |
+-------------+-------------+
              |
              v
+---------------------------+
| FUSION                    |
| EKF                       |
| TC GNSS/INS               |
| FGO / PDR                 |
+-------------+-------------+
              |
              v
+---------------------------+
| INTEGRITY                 |
| Residuals / FDE / RAIM    |
| Cycle Slip / Multipath    |
| RFI / Spoofing            |
+-------------+-------------+
              |
              +-------------------+
              |                   |
              v                   v
        REPLAY ENGINE       VISUALIZATION
              |             Skyplot / Signals
              |             Residuals / Clock
              |             Integrity / Orbit
              v
        EXPORT / DATASET
 RINEX / RTCM / JSON / CSV / SQLite
```

---

# 4. P0 — Evidence Foundation

## Objective

Make the raw observation pipeline scientifically trustworthy before adding advanced positioning.

### P0.1 Canonical raw schema

Implement immutable records for:

- GNSS event timestamp;
- `GnssClock`;
- SVID;
- constellation;
- signal/frequency;
- C/N0;
- pseudorange;
- pseudorange rate;
- accumulated delta range;
- ADR state;
- multipath indicator;
- received satellite time;
- uncertainty;
- raw source identifier.

### Acceptance criteria

- 100% of raw fields that Android actually exposes are retained.
- Unsupported fields are explicitly `UNAVAILABLE`.
- No raw value is silently transformed and persisted as raw.
- Every record has provenance.

### P0.2 Device capability ledger

Create a capability matrix generated from actual runtime evidence:

```text
device
Android API
constellation
signal
frequency
pseudorange
ADR
Doppler
navigation message
antenna info
sensor orientation
```

Never infer capability from model name alone.

### P0.3 Timestamp engine

Implement:

- GNSS time;
- UTC mapping;
- receiver clock bias;
- receiver clock drift;
- discontinuity detection;
- epoch identity.

### P0.4 Evidence identity

Every acquisition session gets:

```text
session_id
device_id
application_version
schema_version
acquisition_start
acquisition_end
source_capabilities
clock_model_version
normalizer_version
```

---

# 5. P1 — Scientific GNSS Core

## P1.1 Broadcast ephemeris

Implement/verify per constellation:

- GPS;
- Galileo;
- BeiDou;
- GLONASS where supported;
- QZSS;
- NavIC where data is actually available.

Do not assume all navigation messages will exist on all phones.

## P1.2 Geometry

Implement deterministic:

- ECEF;
- geodetic conversion;
- ENU;
- azimuth;
- elevation;
- slant range;
- satellite/receiver geometry;
- DOP.

Every geometry result must carry:

```text
ephemeris_source
time_epoch
algorithm_version
```

## P1.3 WLS/SPP

Build the first independent deterministic solver.

Minimum output:

```text
ECEF
LLA
clock bias
clock drift
covariance
residual vector
GDOP/PDOP/HDOP/VDOP
satellite set
```

## P1.4 Residual Observatory

Expose:

- residual per satellite;
- residual RMS;
- residual distribution;
- outlier candidate;
- solver rejection reason.

The UI must make the evidence visible instead of hiding it behind one fix number.

---

# 6. P1 — RINEX 4.01

RINEX must become a first-class export target.

## Required pipeline

```text
Raw Android
    ->
Canonical Observation
    ->
RINEX 4.01
```

Do not build:

```text
Raw -> CSV -> third-party conversion
```

as the canonical path.

## Required preservation

- observation epoch;
- constellation;
- signal;
- frequency;
- pseudorange;
- carrier phase/ADR;
- Doppler;
- C/N0;
- loss-of-lock indicators;
- signal-strength indicators;
- receiver/device metadata;
- provenance.

## Acceptance test

A golden raw dataset exported to RINEX and re-read must produce equivalent canonical observations within explicitly defined tolerances.

---

# 7. P2 — Precise Positioning

## P2.1 RTCM

Implement parser/transport abstraction.

Separate:

```text
RTCM acquisition
RTCM decoding
correction store
correction application
```

## P2.2 NTRIP

Implement:

- caster configuration;
- mountpoint metadata;
- reconnect;
- latency;
- correction age;
- byte counters;
- authentication state.

Never hide stale corrections.

## P2.3 RTK

Target:

```text
single
double
triple differences
ambiguity state
cycle-slip handling
integer ambiguity resolution
```

Required metrics:

- fixed ratio;
- float/fixed state;
- ambiguity age;
- correction age;
- baseline;
- residuals.

## P2.4 PPK

PPK must operate on recorded evidence.

This is strategically important because it gives Horizon a deterministic offline validation path.

---

# 8. P3 — PPP / HAS / SSR

## PPP

Inputs:

```text
code
carrier phase
precise orbit
precise clock
antenna model
receiver clock model
```

Metrics:

- convergence time;
- 3D error;
- horizontal error;
- clock stability;
- ambiguity status.

## HAS

Treat Galileo HAS as a correction source, not a magic accuracy switch.

Required evidence:

```text
HAS message received
HAS correction decoded
correction epoch
correction age
satellite coverage
solution impact
```

## SSR

Separate:

```text
SSR orbit
SSR clock
SSR bias
SSR validity
```

---

# 9. P3 — Antenna Intelligence

When `GnssAntennaInfo` is available, preserve:

- phase center offset;
- phase center variation;
- signal gain;
- frequency;
- antenna reference frame.

Do not invent antenna corrections when Android does not expose them.

---

# 10. P3 — GNSS/INS

## Stage 1

Loose coupling:

```text
GNSS PVT -> INS
```

## Stage 2

Error-state EKF.

States:

```text
position
velocity
attitude
gyro bias
accelerometer bias
clock bias
clock drift
```

## Stage 3

Tightly coupled:

```text
raw GNSS
+
IMU
```

Use raw code / Doppler / carrier-phase where available.

## Stage 4

FDE inside the estimator.

The filter must be able to identify bad measurements rather than blindly fuse them.

---

# 11. P4 — Factor Graph Optimization

Only begin after the EKF pipeline is independently verified.

Factor classes:

```text
GNSS pseudorange
GNSS Doppler
carrier phase
IMU preintegration
PDR
zero velocity / motion constraints
network position
```

Every factor must expose:

```text
factor_id
input_evidence_ids
residual
weight
robust_loss
solver_version
```

---

# 12. P4 — Integrity Observatory

Integrity is a first-class domain.

Required statuses:

```text
VALID
DEGRADED
SUSPECT
INTEGRITY_RISK
UNKNOWN
```

Evidence:

- residuals;
- geometry;
- C/N0 anomalies;
- cycle slips;
- multipath;
- clock jumps;
- external-source disagreement;
- correction age;
- FDE decisions.

Future:

- RAIM;
- protection levels;
- spoofing detection;
- meaconing detection;
- RFI classification.

---

# 13. P4 — Spoofing / RFI

Do not build a detector that simply labels abnormal C/N0 as spoofing.

Required distinction:

```text
RF anomaly
GNSS measurement anomaly
navigation-message anomaly
geometry anomaly
clock anomaly
cross-sensor inconsistency
possible spoofing
```

A spoofing result must include evidence and confidence.

---

# 14. P5 — Deterministic Replay

Replay is one of the highest-value features.

A replay bundle must contain:

```text
raw observations
clock
ephemeris
corrections
sensor evidence
metadata
algorithm versions
configuration
seed where relevant
```

Replay modes:

```text
raw replay
normalization replay
solver replay
integrity replay
algorithm A/B replay
```

The same evidence + same algorithm version must produce reproducible output within defined floating-point tolerances.

---

# 15. Terra Boundary

GNSS and Terra must not be conflated.

## GNSS

```text
broadcast ephemeris
SP3
CLK
SSR
HAS
GNSS geometry
PVT
```

## Terra

```text
TLE/OMM
SGP4
SDP4
catalog objects
orbital propagation
```

SGP4/SDP4 must not be used as the GNSS positioning model merely because both domains involve satellites.

---

# 16. UI / Observatory Workstream

The UI should expose evidence layers.

## Primary cockpit

```text
UTC / GNSS TIME
FIX STATE
POSITION
ACCURACY
INTEGRITY
SATELLITES
```

## Satellite cards

Each satellite must have a compact identity:

```text
G05
E11
R03
C07
```

with:

```text
C/N0
elevation
azimuth
frequency
ADR state
residual
integrity state
```

## Panels

Required:

1. Skyplot.
2. Signal strength.
3. Carrier/ADR.
4. Doppler.
5. Residuals.
6. Clock.
7. Integrity.
8. RF environment.
9. Replay.
10. Export/session state.

No decorative satellite data may be introduced when evidence is absent.

---

# 17. Orientation Architecture

The presentation layer should support:

```text
device orientation
+
sensor availability
+
heading confidence
```

But:

```text
sensor unavailable
```

must remain visibly distinct from:

```text
heading = 0°
```

No fake rotation.

If the device has no suitable sensor path, the skyplot remains North-referenced or explicitly marked non-heading-relative.

---

# 18. Room / Storage Architecture

Recommended entities:

```text
Session
RawGnssEvent
GnssClockSample
NavigationMessage
AntennaInfoSample
SensorSample
CanonicalObservation
EphemerisRecord
CorrectionRecord
GeometryObservation
SolverRun
SolutionEpoch
Residual
IntegrityAssessment
ReplayManifest
ExportArtifact
```

Every derived entity references its source evidence.

---

# 19. Golden Dataset Strategy

Create permanent fixtures:

### GD-001

Static open-sky smartphone raw dataset.

### GD-002

Dual-frequency dataset.

### GD-003

Carrier-phase stress dataset.

### GD-004

Urban canyon.

### GD-005

Indoor/degraded GNSS.

### GD-006

Cycle-slip dataset.

### GD-007

Clock discontinuity dataset.

### GD-008

Multipath dataset.

### GD-009

RTCM/NTRIP dataset.

### GD-010

Spoofing/RFI research dataset.

### GD-011

GNSS/INS motion dataset.

### GD-012

Terra orbital propagation dataset.

Each dataset requires:

```text
source
license
device
time
coordinates if legitimately known
expected properties
known limitations
hash
schema version
```

---

# 20. Verification Matrix

| ID | Capability | Gate |
|---|---|---|
| V-001 | Raw acquisition | P0 |
| V-002 | Raw immutability | P0 |
| V-003 | Capability ledger | P0 |
| V-004 | Time normalization | P0 |
| V-005 | Broadcast ephemeris | P1 |
| V-006 | Geometry | P1 |
| V-007 | WLS/SPP | P1 |
| V-008 | Residual analysis | P1 |
| V-009 | RINEX round-trip | P1 |
| V-010 | RTCM | P2 |
| V-011 | RTK | P2 |
| V-012 | PPK | P2 |
| V-013 | PPP | P3 |
| V-014 | HAS | P3 |
| V-015 | SSR | P3 |
| V-016 | GNSS/INS EKF | P3 |
| V-017 | FDE/RAIM | P4 |
| V-018 | FGO | P4 |
| V-019 | PDR | P4 |
| V-020 | Replay determinism | P5 |
| V-021 | Export fidelity | P5 |
| V-022 | UI evidence integrity | P5 |

A gate passes only with an attached evidence artifact.

---

# 21. Performance Targets

Do not set an accuracy number merely to make the product look advanced.

Measure:

```text
acquisition throughput
events/sec
storage bytes/event
normalization latency
geometry latency
solver latency
replay speed
CPU utilization
memory
battery cost
thermal behavior
```

For positioning:

```text
3D RMSE
horizontal RMSE
vertical RMSE
95th percentile
CEP where appropriate
convergence time
continuity
availability
integrity alert latency
```

All accuracy numbers must identify the reference truth source.

---

# 22. Regression Strategy

Every feature must be tested in four dimensions:

```text
LIVE
RECORDED
REPLAY
EXPORT/REIMPORT
```

A solver that works live but fails on replay is not accepted.

An export that cannot round-trip into the same canonical observations is not accepted.

---

# 23. Build / Device Verification

For every release candidate:

1. Clean build.
2. APK hash.
3. Install.
4. Launch.
5. Acquire live GNSS.
6. Capture raw evidence.
7. Verify session state.
8. Verify stop state.
9. Generate export.
10. Pull export.
11. Validate ZIP.
12. Validate SQLite.
13. Validate JSON/CSV.
14. Re-import/replay.
15. Compare outputs.
16. Capture logs.
17. Update provenance manifest.

---

# 24. Current Device Reality

The tested SM-A075F must not be treated as a universal GNSS reference device.

Maintain:

```text
DeviceCapabilityProfile
```

with per-device evidence.

A feature may therefore be:

```text
SUPPORTED_ON_DEVICE
UNAVAILABLE_ON_DEVICE
NOT_TESTED
IMPLEMENTED_NOT_VERIFIED
```

This distinction is mandatory.

---

# 25. Risks

## R1 — Hardware limitation

Some Android phones do not expose all GNSS fields.

Mitigation:

Capability-driven architecture.

## R2 — Carrier-phase quality

Smartphone ADR may contain discontinuities and hardware-dependent behavior.

Mitigation:

Quality flags + cycle-slip analysis + raw preservation.

## R3 — Ephemeris availability

Navigation messages may be absent.

Mitigation:

External ephemeris/correction sources.

## R4 — Solver complexity

RTK/PPP are substantially more complex than SPP.

Mitigation:

Incremental solver gates.

## R5 — UI claims exceeding scientific evidence

Mitigation:

Integrity/provenance state visible in UI.

## R6 — Terra/GNSS architectural coupling

Mitigation:

Strict domain boundary.

## R7 — Replay nondeterminism

Mitigation:

Versioned algorithms, deterministic configuration, explicit tolerances.

---

# 26. Recommended Implementation Order

```text
P0
Raw evidence
Capability ledger
Timestamp
Provenance

↓

P1
Ephemeris
Geometry
WLS
Residuals
RINEX

↓

P2
RTCM
NTRIP
RTK
PPK

↓

P3
PPP
HAS
SSR
Antenna model
GNSS/INS EKF

↓

P4
FDE
RAIM
FGO
PDR
RFI
Spoofing

↓

P5
Replay
A/B algorithms
Golden datasets
Scientific benchmark suite
Final observatory UI
```

Do not invert this order for visual reasons.

---

# 27. Definition of Done — Scientific Horizon

Horizon reaches the intended scientific maturity when:

- raw evidence is immutable;
- every observation has provenance;
- time is modeled explicitly;
- ephemeris source is explicit;
- geometry is independently testable;
- SPP/WLS is deterministic;
- residuals are visible;
- RINEX export is loss-aware and validated;
- RTK/PPK/PPP are independently gated;
- GNSS/INS fusion is testable;
- integrity is explicit;
- replay is deterministic;
- exported evidence can reproduce the analysis;
- Terra propagation is separated from GNSS positioning;
- UI never displays fabricated measurements;
- every claimed capability has a verification artifact.

---

# 28. Final Engineering Principle

The final product should not be optimized to look like an advanced GNSS application.

It should be engineered so that an independent reviewer can ask:

> “Where did this number come from?”

and Horizon can answer:

```text
session
→ raw observation
→ normalized observation
→ ephemeris/correction
→ geometry
→ solver
→ residual
→ integrity assessment
→ rendered value
```

That provenance chain is the core product.

---

## External research anchors

1. Android GNSS APIs and raw measurements  
   https://developer.android.com/develop/sensors-and-location/sensors/gnss

2. GPSTest  
   https://github.com/barbeau/gpstest

3. IGS products/access  
   https://connect.igs.org/products-access/

4. Galileo HAS  
   https://gssc.esa.int/navipedia/index.php/Galileo_High_Accuracy_Service_%28HAS%29

5. NSGRX / Android-to-RINEX research  
   https://nottingham-repository.worktribe.com/output/70323219

6. Robust Android GNSS/INS  
   https://experts.umn.edu/en/publications/robust-android-gnss-positioning-using-single-differenced-tightly-/

7. Smartphone GNSS-PPK/PDR/FGO research  
   https://pmc.ncbi.nlm.nih.gov/articles/PMC11433767/

8. Android GNSS/RFI research  
   https://www.mdpi.com/2673-4591/126/1/4

---

## Change-control rule

This roadmap is a planning artifact, not evidence of implementation.

A roadmap item changes from `RESEARCH_TARGET` to `VERIFIED` only after:

```text
implementation
→ build
→ runtime execution
→ raw evidence
→ reproducible test
→ artifact/hash
→ checkpoint update
```

No exception.
