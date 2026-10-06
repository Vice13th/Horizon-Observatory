# HORIZON OBSERVATORY Ã¢â‚¬â€ MASTER ROADMAP & EXECUTION LEDGER

**Repository:** `Vice13th/Horizon-Observatory`  
**Branch:** `main`  
**Repository baseline commit:** `423577893094216aab6e4acc97bd600509ac94c3`  
**Roadmap update policy:** Every material change is committed and pushed; origin/main is the authoritative remote HEAD receipt.
**Current project checkpoint:** `HORIZON_CHECKPOINT_2026-10-06_STAGE2_VERIFIED`  
**Working workspace:** `C:\Horizon\horizon_stage2`  
**Original workspace:** `E:\horizon_stage2`  
**Operating rule:** Evidence > narrative; Execution > appearance; Verification > confidence.

> This document is the authoritative execution roadmap for the current Horizon Observatory development line. It records completed work, evidence, unresolved state, decisions, and the planned path to Final Checkpoint. Every future material change MUST update this document and push the update to the private GitHub repository.

---

# 1. GOVERNANCE / EPISTEMIC CONTRACT

Status vocabulary:

- **VERIFIED** Ã¢â‚¬â€ directly demonstrated by a reproducible receipt.
- **OBSERVED** Ã¢â‚¬â€ directly observed but not yet generalized.
- **MEASURED** Ã¢â‚¬â€ numerical evidence captured from the system.
- **UNVERIFIED** Ã¢â‚¬â€ implementation exists or appears plausible, but current evidence does not prove it.
- **BLOCKED** Ã¢â‚¬â€ current hardware/software/evidence prevents verification.
- **FAILED** Ã¢â‚¬â€ an explicit test or operation failed; failure remains part of provenance.
- **RESEARCH TARGET** Ã¢â‚¬â€ selected from external scientific/engineering research for future implementation.
- **INFERRED** Ã¢â‚¬â€ engineering inference; never present as empirical fact.

Non-negotiable rules:

1. Raw evidence remains unchanged.
2. Derived data never replaces raw data.
3. Missing measurements remain missing.
4. No fabricated satellite names, measurements, orientation, positioning, or scientific results.
5. Every algorithm output carries provenance/version.
6. No build/test/runtime success claim without a receipt.
7. Every material implementation change is followed by re-read and verification.
8. Checkpoints are immutable reference points; new work creates a new checkpoint.
9. Build caches, generated APK/AAB files, local backups, runtime dumps, and temporary artifacts do not belong in Git.
10. This roadmap is updated whenever project state changes and the update is committed/pushed.

---

# 2. ARCHITECTURE TARGET

Current architectural direction:

```
GNSS / Android Sensors
        Ã¢â€ â€œ
Acquisition
        Ã¢â€ â€œ
Normalization
        Ã¢â€ â€œ
Immutable Observation Bus
        Ã¢â€ â€œ
Timestamp Engine
        Ã¢â€ â€œ
Room Storage
        Ã¢â€ â€œ
Ephemeris / Geometry / Solver
        Ã¢â€ â€œ
Integrity / Replay / Analysis
        Ã¢â€ â€œ
Export
        Ã¢â€ â€œ
Geographic + Observatory Visualization
```

Terra orbital propagation remains a separate scientific domain:

```
Catalog / OMM
   Ã¢â€ â€œ
Propagation Contract
   Ã¢â€ â€œ
OrbitCore SGP4 / SDP4 backend
   Ã¢â€ â€œ
Orbit state / pass prediction
   Ã¢â€ â€œ
Terra visualization
```

GNSS positioning and Terra orbital propagation MUST NOT be silently coupled.

## 2.1 RESILIENT NAVIGATION / EXTERNAL LOCATION TARGET

Horizon shall preserve the best defensible navigation state during GNSS degradation/loss using evidence-backed sources:

    GNSS raw/status/PVT
          + IMU / orientation
          + cellular/network evidence
          + last trusted navigation state
          + validated prediction/constraints
          ↓
    RESILIENCE ENGINE
          ↓
    Horizon Navigation State
          ↓
    optional external-location integration
          ↓
    consumer applications

Hard boundaries:

- Software may optimize GNSS session handling, assistance, measurement selection, interference awareness, and recovery, but MUST NOT claim physical RF/antenna gain.
- Resilient navigation estimates MUST carry provenance and uncertainty.
- Observed, inferred, predicted, and dead-reckoned states MUST remain distinct.
- External location delivery is an optional output path; it MUST NOT become the source of scientific truth.

The external-delivery roadmap has three separate targets: internal Horizon navigation state; an explicit API/feed for cooperating applications; and an optional Android mock/test-location bridge where the device/configuration permits it. Android's documented mock-location workflow is primarily a testing mechanism, so universal transparent delivery into arbitrary third-party apps is NOT a release guarantee.


---

# 3. COMPLETED WORK Ã¢â‚¬â€ HISTORICAL EXECUTION RECORD

## 3.1 GNSS / Foundation history

### Earlier verified foundation
**Status: VERIFIED at its respective checkpoint**

Previously established work included:

- GNSS acquisition path and observation recording.
- Real Android GNSS observations.
- Session-oriented observation storage.
- Timestamp/export infrastructure.
- Export generation and device pull validation.
- Runtime health evidence.
- Orientation source plumbing using Android rotation-vector APIs.
- Skyplot projection path.
- Scientific GNSS roadmap and verification matrix.
- Existing checkpoint/handoff documentation.

Important: these older receipts remain historical. They MUST NOT automatically be reused to claim post-migration behavior.

### Known historical runtime observations
**Status: OBSERVED**

Historical device runs demonstrated:

- GNSS available.
- Cellular/network availability.
- Recording state and acquisition activity.
- Thousands of observations.
- C/N0/satellite evidence.
- Export succeeded twice in the earlier verified run.
- Orientation correctly reported unavailable when no usable heading path was available rather than fabricating heading.
- AGC was observed constant in the tested sample.

These observations predate the Kotlin/Room migration and therefore require fresh post-migration regression.

---

# 4. ORBIT / TERRA ARCHITECTURE WORK

## 4.1 Propagation contract extraction
**Status: VERIFIED**

Created `:propagation-contract` to break the bridge Ã¢â€ â€™ app dependency cycle and establish a clean propagation API boundary.

Core contract includes:

- `Sgp4Sdp4Input`
- `BackendState`
- `BackendResult`
- `Sgp4Sdp4Backend`

## 4.2 OrbitCore bridge
**Status: VERIFIED for compile/runtime reachability**

Created `:orbitcore-bridge`.

Backend:

- OrbitCore `0.1.0`
- KMP Android artifact
- reflection adapter
- explicit backend name/version
- deep-space capability flag
- propagation result mapping
- root-cause error extraction

