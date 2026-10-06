# HORIZON — Engineering Handoff / Checkpoint Package

## Frozen state

This package is the handoff point immediately after:

- session-stop race hardening
- `SessionLifecycleState` import repair
- complete `ObservatoryService` import restoration
- successful compile reported by the project owner
- no-crash physical-device launch reported by the project owner

## What is inside

- Complete source checkpoint
- Existing architecture and engineering documentation
- Existing GNSS-domain and runtime evidence
- Latest real-device session export
- Stop-race patch
- Local JSON unit-test dependency patch
- Exact final source diff for the stop/import repair
- New checkpoint and verification notes

## Explicit non-claims

This package does not claim:

- a production release
- an independently verified final APK
- completion of long-running performance qualification
- completion of repeated-STOP stress testing
- completion of the next correlation/performance phase

## Next gate

Use this checkpoint as the source of truth for the next cycle. Re-run:

1. unit tests
2. assembleDebug
3. install on SM-A075F
4. one controlled 2–5 minute session
5. repeated STOP test
6. memory/PSS trend capture
7. export/readback/integrity verification

---

## SESSION ADDENDUM (this thread, appended not overwritten)

- Fixed: SensorObservationSource.kt values-array used implicit List serialization in
  JSONObject.put -- changed to explicit JSONArray(values).
- Added: TS installation-identity product flavor (app/build.gradle.kts, manifest
  android:label placeholder) -- same code/behavior, second installable identity only.
  primaryRelease keeps applicationId unchanged.
- Added: domain/assistance (AssistanceEvidenceModels, AssistanceEvidenceRecorder) +
  analysis/assistance/AssistanceCorrelationEngine -- A-GPS modeled as Assistance Evidence,
  not a GNSS observation. No automatic Android source exists (no public API exposes
  SUPL/A-GPS); recorder is caller-driven infrastructure only.
- Added: astronomy/ package (Phase 1 of AR Observatory) -- SatelliteState, OrbitPropagator
  (two-body Kepler, explicitly NOT SGP4), CoordinateTransforms, ObserverModel,
  TopocentricCalculator (+ batch path), VisibilityCalculator, OrbitDataSource contract.
  6 deterministic unit tests, hand-verifiable (not validated against real ephemeris).
- STATUS: none of the above compiled or executed in this environment (no Android
  SDK/Gradle runtime available). All items UNVERIFIED pending a real build/test run.
