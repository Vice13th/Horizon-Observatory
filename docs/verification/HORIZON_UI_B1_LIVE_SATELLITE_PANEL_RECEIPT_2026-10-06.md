# HORIZON UI B1 — LIVE GNSS + COMPACT SATELLITE PANEL RECEIPT
## 2026-10-06 — live elevation correction

STATE: IMPLEMENTED / BUILD VERIFIED / DEVICE RUNTIME VERIFIED

## Root cause of the non-live elevation panel
The Room observation Flow was live, but the Compose analysis trigger was incorrectly keyed by `observations.size`.
Active sessions are intentionally bounded to LIVE_OBSERVATION_WINDOW = 512. Once the list reached 512 rows, its size stopped changing, so `LaunchedEffect(..., observations.size)` stopped rebuilding `SessionAnalysisEngine`.
Result: new GNSS status/raw observations continued arriving, but the derived `analysis.gnssDomain.latestSatelliteEvidence` feeding the satellite/elevation UI became stale.

## Fix
File:
- app/src/main/kotlin/horizon/observatory/ui/MainActivity.kt

Changed the analysis trigger from:
- LaunchedEffect(latest session/lifecycle, observations.size)
to:
- observationVersion = observations.lastOrNull()?.sequenceNumber
- LaunchedEffect(latest session/lifecycle, observationVersion)

This preserves the bounded 512-row live window while causing the derived GNSS domain snapshot to rebuild whenever the newest persisted observation changes.

## Acquisition path verified in source
GnssObservationSource registers Android GnssMeasurementsEvent.Callback and GnssStatus.Callback.
GnssStatus callback records azimuth, elevation, C/N0, usedInFix and a monotonic timestamp as RAW_ACQUISITION / MEASURED GNSS_STATUS observations.
SessionRepository exposes Room observations as a Flow.
GnssCorrelationEngine derives latestSatelliteEvidence from those persisted observations.
ObservedSkyState and SatellitePanelField consume that derived evidence.

## Device runtime evidence
Device: Samsung SM-A075F / R8YY92YWAAF / Android 16 API 36.
Foreground ObservatoryService was observed with `isForeground=true`.
Fresh HorizonHealth evidence showed an active RECORDING session with observationCount=24601 and acquisitionRunning=true before the final rebuild/install cycle.
After installing the corrected APK, a RECORDING session was observed with raw GNSS and GNSS STATUS counts increasing in the UI.
GNSS UI hierarchy contained the live SATELLITES panel and elevation values such as G13 EL 83°, G19 EL 7°, G21 EL 27°, G24 EL 21°, G25 EL 57°, G28 EL 14°.

## Build / install
- git diff --check: PASS
- :app:compilePrimaryDebugKotlin --offline --no-configuration-cache: PASS
- :app:assemblePrimaryDebug --offline --no-configuration-cache: PASS
- install: SUCCESS
- launch: SUCCESS
- APK SHA-256: 92044ABE4F95CEFF696DB6366E687319DDE2D7917C17F6D1E61A074B9B9528FC

## Important evidence boundary
GNSS status callbacks and Room observations are live. The UI analysis trigger defect is fixed.
Integer-rounded elevation can remain visually unchanged for several seconds because satellite elevation changes continuously but slowly; unchanged rounded values alone are not evidence of a stale pipeline.
A stronger freshness indicator can be added later if desired, but it is not required to fix the identified stale-analysis bug.

## Scientific firewall
No changes to GNSS raw acquisition semantics, raw payloads, Room schema, export semantics, satellite identity, NORAD resolution, OrbitCore, SGP4/SDP4, or propagation mathematics.
No commit, push, reset, merge, or history rewrite.