## 4.3 OrbitCore API investigation
**Status: VERIFIED**

Inspected published OrbitCore artifacts and source.

Established:

- OrbitCore is Kotlin Multiplatform.
- Android/JVM variants exist.
- OrbitCore exposes `kotlin.time.Instant`.
- Kotlin runtime compatibility therefore became an architectural constraint.
- OrbitCore includes SGP4/SDP4-related functionality and pass prediction.

## 4.4 Geometry fixture correction
**Status: VERIFIED**

An overhead geometry fixture initially failed because the inverse ECI/ECEF sign was incorrect.

The fixture was corrected according to the actual transform:

```
ecefX = eciX*cos(g) + eciY*sin(g)
ecefY = -eciX*sin(g) + eciY*cos(g)
```

The corresponding inverse fixture uses the correct positive sign for the reconstructed ECI Y component.

This failure and correction remain part of the engineering provenance.

## 4.5 Scientific propagation status
**Status: UNVERIFIED**

Current bridge tests demonstrate runtime reachability and finite state production.

They do NOT yet constitute an independent scientific reference-vector validation of OrbitCore SGP4/SDP4.

Required next gate:

- authoritative/reference test vectors;
- epoch agreement;
- position/velocity tolerance;
- near-earth and deep-space cases;
- deterministic repeatability;
- provenance of each reference vector.

---

# 5. KOTLIN / BUILD SYSTEM MIGRATION

## 5.1 Initial problem
**Status: VERIFIED**

OrbitCore `0.1.0` exposed `kotlin.time.Instant`, while the previous Kotlin runtime was incompatible.

Reflection tests initially failed because the runtime could not load the required Kotlin time type.

## 5.2 Migration decision
**Status: VERIFIED**

Architectural decision:

- Kotlin `1.9.24 Ã¢â€ â€™ 2.4.0`
- AGP `8.9.1 Ã¢â€ â€™ 8.10.1`
- Room `2.6.1 Ã¢â€ â€™ 2.8.5`
- Compose compiler plugin added
- KSP adopted for Room
- deprecated Android `kotlinOptions` migrated to Kotlin compiler DSL/JVM 17.

## 5.3 KSP migration
**Status: VERIFIED**

KSP configured with:

- schema location;
- incremental processing;
- Kotlin 2.4-compatible KSP `2.3.10`.

KAPT Room configuration was removed.

## 5.4 Compile/test gate
**Status: VERIFIED**

The following gate succeeded:

- `:app:kspPrimaryDebugKotlin`
- `:app:compilePrimaryDebugKotlin`
- `:app:testPrimaryDebugUnitTest`
- `:app:testT1DebugUnitTest`
- `:orbitcore-bridge:testDebugUnitTest`

Bridge XML result:

- tests = 1
- failures = 0
- errors = 0

---

# 6. MOBILE BUILD / DEVICE RECEIPTS

## 6.1 APK build
**Status: VERIFIED**

Both variants assembled:

- `app-primary-debug.apk`
- `app-t1-debug.apk`

Primary APK:

- size: 10,400,849 bytes
- SHA-256:
  `5BE34AA04FB53A56535B00EE4D946D4B80704B3E28A82DBE668A3CE84F13D11F`

T1 APK:

- size: 10,957,976 bytes
- SHA-256:
  `3826EAE9DA970871A967DD10BA493254B9F3015B19A05549E1A1E81C1B03EFA1`

## 6.2 Device install
**Status: VERIFIED**

Target:

- device: Hive
- Android device serial: `R8YY92YWAAF`
- model: `SM_A075F`
- package: `horizon.observatory.debug`
- target SDK: 36
- version: `1.0.0-observatory`
- versionCode: 1

ADB installation exit code = 0.

## 6.3 Launch / crash check
**Status: VERIFIED**

MainActivity launched.

No Horizon/AndroidRuntime fatal exception was observed in the launch crash-buffer inspection.

## 6.4 Installed APK integrity
**Status: VERIFIED**

Pulled installed APK matched the built APK byte-for-byte.

Built SHA-256:

`5BE34AA04FB53A56535B00EE4D946D4B80704B3E28A82DBE668A3CE84F13D11F`

Installed SHA-256:

`5BE34AA04FB53A56535B00EE4D946D4B80704B3E28A82DBE668A3CE84F13D11F`

MATCH = TRUE.

---

# 7. WORKSPACE / BACKUP SAFETY

## 7.1 Independent workspace copy
**Status: VERIFIED**

Workspace copied:

`E:\horizon_stage2` Ã¢â€ â€™ `C:\Horizon\horizon_stage2`

Earlier copy verification:

- source files: 7,968
- destination files: 7,968
- source bytes: 581,260,321
- destination bytes: 581,260,321
- DIFF_COUNT = 0
- MISSING_COUNT = 0

Original E: workspace was not deleted.

## 7.2 Current backup
**Status: VERIFIED**

Backup:

`C:\Horizon\BACKUP_horizon_stage2_20261006_122148`

Verified:

- source files: 7,974
- backup files: 7,974
- source bytes: 582,042,205
- backup bytes: 582,042,205
- DIFF_COUNT = 0
- MISSING_COUNT = 0

Dataset directory was present in backup.

No source evidence was deleted as part of repository cleanup.

---

# 8. GITHUB REPOSITORY BASELINE

## 8.1 Repository cleanup
**Status: VERIFIED**

Private repository:

`Vice13th/Horizon-Observatory`

Created a professional repository hygiene policy.

Excluded from Git:

- Gradle caches
- Kotlin caches
- Android build directories
- native build outputs
- generated APK/AAB artifacts
- runtime screenshots/XML dumps
- checkpoint ZIP archives
- local agent handoff duplicates
- temporary compiler/classpath diagnostics
- local logs/scratch files
- generated evidence archives

Local files were not deleted merely because they were excluded from Git.

## 8.2 Baseline commit
**Status: VERIFIED**

Commit:

`423577893094216aab6e4acc97bd600509ac94c3`

Message:

`chore: establish clean Horizon Observatory repository baseline`

Branch:

`main`

Local HEAD = origin/main.

Working tree was verified clean.

Repository remains private.

---

# 9. CURRENT STATE Ã¢â‚¬â€ 2026-10-06

## VERIFIED

- Clean private GitHub baseline.
- Kotlin 2.4 migration compiles.
- KSP Room processing passes.
- Unit test gates pass.
- OrbitCore bridge test passes.
- Primary/T1 APK assembly passes.
- Primary APK installs.
- MainActivity launches.
- Installed APK SHA matches build SHA.
- Workspace backup is independently verified.
- Propagation contract exists.
- OrbitCore bridge exists.

