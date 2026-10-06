# HORIZON OBSERVATORY — MASTER ROADMAP & EXECUTION LEDGER

**Repository:** `Vice13th/Horizon-Observatory`  
**Branch:** `main`  
**Current repository HEAD:** `423577893094216aab6e4acc97bd600509ac94c3`  
**Current project checkpoint:** `HORIZON_CHECKPOINT_2026-10-06_STAGE2_VERIFIED`  
**Working workspace:** `C:\Horizon\horizon_stage2`  
**Original workspace:** `E:\horizon_stage2`  
**Operating rule:** Evidence > narrative; Execution > appearance; Verification > confidence.

> This document is the authoritative execution roadmap for the current Horizon Observatory development line. It records completed work, evidence, unresolved state, decisions, and the planned path to Final Checkpoint. Every future material change MUST update this document and push the update to the private GitHub repository.

---

# 1. GOVERNANCE / EPISTEMIC CONTRACT

Status vocabulary:

- **VERIFIED** — directly demonstrated by a reproducible receipt.
- **OBSERVED** — directly observed but not yet generalized.
- **MEASURED** — numerical evidence captured from the system.
- **UNVERIFIED** — implementation exists or appears plausible, but current evidence does not prove it.
- **BLOCKED** — current hardware/software/evidence prevents verification.
- **FAILED** — an explicit test or operation failed; failure remains part of provenance.
- **RESEARCH TARGET** — selected from external scientific/engineering research for future implementation.
- **INFERRED** — engineering inference; never present as empirical fact.

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
        ↓
Acquisition
        ↓
Normalization
        ↓
Immutable Observation Bus
        ↓
Timestamp Engine
        ↓
Room Storage
        ↓
Ephemeris / Geometry / Solver
        ↓
Integrity / Replay / Analysis
        ↓
Export
        ↓
Geographic + Observatory Visualization
```

Terra orbital propagation remains a separate scientific domain:

```
Catalog / OMM
   ↓
Propagation Contract
   ↓
OrbitCore SGP4 / SDP4 backend
   ↓
Orbit state / pass prediction
   ↓
