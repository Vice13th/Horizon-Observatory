# Orbit Prediction Foundation — architecture and status

Checkpoint: orbit-prediction foundation on top of `HorizonObservatory_FINAL_MERGED__4_.zip`.
**BUILD: UNVERIFIED. TEST EXECUTION: UNVERIFIED. DEVICE: UNVERIFIED.** The authoring environment had no
JDK 17, Gradle, Android SDK or network. Everything below marked IMPLEMENTED means "code exists and was
statically re-read"; it does not mean compiled, tested, or scientifically validated.

## Non-negotiable equivalences

| Never equal | Enforced by |
|---|---|
| OBSERVED ≠ PREDICTED | `PredictedSatelliteState` is a separate type; `SatelliteEvidence` is untouched; `PredictionEvidenceClass` has the single value `DERIVED_PREDICTED` |
| SVID ≠ NORAD | `GnssSatelliteId` (receiver side) vs `SatelliteIdentityMapping` (only `Matched` exposes a NORAD id) |
| OMM ≠ propagation result | `OmmRecord` (source elements) vs `TemeState` / `PredictedGeometry` (derived) |
| device clock ≠ GNSS-verified UTC | `TimeBasis.DEVICE_CLOCK_UTC`; `TimestampEngine` UTC = one wall-clock sample at process start + elapsed real time |
| two-body ≠ SGP4/SDP4 | legacy `OrbitPropagator` is documented non-authoritative and is not behind `OrbitPropagationEngine` |
| stale ≠ live | `OrbitFreshness`, `CatalogStaleness`; retrieval time is never reset by reads |
| missing ≠ zero | required OMM fields are never defaulted; non-matched predictions carry no geometry |

## Chain

```
Observed GnssSatelliteId (constellation + SVID, from the observed layer)
  -> SatelliteIdentityResolver          MATCHED | UNMATCHED | AMBIGUOUS | UNVERIFIED (+ provenance, validity window)
  -> OrbitDataSource (Room)             CatalogedOrbit = OmmRecord + OrbitProvenance
  -> OrbitPropagationEngine             Sgp4Sdp4PropagationEngine over an Sgp4Sdp4Backend (none integrated)
  -> PredictionFramePipeline            TEME -> ECEF -> observer ENU -> az / el / range
  -> PredictedSatelliteState            status, freshness, staleness, provenance, geometry only if PREDICTED
  -> PredictionLayerStateHolder         separate prediction layer state (UI not modified)
```
Any stage can stop the chain; the stopped state has a non-PREDICTED status and no geometry.

## Time contract
- Unit: Unix-time milliseconds, interpreted as UTC (no leap-second accounting; consistent across OMM epoch, device clock).
- Types: `CatalogEpochUtcMs`, `RetrievalTimeUtcMs`, `ObservationTimeUtcMs`, `MonotonicElapsedNanos`, `PropagationTime(utcMillis, basis)`.
- Monotonic time never enters propagation. No GPS-time/TAI conversion exists anywhere in this pipeline.
- Sidereal time treats UTC as UT1 (|UT1−UTC| < 0.9 s). Polar motion ignored. See `PredictionFramePipeline`.

## Catalog storage (Room v7)
- `orbit_catalog` holds the complete OMM record in source units, exact `epochRaw`, and provenance
  (`catalogSource`, `catalogSourceIdentifier`, `catalogFormat`, `retrievedAtUtcMillis`).
- `MIGRATION_6_7` DROPS the v6 lossy table (no BSTAR, no epoch text) and creates the new one. v6 rows cannot be
  upgraded without fabricating SGP4 inputs; the table is a re-fetchable cache of public data. Session,
  observation and evidence tables are not touched. `MIGRATION_5_6` is unchanged.
- Merge rule: an incoming record with an OLDER epoch than the stored one is rejected; equal epoch updates retrieval time.