## OBSERVED

- Historical real GNSS acquisition worked.
- Historical export worked twice.
- Historical skyplot/orientation plumbing exists.
- Historical satellite evidence exists.
- Historical device capability limitations were honestly represented.

## UNVERIFIED

- Physical heading-driven skyplot rotation.
- Satellite panel orientation behavior.
- Final compact satellite naming UI.
- OrbitCore scientific reference-vector accuracy.
- Full SGP4/SDP4 production integration.
- Final Terra/GNSS integrated behavior.

## VERIFIED Ã¢â‚¬â€ POST-MIGRATION REGRESSION (2026-10-06)

- Real post-Kotlin/Room/KSP GNSS recording completed on target device.
- Session `fa2c48ec-15e6-40c1-a892-c170bc27cd04` completed with 2,759 persisted observations.
- GNSS and cellular were AVAILABLE during recording.
- Export #1 succeeded after the export-directory fix.
- Export #2 succeeded after the same fix.
- Both archives passed internal checksum validation.
- Both archives reported contiguous sequence 1..2759 and clean integrity.
- Stable exported payload files were byte-identical across the two exports.
- Built and installed APK SHA-256 matched: `1D0788E88CB59917BD7EA7561229C66707C94DF3C40CA9D40719FEC28D69DA6C`.

## FAILED THEN FIXED Ã¢â‚¬â€ EXPORT DIRECTORY

- First post-migration export attempt failed because `mkdirs()` returned false for an already-existing exports directory.
- `ExportEngine` was corrected to create the directory only when absent and explicitly verify `isDirectory`.
- The corrected implementation was rebuilt, installed, and verified by two successful exports.

## OBSERVED DIAGNOSTIC

- The verified session contained 316 source-timestamp regressions.
- The export integrity gate remained clean because ingestion timestamps were complete and non-decreasing and sequence continuity was preserved.
- This diagnostic remains evidence, not an inferred defect classification.

## KNOWN UI BACKLOG

1. Satellite panels do not currently demonstrably rotate with device orientation.
2. Bottom labels under panels must be removed.
3. Each satellite panel must contain a compact real satellite identifier/name.
4. Satellite icon/panel design may need redesign if required for readability.
5. No fabricated satellite identifiers are permitted.
6. UI changes must preserve the underlying observed satellite evidence.

---

# 10. IMMEDIATE EXECUTION ROADMAP

## PHASE A Ã¢â‚¬â€ POST-MIGRATION REGRESSION
**Priority: P0**  
**Status: VERIFIED Ã¢â‚¬â€ CLOSED 2026-10-06**

### A1 Ã¢â‚¬â€ Clean device launch
Acceptance:

- package launches;
- no fatal exception;
- MainActivity resumed;
- fresh runtime receipt.

### A2 Ã¢â‚¬â€ Start real recording
Acceptance:

- recording state proven;
- GNSS acquisition state proven;
- actual observation count > 0;
- timestamp/epoch evidence captured.

### A3 Ã¢â‚¬â€ Capture GNSS evidence
Acceptance:

- real `GnssMeasurement` observations;
- constellation/SVID evidence;
- C/N0 where exposed;
- raw/normalized provenance retained.

### A4 Ã¢â‚¬â€ Stop recording
Acceptance:

- stop transition proven;
- final session state proven;
- no stop race;
- final observation count recorded.

### A5 Ã¢â‚¬â€ Export regression #1
Acceptance:

- export generated;
- archive/file pulled from device;
- structure inspected;
- counts and metadata checked;
- hash recorded.

### A6 Ã¢â‚¬â€ Export regression #2
Acceptance:

- second export succeeds;
- no overwrite corruption;
- deterministic/consistent structure;
- independent hash/metadata receipt.

### A7 Ã¢â‚¬â€ Compare with pre-migration behavior
Acceptance:

- no unsupported capability silently regressed;
- any regression becomes a documented FAILED/UNVERIFIED item.

---

# 11. PHASE B Ã¢â‚¬â€ OBSERVATORY UI / PANEL RECOVERY

**Priority: P0 → P1**

## B0 — ACTIVE-WORK RECONCILIATION + PANEL RECOVERY FIREWALL

**Status: REQUIRED BEFORE NEW UI FEATURE WORK**

The current Observatory panel is treated as **UNSTABLE**. Recover correctness and runtime stability before visual expansion.

### B0.0 Active-work reconciliation

Before editing any UI file:

- inspect the actual working tree;
- inspect uncommitted changes and active execution sessions when available;
- identify files currently owned by another execution path;
- preserve unpushed user/model work;
- create or reuse a checkpoint before taking ownership of overlapping files;
- never overwrite local work merely because GitHub does not contain it.

If the active workspace cannot be inspected, do NOT claim it is clean and do NOT perform destructive UI replacement from GitHub state alone.

### B0.1 Panel triage

Investigate with evidence:

- stale satellite panels;
- rotation/configuration state loss;
- excessive snapshot polling;
- main-thread database/analysis work;
- full-list recomputation;
- Compose recomposition storms;
- duplicate collectors;
- long-session UI memory retention;
- lifecycle leaks;
- skyplot rendering cost;
- panel/model state coupling.

### B0.2 Canonical-state firewall

The UI consumes canonical live/persisted state through a stable projection layer. No composable may own scientific state, acquisition truth, session truth, persistence truth, or navigation truth.

### B0.3 B0 acceptance

- satellite data refreshes continuously during acquisition;
- rotation does not freeze or replace live state;
- duplicate collectors are eliminated;
- no unbounded polling remains;
- UI remains responsive during recording;
- no new crash/ANR is introduced;
- raw observations and persistence integrity remain unchanged;
- real-device evidence demonstrates recovery.

**UI FEATURE FREEZE:** B1–B6 remain blocked until B0 passes.



**Priority: P1**

## B1 Ã¢â‚¬â€ Evidence-preserving satellite identity

Use actual `constellationType` + `svid` data.

Acceptance:

- compact satellite label derives from observed evidence;
- no invented names;
- missing identity remains explicitly unavailable.

## B2 Ã¢â‚¬â€ Remove panel-bottom labels

Acceptance:

- redundant labels below panels removed;
- no loss of semantic identity inside panel.

## B3 Ã¢â‚¬â€ Compact satellite name inside panel

Acceptance:

- visible at normal device size;
- readable without overlap;
- stable under dynamic observation updates.

## B4 Ã¢â‚¬â€ Orientation-aware panel layout

Acceptance:

- determine actual orientation source behavior from runtime evidence;
- panel arrangement responds only when a valid orientation signal exists;
- no fake rotation when sensor evidence is unavailable.

## B5 Ã¢â‚¬â€ Skyplot synchronization

Acceptance:

