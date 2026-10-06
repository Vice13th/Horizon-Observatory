# HORIZON — Current Engineering Checkpoint
Date: 2026-09-24

## Scope

This checkpoint freezes the HORIZON state **after the session-stop race fix, lifecycle-state import repair, and successful Kotlin compilation reported by the project owner**, followed by a physical-device launch with no observed crash.

This is a **stage checkpoint / handoff package**, not a final release claim.

## Source basis

The checkpoint is based on the `HORIZON_GNSS_DOMAIN_AUTOMATED_2026-09-24` project baseline already carrying the prior GNSS-domain, runtime export, integrity, UI, analysis, and test foundations.

The following final-stage changes are present in this checkpoint source:

1. `SessionLifecycleState` is explicitly imported in `ObservatoryService.kt`.
2. `stopMutex` serializes STOP handling.
3. STOP handling re-reads the active session and lifecycle state before calling `beginStopping`.
4. Duplicate/non-recording STOP requests are ignored instead of attempting an illegal transition.
5. The existing `NonCancellable` persistence boundary and ingress mutex remain intact.
6. The local JVM `org.json` test dependency remains present from the earlier unit-test repair.

## Build / runtime status

### Reported by project owner

- `:app:compileDebugKotlin` succeeded after the import/symbol repair.
- Application launched on the physical Samsung SM-A075F.
- No crash was observed during the latest manual run.
- User reported the application was somewhat slow near the end of the run.

### Not independently re-executed in this packaging environment

- Android Gradle build on the Windows host.
- Final `testDebugUnitTest` after the last import repair.
- Final APK installation from this exact packaged source tree.

Therefore those gates are explicitly left as **OWNER-REPORTED / PENDING RE-RUN**, not independently certified here.

## Latest real-device session evidence

Source: `evidence/latest/horizon_session_c40cd3d7-f824-4e05-bfa3-95707110ea97.zip`

- Session: `c40cd3d7-f824-4e05-bfa3-95707110ea97`
- Lifecycle: `COMPLETED`
- Device: `samsung SM-A075F`
- Android: `16` / API `36`
- Duration: `169.755 s`
- Observations: `10961`
- Approx. ingestion rate: `64.57 obs/s`
- Error events: `0`
- Sequence: `1..10961`, contiguous = `True`
- Ingestion timestamps: non-decreasing = `True`
- Source timestamp regressions: `1480`; these are retained as source-time evidence and are not treated as ingestion-order failures.
- Integrity audit: clean = `True`

### Observation mix

{
  "SYSTEM_EVENT": 2,
  "SENSOR_LIGHT": 761,
  "CELLULAR_INFO": 301,
  "SENSOR_ACCEL": 7852,
  "SENSOR_PROXIMITY": 1,
  "GNSS_STATUS": 168,
  "GNSS_RAW_MEASUREMENT": 1681,
  "GNSS_FIX": 154,
  "GNSS_NAVIGATION_MESSAGE": 41
}

### Source mix

{
  "SYSTEM": 2,
  "SENSOR": 8614,
  "CELLULAR": 301,
  "GNSS": 2044
}

## Known current issue

The latest manual run was reported as **somewhat slow** near the end. This checkpoint does not claim a root cause yet.

The next engineering investigation should focus on:

- UI refresh cadence and whether the UI re-reads/analyzes the full observation set repeatedly.
- Session-analysis allocations over a growing dataset.
- queue drain / database write contention.
- foreground service and sensor/GNSS callback scheduling.
- process RSS/PSS trend during a controlled 2–5 minute run.

No performance fix is included in this checkpoint unless supported by a concrete source change and measurement.

## Release boundary

This package must not be labeled a production/final release. It is the authoritative **2026-09-24 post-stop-race-fix checkpoint** for the current development line.