Terra visualization
```

GNSS positioning and Terra orbital propagation MUST NOT be silently coupled.

---

# 3. COMPLETED WORK — HISTORICAL EXECUTION RECORD

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

Created `:propagation-contract` to break the bridge → app dependency cycle and establish a clean propagation API boundary.

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

- Kotlin `1.9.24 → 2.4.0`
- AGP `8.9.1 → 8.10.1`
- Room `2.6.1 → 2.8.5`
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

`E:\horizon_stage2` → `C:\Horizon\horizon_stage2`

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

# 9. CURRENT STATE — 2026-10-06

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

- Post-Kotlin-migration real GNSS acquisition.
- Post-Kotlin-migration export run #1.
- Post-Kotlin-migration export run #2.
- Post-Kotlin-migration export byte/structure regression.
- Physical heading-driven skyplot rotation.
- Satellite panel orientation behavior.
- Final compact satellite naming UI.
- OrbitCore scientific reference-vector accuracy.
- Full SGP4/SDP4 production integration.
- Final Terra/GNSS integrated behavior.

## KNOWN UI BACKLOG

1. Satellite panels do not currently demonstrably rotate with device orientation.
2. Bottom labels under panels must be removed.
3. Each satellite panel must contain a compact real satellite identifier/name.
4. Satellite icon/panel design may need redesign if required for readability.
5. No fabricated satellite identifiers are permitted.
6. UI changes must preserve the underlying observed satellite evidence.

---

# 10. IMMEDIATE EXECUTION ROADMAP

## PHASE A — POST-MIGRATION REGRESSION
**Priority: P0**  
**Status: NEXT**

### A1 — Clean device launch
Acceptance:

- package launches;
- no fatal exception;
- MainActivity resumed;
- fresh runtime receipt.

### A2 — Start real recording
Acceptance:

- recording state proven;
- GNSS acquisition state proven;
- actual observation count > 0;
- timestamp/epoch evidence captured.

### A3 — Capture GNSS evidence
Acceptance:

- real `GnssMeasurement` observations;
- constellation/SVID evidence;
- C/N0 where exposed;
- raw/normalized provenance retained.

### A4 — Stop recording
Acceptance:

- stop transition proven;
- final session state proven;
- no stop race;
- final observation count recorded.

### A5 — Export regression #1
Acceptance:

- export generated;
- archive/file pulled from device;
- structure inspected;
- counts and metadata checked;
- hash recorded.

### A6 — Export regression #2
Acceptance:

- second export succeeds;
- no overwrite corruption;
- deterministic/consistent structure;
- independent hash/metadata receipt.

### A7 — Compare with pre-migration behavior
Acceptance:

- no unsupported capability silently regressed;
- any regression becomes a documented FAILED/UNVERIFIED item.

---

# 11. PHASE B — SATELLITE OBSERVATORY UI

**Priority: P1**

## B1 — Evidence-preserving satellite identity

Use actual `constellationType` + `svid` data.

Acceptance:

- compact satellite label derives from observed evidence;
- no invented names;
- missing identity remains explicitly unavailable.

## B2 — Remove panel-bottom labels

Acceptance:

- redundant labels below panels removed;
- no loss of semantic identity inside panel.

## B3 — Compact satellite name inside panel

Acceptance:

- visible at normal device size;
- readable without overlap;
- stable under dynamic observation updates.

## B4 — Orientation-aware panel layout

Acceptance:

- determine actual orientation source behavior from runtime evidence;
- panel arrangement responds only when a valid orientation signal exists;
- no fake rotation when sensor evidence is unavailable.

## B5 — Skyplot synchronization

Acceptance:

- skyplot uses verified orientation source;
- rotation direction is validated empirically;
- unavailable heading state remains explicit.

## B6 — UI regression

Acceptance:

- no satellite evidence disappears;
- no rendering crash;
- no interaction regression;
- screenshot/UITest evidence captured where reliable.

---

# 12. PHASE C — SCIENTIFIC PROPAGATION VALIDATION

**Priority: P1**

## C1 — Reference-vector acquisition

Use authoritative scientific/reference data.

Required:

- known TLE/OMM;
- epoch;
- expected state vector;
- tolerance;
- source/provenance.

## C2 — SGP4 near-earth validation

Acceptance:

- multiple epochs;
- position tolerance;
- velocity tolerance;
- deterministic repeatability.

## C3 — SDP4/deep-space validation

Acceptance:

- deep-space case;
- same tolerances;
- explicit classification;
- no accidental SGP4-only path.

## C4 — Backend contract validation

Acceptance:

- success/error/unavailable states;
- version/provenance attached;
- deterministic result serialization.

## C5 — Terra boundary validation

Acceptance:

- GNSS code does not silently depend on Terra;
- Terra consumes propagation contract only;
- no circular module dependency.

---

# 13. PHASE D — GNSS SCIENTIFIC CORE

**Priority: P2**

## D1 — Canonical immutable raw schema

Retain all Android-exposed evidence without destructive normalization.

## D2 — Capability ledger

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

## D3 — Timestamp engine

Implement/verify:

- GNSS time;
- UTC mapping;
- receiver clock bias;
- receiver clock drift;
- discontinuity detection;
- epoch identity.

## D4 — Broadcast ephemeris

Per actual available constellation:

- GPS;
- Galileo;
- BeiDou;
- GLONASS where supported;
- QZSS;
- NavIC only where data exists.

## D5 — Geometry engine

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

## D6 — Independent WLS/SPP

Outputs:

- ECEF;
- LLA;
- receiver clock bias/drift;
- covariance;
- residual vector;
- GDOP/PDOP/HDOP/VDOP;
- satellite set.

## D7 — Residual Observatory

Expose:

- per-satellite residual;
- RMS;
- distribution;
- outlier candidate;
- rejection reason.

---

# 14. PHASE E — RESEARCH-GRADE EXPORT

**Priority: P2**

## E1 — RINEX 4.01

Pipeline:

```
Raw Android evidence
→ canonical observation
→ time normalization
→ RINEX observation/navigation writer
→ validation
```

Acceptance:

- standards-conformant structure;
- provenance retained;
- no fabricated observables;
- unsupported fields remain absent/explicitly unavailable.

## E2 — JSON/CSV/SQLite

Acceptance:

- schema version;
- session ID;
- algorithm version;
- timestamp provenance;
- reproducible export.

## E3 — Deterministic replay package

Every research export should be replayable without the original UI session.

---

# 15. PHASE F — PRECISE POSITIONING

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

# 16. PHASE G — PPP / SSR / HAS

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

# 17. PHASE H — ANTENNA / GNSS-INS / FGO

**Priority: P3–P4 / research target**

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

# 18. PHASE I — INTEGRITY / SPOOFING / RFI

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

# 19. PHASE J — DETERMINISTIC REPLAY

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
→ same result
```

Any nondeterminism must be measured and documented.

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
| G7 Post-migration GNSS | Real observations | UNVERIFIED |
| G8 Export #1 | Device export | UNVERIFIED |
| G9 Export #2 | Repeat export | UNVERIFIED |
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

## 2026-10-06 — Repository baseline
**VERIFIED**

- Workspace safety backup verified.
- Repository initialized/cleaned.
- Professional .gitignore established.
- Generated/build/cache/runtime artifacts excluded.
- Existing GitHub baseline preserved.
- Private repository confirmed.
- Clean baseline pushed to `main`.
- Commit: `423577893094216aab6e4acc97bd600509ac94c3`.

## 2026-10-06 — Current roadmap initialization
**THIS CHANGE**

- Master roadmap created.
- Historical engineering work consolidated.
- Current evidence states recorded.
- Open gates recorded.
- Future execution sequence defined.
- Mandatory update-and-push protocol established.

---

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
→ git status
→ git diff
→ targeted verification
→ commit
→ push
→ verify origin/main
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

# 28. CURRENT NEXT ACTION

**NEXT EXECUTION TARGET: PHASE A — POST-MIGRATION REGRESSION**

Order:

1. clean launch;
2. start real recording;
3. verify GNSS acquisition;
4. collect real observations;
5. stop;
6. export #1;
7. export #2;
8. pull and validate exports;
9. compare with historical baseline;
10. update this roadmap;
11. commit + push;
12. only then begin Satellite Observatory UI work.

**Do not skip Phase A to make UI progress appear faster.**

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
NO RECEIPT → NO EPISTEMIC UPGRADE
NO VERIFIED END STATE → TASK NOT COMPLETE
```

This document is a living engineering ledger, not a marketing roadmap.