- skyplot uses verified orientation source;
- rotation direction is validated empirically;
- unavailable heading state remains explicit.

## B6 Ã¢â‚¬â€ UI regression

Acceptance:

- no satellite evidence disappears;
- no rendering crash;
- no interaction regression;
- screenshot/UITest evidence captured where reliable.

---

# 12. PHASE C Ã¢â‚¬â€ SCIENTIFIC PROPAGATION VALIDATION

**Priority: P1**

## C1 Ã¢â‚¬â€ Reference-vector acquisition

Use authoritative scientific/reference data.

Required:

- known TLE/OMM;
- epoch;
- expected state vector;
- tolerance;
- source/provenance.

## C2 Ã¢â‚¬â€ SGP4 near-earth validation

Acceptance:

- multiple epochs;
- position tolerance;
- velocity tolerance;
- deterministic repeatability.

## C3 Ã¢â‚¬â€ SDP4/deep-space validation

Acceptance:

- deep-space case;
- same tolerances;
- explicit classification;
- no accidental SGP4-only path.

## C4 Ã¢â‚¬â€ Backend contract validation

Acceptance:

- success/error/unavailable states;
- version/provenance attached;
- deterministic result serialization.

## C5 Ã¢â‚¬â€ Terra boundary validation

Acceptance:

- GNSS code does not silently depend on Terra;
- Terra consumes propagation contract only;
- no circular module dependency.

---

# 13. PHASE D Ã¢â‚¬â€ GNSS SCIENTIFIC CORE

**Priority: P2**

## D1 Ã¢â‚¬â€ Canonical immutable raw schema

Retain all Android-exposed evidence without destructive normalization.

## D2 Ã¢â‚¬â€ Capability ledger

Record actual device capabilities:

- API level;
- constellation;
- signal;
- frequency;
- pseudorange;
- ADR;
- Doppler;
- navigation message;
- antenna info;
- orientation sensors.

Never infer from device model alone.

## D3 Ã¢â‚¬â€ Timestamp engine

Implement/verify:

- GNSS time;
- UTC mapping;
- receiver clock bias;
- receiver clock drift;
- discontinuity detection;
- epoch identity.

## D4 Ã¢â‚¬â€ Broadcast ephemeris

Per actual available constellation:

- GPS;
- Galileo;
- BeiDou;
- GLONASS where supported;
- QZSS;
- NavIC only where data exists.

## D5 Ã¢â‚¬â€ Geometry engine

Deterministic:

- ECEF;
- geodetic;
- ENU;
- azimuth;
- elevation;
- range;
- Doppler geometry;
- DOP.

Every output carries:

- ephemeris source;
- epoch;
- algorithm version.

## D6 Ã¢â‚¬â€ Independent WLS/SPP

Outputs:

- ECEF;
- LLA;
- receiver clock bias/drift;
- covariance;
- residual vector;
- GDOP/PDOP/HDOP/VDOP;
- satellite set.

## D7 Ã¢â‚¬â€ Residual Observatory

Expose:

- per-satellite residual;
- RMS;
- distribution;
- outlier candidate;
- rejection reason.

---

# 14. PHASE E Ã¢â‚¬â€ RESEARCH-GRADE EXPORT

**Priority: P2**

## E1 Ã¢â‚¬â€ RINEX 4.01

Pipeline:

```
Raw Android evidence
Ã¢â€ â€™ canonical observation
Ã¢â€ â€™ time normalization
Ã¢â€ â€™ RINEX observation/navigation writer
Ã¢â€ â€™ validation
```

Acceptance:

- standards-conformant structure;
- provenance retained;
- no fabricated observables;
- unsupported fields remain absent/explicitly unavailable.

## E2 Ã¢â‚¬â€ JSON/CSV/SQLite

Acceptance:

- schema version;
- session ID;
- algorithm version;
- timestamp provenance;
- reproducible export.

## E3 Ã¢â‚¬â€ Deterministic replay package

Every research export should be replayable without the original UI session.

---

# 15. PHASE F Ã¢â‚¬â€ PRECISE POSITIONING

**Priority: P3 / research target**

Sequence:

1. RTCM ingestion.
2. NTRIP transport.
3. Differential GNSS.
4. RTK.
5. PPK.
6. ambiguity resolution.
7. quality/integrity metrics.

No RTK/PPK claim before real reference/base evidence exists.

---

# 16. PHASE G Ã¢â‚¬â€ PPP / SSR / HAS

**Priority: P3 / research target**

Targets:

- PPP;
- PPP-AR where justified;
- SSR;
- Galileo HAS;
- precise clocks/orbits;
- convergence monitoring.

All external correction data must retain source/time/provenance.

---

# 17. PHASE H Ã¢â‚¬â€ ANTENNA / GNSS-INS / FGO

**Priority: P3Ã¢â‚¬â€œP4 / research target**

Sequence:

1. Antenna PCO/PCV model.
2. Receiver orientation/attitude evidence.
3. IMU calibration.
4. Loosely coupled GNSS/INS.
5. Tightly coupled GNSS/INS.
6. Factor graph optimization.
7. PDR integration where evidence supports it.

No unsupported sensor fusion claims.

---

# 18. PHASE I Ã¢â‚¬â€ INTEGRITY / SPOOFING / RFI

**Priority: P4**

Targets:

- residual-based fault detection;
- FDE;
- RAIM;
- cycle-slip detection;
- multipath indicators;
- interference/RFI evidence;
- spoofing indicators;
- integrity risk metrics.

A spoofing detector must produce evidence and confidence, not a binary UI claim without supporting measurements.

---

# 19. PHASE J Ã¢â‚¬â€ DETERMINISTIC REPLAY

**Priority: P4**

Requirements:

- immutable raw session;
- deterministic clock reconstruction;
- algorithm/version pinning;
- repeatable solver output;
- replay diff;
- regression corpus.

Target:

```
same evidence + same algorithm version
Ã¢â€ â€™ same result
```

Any nondeterminism must be measured and documented.

---

# 19.5. PHASE K — EMERGENCY NAVIGATION / GNSS INTERFERENCE RESILIENCE

**Priority: P0 → P3 — FAST TRACK**

This is now the primary parallel delivery track after B0. It MUST build on verified raw evidence and MUST NOT bypass safety/scientific gates. B4/B5 orientation limitations do not block K software implementation.

## K0 — Capability discovery

Measure target-device availability of raw GNSS, C/N0, AGC, pseudorange/received satellite time, Doppler, ADR/carrier phase where available, navigation messages, multi-constellation/frequency, IMU/orientation, and cellular measurements.

## K1 — Interference evidence