## Fetching
`OrbitCatalogFetcher` (interface) / `HttpUrlConnectionOrbitCatalogFetcher`: https only, GET, no user/device data,
redirects not followed, bounded payload, explicit results for success / HTTP failure / timeout / empty / too large /
network failure / invalid request. Cancellation rethrows. `OrbitCatalogIngestor` stores only after a successful
fetch AND ≥1 accepted record and publishes a local `OrbitCatalogIngestReport`. **Nothing calls `ingest` yet; the app
does not fetch automatically.** The default CelesTrak URL is configuration, UNVERIFIED until run.

## Live state, device pose and the dynamic observed sky plot (package `horizon.observatory.live`)
Flow (presentation only; nothing here is stored or written back):
```
SatelliteEvidence (observed, receiver az/el, true-north)  -> ObservedSkyState   (typed observed layer; missing az/el excluded + counted, never placed at 0)
TYPE_ROTATION_VECTOR sample -> PoseMath -> DeviceOrientationState                (Available | HeadingUndefined | Unavailable)
GeomagneticField declination (from latest recorded fix)  -> PoseMath.toTrueNorth  (magnetic -> true heading)
(observed az/el, orientation decision) -> SkyProjection.project                  -> plot coordinates
```
- Pose is a PRESENTATION input. It never mutates observations, evidence, or predicted values; projection returns new values.
- Heading = bearing of the device top edge, magnetic reference (rotation-vector convention); converted to true north only with
  an explicit declination. No declination (no fix) => plot stays north-up and says so. No fake rotation anywhere.
- Heading is undefined when the device is tilted > 60 deg from flat (policy constant, not physics) => `HeadingUndefined`, plot stays north-up.
- The plot rotates only while a session is ACTIVE: rotating a plot of recorded satellite positions by the current heading is meaningless.
- `RotationVectorOrientationSource` registers its sensor listener only while the GNSS tab is visible, resumed and live; independent of recording.
- Prediction layer is NOT drawn on the sky plot yet (next step); the observed plot never receives predicted values.
- UNVERIFIED: runtime behaviour on a device, SensorEvent.timestamp domain across devices, WMM declination accuracy, `LocalLifecycleOwner` (deprecated in newer lifecycle, still present with the pinned Compose BOM).

## Status

| Area | IMPLEMENTED | VERIFIED | UNVERIFIED / UNKNOWN |
|---|---|---|---|
| Full OMM model + parser | yes | — | compile, tests not run |
| Room v7 entity/DAO/migration | yes | — | compile, kapt schema export, migration run |
| Fetcher + ingestor | yes | — | compile, loopback tests not run, real endpoint not contacted |
| Identity mapping | yes (empty table) | — | no authoritative table exists → everything UNMATCHED |
| Propagation boundary | yes | — | **no SGP4/SDP4 backend; no reference vectors imported** |
| Frame pipeline | yes | — | reuses existing GMST chain; no ephemeris-grade validation |
| Prediction use case / layer state | yes | — | not called from UI |
| Live pose + orientation-aware observed sky plot | yes | pose math geometry covered by JVM tests (not run) | on-device behaviour, declination accuracy, rendering |
| Schema baselines 5.json / 6.json / 7.json | no | — | must be generated by building MERGED3 / MERGED__4_ / this source |

## Schema baselines (required before migration tests mean anything)
1. Build MERGED3 (Room v5) with schema export → commit `app/schemas/horizon.observatory.storage.room.AppDatabase/5.json`.
2. Build MERGED__4_ (Room v6) → commit `6.json`.
3. Build this source (Room v7) → commit `7.json`.
4. Run `connectedPrimaryDebugAndroidTest`. `OrbitCatalogMigrationTest` **skips** when a baseline is missing; a skip is not a pass.
Do not hand-write these JSON files.

## Known limitations
- No SGP4/SDP4 implementation: every prediction currently ends `ENGINE_UNAVAILABLE`.
- Mean-motion convention (Kozai vs Brouwer) handling is the backend's responsibility and must be confirmed per library.
- SGP4/SDP4 accuracy for GNSS satellites from public GP data is not characterized here; it must be measured, not assumed.
- Identity table is empty; no automatic SVID→NORAD inference exists by design.
- `INTERNET` permission is declared and now has a consumer (`HttpUrlConnectionOrbitCatalogFetcher`), but no call site triggers it.