Monitor measurable changes such as C/N0 collapse, AGC behavior where exposed, satellite/used-in-fix changes, constellation/band dropout, Doppler consistency, clock behavior, measurement age, and cross-source disagreement. Produce evidence-backed states such as NORMAL, DEGRADED, JAM-LIKELY, SPOOF-LIKELY, UNKNOWN, and RECOVERY; these are hypotheses, not proof of physical cause.

## K2 — Measurement trust / survivor selection

Rank surviving measurements using signal stability, temporal continuity, freshness, Doppler/ADR consistency where available, geometry/residual consistency, and source agreement. Never destroy raw evidence. Record down-weight/reject decisions and reasons.

## K3 — Navigation continuity state machine

Use explicit states:

FULL_GNSS → GNSS_DEGRADED → PARTIAL_GNSS → GNSS_LOST → INERTIAL_BRIDGING → CELL_AIDED → MULTI_SOURCE_FUSION → RECOVERY

Transitions require evidence. Dead reckoning MUST carry increasing uncertainty and MUST NOT be represented as fresh GNSS.

## K4 — Last-trusted-state / dead-reckoning bridge

Use last trusted PVT, valid velocity/heading, IMU evidence, cellular evidence where useful, and explicit uncertainty growth. Preserve the last known good state for recovery without rewriting historical observations.

## K5 — Emergency Navigation Mode

User-facing mode: EMERGENCY NAVIGATION = ON.

When enabled, maintain the resilience engine and local session according to Android lifecycle/background rules. Target foreground-service resilience, screen-off/background operation where permitted, process/service recovery, continuous local track persistence, automatic state transitions, and automatic recovery when GNSS returns.

## K6 — Reception Optimization profile

User-facing option: RECEPTION OPTIMIZATION = ON/OFF.

This may activate only verified software-side optimizations such as receiver/session persistence, assistance freshness management, stale-measurement handling, survivor weighting, interference-aware filtering, adaptive recovery, and power-aware profiles. It MUST NOT claim physical antenna/RF gain.

## K7 — Horizon Resilient Location Bridge

Keep three outputs separate:

1. internal Horizon navigation state;
2. explicit API/feed for applications integrated with Horizon;
3. optional Android mock/test-location bridge for supported controlled configurations.

The bridge is explicitly enabled, stoppable, recoverable, provenance-preserving where the receiving interface permits, and must never fabricate an indefensible position. Universal transparent injection into arbitrary third-party production apps is not a release guarantee because Android documents mock-location primarily as a testing mechanism.

## K8 — Real-world validation

Validate open-sky baseline, controlled degradation, GNSS loss, intermittent GNSS, sensor dropout, cellular availability changes, recovery, long-running emergency mode, background/screen-off, and service/application restart.

Measure continuity, time without fresh GNSS, position error against known truth where available, uncertainty growth, recovery time, event loss, crash/ANR rate, and resource/battery cost.

No field claim without reproducible evidence.

---

# 20. GOLDEN DATASET / TEST STRATEGY

Maintain separate golden datasets for:

- GD-001 raw GNSS capture;
- GD-002 timestamp edge cases;
- GD-003 geometry;
- GD-004 broadcast ephemeris;
- GD-005 SPP/WLS;
- GD-006 residual/outlier;
- GD-007 RINEX;
- GD-008 RTK/RTCM;
- GD-009 precise positioning;
- GD-010 Terra SGP4/SDP4;
- GD-011 orientation;
- GD-012 export/replay.

Each dataset requires:

- source;
- immutable checksum;
- schema/version;
- expected outputs;
- tolerance;
- provenance.

---

# 21. VERIFICATION GATE MATRIX

| Gate | Requirement | Status |
|---|---|---|
| G0 Repository hygiene | Private clean baseline | VERIFIED |
| G1 Workspace backup | Full byte/file comparison | VERIFIED |
| G2 Kotlin/KSP migration | Compile + unit tests | VERIFIED |
| G3 APK build | Primary + T1 | VERIFIED |
| G4 Device install | APK install | VERIFIED |
| G5 Launch | MainActivity / crash check | VERIFIED |
| G6 Installed APK integrity | SHA match | VERIFIED |
| G7 Post-migration GNSS | Real observations | VERIFIED |
| G8 Export #1 | Device export | VERIFIED |
| G9 Export #2 | Repeat export | VERIFIED |
| G10 Orientation | Physical heading behavior | UNVERIFIED |
| G11 Satellite panel redesign | Evidence-preserving UI | NOT STARTED |
| G12 OrbitCore scientific vectors | SGP4/SDP4 reference accuracy | UNVERIFIED |
| G13 GNSS SPP/WLS | Independent solver | RESEARCH TARGET |
| G14 RINEX 4.01 | Standards validation | RESEARCH TARGET |
| G15 RTK/PPK | Real correction evidence | RESEARCH TARGET |
| G16 PPP/HAS/SSR | Real precise corrections | RESEARCH TARGET |
| G17 Integrity | Quantified integrity | RESEARCH TARGET |
| G18 Replay | Deterministic replay | RESEARCH TARGET |
| G19 Final integration | Full system regression | NOT STARTED |
| G20 Final checkpoint | Verified release state | NOT STARTED |
| G21 Panel recovery firewall | UI live-state correctness / stability | NEXT |
| G22 Emergency navigation engine | Degraded/lost GNSS continuity with provenance | RESEARCH TARGET |
| G23 Interference evidence | Evidence-driven jamming/spoofing indicators | RESEARCH TARGET |
| G24 Horizon external location API | Explicit feed for cooperating applications | RESEARCH TARGET |
| G25 Android location bridge | Mock/test-location path on supported configurations | RESEARCH TARGET |
| G26 Emergency long-run | Background/screen-off/process recovery | RESEARCH TARGET |

---

# 22. BUILD / DEVICE VERIFICATION PROTOCOL

Every code milestone:

1. Inspect changed files.
2. Re-read modified code.
3. Run targeted compile/test.
4. Run broader test gate.
5. Build APK.
6. Record SHA-256.
7. Install on target device.
8. Launch.
9. Capture relevant runtime evidence.
10. Pull artifacts.
11. Compare hashes/structure/counts.
12. Update this roadmap.
13. Commit roadmap + code.
14. Push.
15. Verify remote HEAD.
16. Create checkpoint when a meaningful gate closes.

No step may be skipped silently.

---

# 23. CHECKPOINT PROTOCOL

Checkpoint naming:

`HORIZON_CHECKPOINT_<DATE>_<MILESTONE>`

Each checkpoint records:

- exact source state;
- Git commit;
- APK hash;
- test receipts;
- device state;
- runtime logs;
- export hashes;
- scientific validation state;
- known failures;
- unresolved questions.

Build/cache folders are excluded from repository checkpoints unless specifically required as external artifacts.

---

# 24. FUTURE CHANGE LOG

## 2026-10-06 Ã¢â‚¬â€ Repository baseline
**VERIFIED**

- Workspace safety backup verified.
- Repository initialized/cleaned.
- Professional .gitignore established.
- Generated/build/cache/runtime artifacts excluded.
- Existing GitHub baseline preserved.
- Private repository confirmed.
- Clean baseline pushed to `main`.
- Commit: `423577893094216aab6e4acc97bd600509ac94c3`.

## 2026-10-06 Ã¢â‚¬â€ Current roadmap initialization
**THIS CHANGE**

- Master roadmap created.
- Historical engineering work consolidated.
- Current evidence states recorded.
- Open gates recorded.
- Future execution sequence defined.
- Mandatory update-and-push protocol established.

---

# 24.1 CHANGELOG Ã¢â‚¬â€ 2026-10-06 POST-MIGRATION REGRESSION

**STATUS: VERIFIED Ã¢â‚¬â€ PHASE A CLOSED**

- Executed real target-device recording after the Kotlin 2.4 / Room 2.8.5 / KSP migration.
- Session `fa2c48ec-15e6-40c1-a892-c170bc27cd04` completed with 2,759 observations.
- Discovered a real export regression: existing exports directory caused `mkdirs()` to return false and abort export.
- Fixed `ExportEngine` directory creation semantics.
- Rebuilt successfully; unit/test/bridge gates remained green.
- Installed APK SHA-256 matched built APK: `1D0788E88CB59917BD7EA7561229C66707C94DF3C40CA9D40719FEC28D69DA6C`.
- Export #1 succeeded: 542,501 bytes, SHA-256 `5BF287B5C4393A5CADA9846135E8DB4B238D07911774A08B7B9E90FE3BC77DB7`.
- Export #2 succeeded: 542,500 bytes, SHA-256 `53EB52446004DC7EC82B0B4F4EF769CCB20F332FC71DC5DB6C71B2369DB36214`.
- Both archives passed independent extraction and internal checksum verification.
- Both archives contained the same 2,759 observations, sequence 1..2759, 2 system events, 0 errors, and clean integrity.
- 316 source-timestamp regressions were observed and preserved as diagnostic evidence.
- Detailed receipt: `docs/receipts/POST_MIGRATION_REGRESSION_2026-10-06.md`.
- Phase B Satellite Observatory UI is now the next execution target.

# 24.2 CHANGELOG — 2026-10-06 RESILIENCE SCOPE / UI FIREWALL

**STATUS: ROADMAP CONTROL UPDATE**

- Existing Phase A verified baseline is preserved.
- The unstable Observatory panel is now protected by mandatory B0 recovery before B1–B6 feature work.
- Active-work reconciliation is mandatory before overlapping UI edits.
- Emergency Navigation / GNSS Interference Resilience is now a first-class roadmap phase.
- Reception Optimization is defined as software-side optimization only; physical RF/antenna gain is explicitly not claimed.
- Horizon Resilient Location is separated into internal navigation state, an explicit cooperating-app API, and an optional Android mock/test bridge.
- Universal transparent delivery to arbitrary third-party applications is explicitly not guaranteed.
- This update changes documentation/control only; no production source, Gradle, Room schema, or UI implementation was changed.

---

# 24.3 FAST EXECUTION / AUTONOMOUS OPERATION CONTRACT

**Priority: P0**

The project is time-critical. Execution MUST minimize latency without lowering verification standards.

## 24.3.1 Reasoning mode

- `reasoning_effort=high` is a session-level invariant whenever the host/runtime supports configurable reasoning effort.
- Set it once at session initialization; do not ask the user to re-issue it for later turns.
- Never voluntarily downgrade reasoning effort mid-task because a step appears easy.
- If the host does not expose a configurable reasoning parameter, use the strongest available reasoning mode and record the limitation once; do not loop on the setting.

## 24.3.2 Tool/plugin auto-use

At session start, discover available tools/plugins once and build an internal capability map.

Use relevant installed capabilities automatically; the user must NOT need to remind the agent.

Priority for HORIZON work:

- Superpowers: using-superpowers, writing-plans, systematic-debugging, executing-plans, test-driven-development, verification-before-completion, requesting-code-review, receiving-code-review, finishing-a-development-branch.
- Codex workflows: code-work, code-verification, bug-investigation, feature-development, feature-testing, comprehensive-qa, pre-release-review, orchestrate-work, resume-interrupted-task, session-handoff.
- Research/tooling: GitHub, Context7, Exa, SciSpace.
- Remote execution: Remote Desktop Commander only when physical device/runtime evidence is required.
- Security plugins only when the changed surface or task actually requires security review.

Do not invoke irrelevant plugins merely to satisfy a checklist.

## 24.3.3 LOCAL-FIRST / REMOTE-LAST

Default execution location is the agent's local authorized workspace.

Perform locally whenever possible:

- repository inspection;
- source analysis;
- search/indexing;
- edits;
- refactoring;
- unit tests;
- static checks;
- compilation;
- Gradle/build;
- package generation;
- artifact analysis;
- export parsing;
- replay;
- documentation.

Use remote/physical-device execution only for evidence that local execution cannot establish:

- ADB install/launch;
- real device GNSS;
- sensors;
- screen-off/background behavior;
- rotation behavior;
- long-running runtime behavior;
- physical resource/thermal/battery measurements.

Never use remote execution to inspect code that is already locally available.

## 24.3.4 BATCH / PARALLEL TOOLING

- Batch independent file reads/searches.
- Run independent tests/checks in parallel when they do not share mutable state.
- Prefer one long-lived build/test process plus output polling over repeated process creation.
- Prefer one batched remote verification session over many small remote calls.
- Do not make serial remote calls for independent evidence.
- Do not re-run a command whose receipt already proves the same condition unless the source/build/device state changed.

## 24.3.5 AUTONOMOUS CONTINUATION

After a gate passes:

1. record evidence;
2. update the roadmap/journal;
3. immediately select the next unblocked gate;
4. continue execution without waiting for a user prompt.

The agent may pause only for:

- an explicit authorization boundary;
- destructive action requiring user approval;
- unavailable required hardware/tool;
- unresolved ambiguity that materially changes the implementation;
- a hard failure after the retry budget is exhausted.

A normal engineering decision is NOT a reason to ask the user.

## 24.3.6 NO PROGRESS THEATER

Do not confuse documentation volume with project progress.

A cycle is not complete merely because:

- a plan was written;
- a README was updated;
- a PR was opened;
- a script was generated;
- a plausible explanation was produced.

Progress requires an actual implementation, test, artifact, or verified environmental improvement.

## 24.3.7 RETRY / DEADLOCK CONTROL

For a failing operation:

- Attempt 1: diagnose.
- Attempt 2: use one materially different valid method.
- Attempt 3: mark BLOCKED/FAILED, preserve evidence, and continue independent work.

No infinite retry.
No repeated environment probing without new information.
No rebuild loops.

## 24.3.8 UI EMERGENCY RULE

Because the current panel is unstable:

`B0 PANEL RECOVERY` owns UI execution until it passes.

Do not expand the UI while B0 is failing.

Do not rewrite the entire `MainActivity` or replace the panel architecture merely to remove symptoms.

## 24.3.9 FINISH-FAST / FINISH-CORRECTLY

The optimization target is:

`MAXIMUM VERIFIED PROGRESS PER TOOL CALL`

not minimum tool count.

The agent should choose the fastest path that still leaves:

- a coherent source tree;
- passing relevant tests;
- known build state;
- explicit device verification state;
- durable evidence;
- an updated roadmap.

## 24.3.10 CURRENT PRIORITY

Until changed by fresh repository evidence:

1. B0 panel recovery firewall — VERIFIED; do not reopen unless regression evidence appears.
2. K0–K7 Emergency Navigation software fast track — run in parallel with independent UI/scientific work.
3. B1–B3 UI evidence/polish — parallel when ownership is independent.
4. Scientific propagation/solver gates — parallel where dependencies permit.
5. K8 physical/long-run validation — batched after local K work is ready.
6. External location delivery and final integration/release verification.

# 24.4 K FAST-TRACK EXECUTION MODE

**STATUS: ACTIVE CONTROL POLICY — 2026-10-06**

After B0 is verified, K becomes a parallel P0/P1 delivery track.

## Objective

Finish every K workstream that can be implemented and verified without physical interference testing **as fast as safely possible**, then batch all hardware-dependent evidence into the final remote/device gate.

## Dependency policy

These do NOT block K:

- B4/B5 orientation hardware limitation on SM-A075F;
- cosmetic UI polish outside K;
- unavailable physical jammer test environment.

These DO block only the specific claims they concern:

- device capability claims → K0;
- real interference classification → K1/K8;
- real-world continuity/accuracy claims → K8;
- production release claims → final integration.

## Parallel execution matrix

| Workstream | Start | Evidence path |
|---|---|---|
| K0 | Immediate | local capability contracts + device receipt when available |
| K1 | Immediate after K0 data model | replay/golden data + later device validation |
| K2 | After K1 data model | unit/property/replay tests |
| K3 | After K2 contracts | state-machine tests + replay |
| K4 | Parallel with K3 | uncertainty/trajectory tests + replay |
| K5 | After K3/K4 interfaces | lifecycle/service tests + controlled runtime |
| K6 | Parallel with K2–K5 | configuration + regression tests |
| K7 | Parallel with K5/K6 | API/bridge tests + supported-device verification |
| K8 | Prepare continuously | single batched physical-device/long-run verification |

## Autonomous continuation

After each K gate:

1. record fresh evidence;
2. update the roadmap;
3. immediately start the next unblocked K gate;
4. continue independent K workstreams in parallel.

Do not return to the user for ordinary sequencing decisions.

## No-false-finish rule

K is not COMPLETE until K8 has current reproducible evidence.

A fully implemented K0–K7 is **SOFTWARE COMPLETE / HARDWARE VALIDATION PENDING**, not field verified.

## Remote efficiency

Do all K design, implementation, unit tests, replay, simulation, export validation, and API work locally.

When physical evidence is available, run one batched session covering:

K0 capability
+ K1 degradation/loss
+ K3 transitions
+ K5 background/screen-off
+ K7 bridge
+ K8 long-run/recovery

and collect all required receipts in one pass.

# 25. MANDATORY ROADMAP UPDATE PROTOCOL

For EVERY future material change:

### Before change
Record:

- objective;
- affected modules/files;
- current gate;
- expected evidence;
- rollback/checkpoint reference.

### During change
Record:

- actual modifications;
- failures;
- corrections;
- external research used;
- exact provenance.

### After change
Record:

- tests;
- build receipt;
- runtime receipt;
- hashes;
- device evidence;
- VERIFIED/UNVERIFIED result.

### Git operation
Always:

```
update ROADMAP
Ã¢â€ â€™ git status
Ã¢â€ â€™ git diff
Ã¢â€ â€™ targeted verification
Ã¢â€ â€™ commit
Ã¢â€ â€™ push
Ã¢â€ â€™ verify origin/main
```

The roadmap update MUST be in the same commit as the material change whenever practical.

---

# 26. EXTERNAL RESEARCH POLICY

External research is permitted for:

- scientific standards;
- SGP4/SDP4 reference vectors;
- RINEX specifications;
- GNSS algorithms;
- Android GNSS APIs;
- OrbitCore API behavior;
- UI/UX comparative research.

Research sources must be distinguished from empirical project evidence.

Plugin/tool usage must never be claimed unless an actual receipt exists.

Research outputs must not overwrite raw evidence or silently become project facts.

---

# 27. FINAL DEFINITION OF DONE

Horizon Observatory reaches Final Checkpoint only when:

1. Repository is private, clean, reproducible and free of generated debris.
2. Build is reproducible.
3. Unit/integration tests pass.
4. Device installation is verified.
5. Real GNSS acquisition is verified on target hardware.
6. Export #1 and #2 are verified after the latest architectural changes.
7. Timestamp/provenance chain is verified.
8. Satellite visualization uses real evidence.
9. Orientation behavior is empirically verified or explicitly represented as unavailable.
10. OrbitCore SGP4/SDP4 is independently validated against reference vectors.
11. Terra boundary is verified.
12. Scientific GNSS solver gates are explicitly passed before their capabilities are claimed.
13. Replay is deterministic or nondeterminism is quantified.
14. All known failures/limitations are documented.
15. Final checkpoint contains reproducible receipts.
16. This roadmap exactly matches the repository state.
17. Final commit and remote HEAD are independently verified.

---

# 28. B0 — ACTIVE-WORK RECONCILIATION + PANEL RECOVERY FIREWALL

**STATUS: VERIFIED — 2026-10-06 functional real-device receipt**

B0 preserved the pre-existing local Satellite Observatory UI work and removed the unbounded live-session observation load from the panel/analysis path. During RECORDING, the UI reads at most the newest 512 persisted observations every 2 seconds. When the session is no longer active, the existing full snapshot path is used. This changes only the UI projection; raw Room evidence remains complete.

Evidence:

- RED: `:app:compilePrimaryDebugAndroidTestKotlin` failed before the new DAO method existed.
- GREEN: the same Android-test compilation succeeded after implementation.
- Real-device Room regression: 11 tests completed with 0 failures; 2 existing migration tests were skipped.
- APK installed: SHA-256 `4CCE1A611F596A574C6FE9A215A588A189C61843C0FE8B8F4564BA66D8D4B3DA`.
- Active recording reached a live UI total of 512 and remained in RECORDING state.
- The same session later reached COMPLETED with 9,522 persisted observations: 1,428 RAW GNSS, 186 GNSS STATUS, 148 FIX, 108 CELLULAR, 7,627 SENSORS.
- Launch/record/complete crash buffers were empty.
- Target SM-A075F exposes a coarse `TYPE_DEVICE_ORIENTATION (27)` sensor, but no `TYPE_ROTATION_VECTOR`, `TYPE_GAME_ROTATION_VECTOR`, `TYPE_GEOMAGNETIC_ROTATION_VECTOR`, `TYPE_MAGNETIC_FIELD`, or `TYPE_HEADING`; continuous north-referenced heading therefore remains unavailable and B4/B5 physical rotation verification is blocked rather than inferred.
- Debug gfxinfo aggregate measured 1,139 rendered frames and 94 janky frames (8.25%); this is recorded as a performance observation, not promoted to a root-cause claim.

B0 is therefore closed for functional recovery. B1 is the next gate.

---

# 28. CURRENT NEXT ACTION

**NEXT EXECUTION TARGET: PHASE B1 — EVIDENCE-PRESERVING SATELLITE IDENTITY**

Phase A is closed and the post-migration/export baseline is verified. The previous Phase B plan is now gated behind B0 so the unstable panel is repaired before visual expansion.

Execution order:

1. inspect the actual working tree and active/unpushed work;
2. protect overlapping local work with a checkpoint/branch;
3. diagnose the UI path from canonical state → UI projection → rendering;
4. make the smallest safe correction;
5. run targeted UI/unit/build regression;
6. install and verify on SM-A075F;
7. only after B0 passes, execute B1–B6;
8. continue scientific propagation/solver gates;
9. execute Phase K Emergency Navigation in dependency order;
10. implement external-location delivery only after the resilience state itself is verified.

B0 is now verified. Continue immediately with B1–B6 in dependency order:

1. trace actual `constellationType` + `svid` identity from persisted observations into the satellite panel;
2. preserve compact evidence-derived identity only; never infer NORAD/catalog identity;
3. keep redundant labels below panels removed;
4. use orientation-aware placement only when a valid runtime orientation source exists;
5. preserve explicit UNAVAILABLE behavior otherwise;
6. empirically verify rotation direction on a device with a valid orientation sensor;
7. run UI/build/unit regression and install verification;
8. then continue scientific propagation/solver gates.

**CRITICAL:** If active workspace inspection is unavailable, preserve unpushed work and do not overwrite/regenerate UI source.




---

# 29. PROJECT PRINCIPLE

```
REALITY > ASSUMPTION
EVIDENCE > CLAIM
INSPECTION > MEMORY
EXECUTION > EXPLANATION
VERIFICATION > CONFIDENCE
PRESERVATION > CONVENIENCE
MINIMAL SAFE CHANGE > UNCONTROLLED REFACTOR
NO RECEIPT Ã¢â€ â€™ NO EPISTEMIC UPGRADE
NO VERIFIED END STATE Ã¢â€ â€™ TASK NOT COMPLETE
```

This document is a living engineering ledger, not a marketing roadmap.

# 28.1 PHASE K EXECUTION RECEIPT — 2026-10-06

K0 capability contract and K1-K4 deterministic domain engines were implemented and covered by a green JVM regression receipt at the time of this historical receipt. The live-ingress gap described here was subsequently closed by receipt 28.3.


# 28.2 K5-K7 RECEIPT — 2026-10-06

- K5: persistent Emergency Navigation mode and existing foreground-service wiring implemented; device lifecycle/background verification remains pending.
- K6: software-only Reception Optimization policy implemented; no RF/antenna claim.
- K7: explicit read-only Horizon Resilient Location ContentProvider implemented and instrumented on SM-A075F.
- Historical blocker at the time of this receipt: integrate K1-K4 runtime engines with observation ingress/derived persistence and verify deterministic replay. This was subsequently closed by receipt 28.3.


# 28.3 PHASE K INTEGRATION + CONTROLLED LOSS RECEIPT — 2026-10-06

**STATUS: K0-K7 SOFTWARE INTEGRATION VERIFIED / K8 HARDWARE VALIDATION PENDING**

The resilience runtime is now wired directly into `ObservatoryService` at the serialized raw-observation ingress. Raw observations are persisted unchanged; meaningful resilience transitions, interference assessments, and non-KEEP trust decisions are persisted as separate `SYSTEM_EVENT` rows with `ObservationProvenance.DERIVED` and `EvidenceStatus.DERIVED`. The runtime is session-scoped and deterministic replay is covered by JVM tests.

Device receipt on SM-A075F / Android 16:

- APK SHA-256: `D9E8C5838E3AE27D46706E5083184094B38489B58FB4B18D926615EC039B0B28`.
- `:app:connectedPrimaryDebugAndroidTest`: BUILD SUCCESSFUL; 14 tests finished, 2 existing migration tests skipped.
- Active runtime session produced raw GNSS/status/fix/cellular/sensor observations plus 66 derived resilience events.
- A controlled Location-off interval produced real transitions: `FULL_GNSS -> GNSS_DEGRADED -> GNSS_LOST -> INERTIAL_BRIDGING -> MULTI_SOURCE_FUSION`; after Location restoration, `RECOVERY -> FULL_GNSS` was observed.
- The WAL-consistent closed Room snapshot for the latest clean-stop session reports `COMPLETED`, with 44 GNSS_RAW_MEASUREMENT, 10 GNSS_STATUS, 1 GNSS_FIX, 5 CELLULAR_INFO, 172 SENSOR_ACCEL, 20 SYSTEM_EVENT, and 18 DERIVED rows.
- Clean-stop service receipt: `HorizonService: Session COMPLETED ... observations=270`.
- Earlier interrupted-stop evidence was reconciled via the service recovery path; a subsequent app/service start showed the interrupted session as COMPLETED.
- No Horizon crash-buffer entries were observed in the controlled-loss/clean-stop run.
- The device Location setting was restored from `3` to `0` and back to the original `3`; no persistent device setting change was left behind.

Scientific boundary:

- This is controlled GNSS-loss/resilience evidence, **not physical jamming/spoofing validation**.
- K1 labels remain hypotheses.
- K8 physical interference classification, open-sky truth comparison, long-run resource/battery characterization, and screen-off/background service validation remain pending.

**NEXT UNBLOCKED TARGET: K8 batched physical/long-run validation when suitable hardware/control conditions are available.**
