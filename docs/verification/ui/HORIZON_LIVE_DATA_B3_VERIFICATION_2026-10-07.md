# Horizon Live Data B3 Verification — 2026-10-07

## Scope

Repair and verify the frozen Live Sky / GNSS live-data path without changing raw evidence semantics.

## Root-cause evidence

- GNSS acquisition and persistence continued while the Compose Room observation Flow produced only its initial emission during the prior runtime audit.
- The observation database file grew during active acquisition while the visible sky/content region remained unchanged.
- Overview and GNSS were already sharing the same renderer, so the freeze was upstream of the renderer.
- A stale persisted RECORDING session could also leave the START control disabled after process death/restart; this blocked recovery on the test device.

## Implemented

1. Added LiveObservationBus as a lightweight post-persistence UI signal path.
2. ObservatoryService publishes the exact persisted ObservationEntity identity (session + sequence + type + monotonic ingestion timestamp) only after the persistence queue drains.
3. UI consumes sampled live signals with collectAsStateWithLifecycle().
4. On each live signal, UI re-reads the authoritative bounded Room snapshot; the bus does not replace raw evidence.
5. Live session state is refreshed from SessionRepository.getLatestSession() so a missed Room session-row invalidation cannot strand the UI on a stale session.
6. Overview and GNSS now receive the same ObservedSkyState instance.
7. Live Sky is labelled LIVE SKY only for an active recording/stopping session; completed sessions are labelled OBSERVED SKY.
8. RoomObservationPersistenceQueue explicitly requests an invalidation refresh after successful drains as a defensive consistency measure.
9. START is no longer blocked by a stale persisted RECORDING/STOPPING state; the service owns idempotent start/recovery behavior.

## Verification

### Build

- :app:assemblePrimaryDebug — PASS
- Final APK SHA-256: 6452A10C275028FEFB9C8E5A2F64ED616293F5509E0E90E7BC81E7142065915E

### Unit tests

- :app:testPrimaryDebugUnitTest — FAIL, pre-existing unrelated test compilation error
- Failure remains at app/src/test/kotlin/horizon/observatory/resilience/ResilienceEngineTest.kt:68 because a nullable Double? is used as a non-null Double.
- No test source was modified for this live-data change.

### Device

Device: Samsung SM-A075F / Android 16 / API 36.

Verified on the installed final APK:

- Foreground ObservatoryService starts successfully.
- A stale RECORDING state can be recovered/start requested from the UI.
- Active session reaches RECORDING.
- Real GNSS data is present in the GNSS domain.
- Overview Live Sky displayed 18 latest satellites.
- GNSS Live Sky displayed 18 latest satellites.
- Both views use the same ObservedSkyState source in code.
- GNSS screen showed HISTORY SAMPLES: 96, GNSS DOMAIN with latest satellite evidence · correlated=103 · nav=3, and LIVE SKY with 18 latest satellites.
- Device reports no rotation-vector sensor; UI correctly remains north-up and states that live orientation is unavailable.
- No FATAL EXCEPTION / AndroidRuntime crash was observed in the final smoke-test log.
- Session was stopped cleanly after verification and returned to COMPLETED / IDLE.

### Live-path diagnostic proof

A temporary diagnostic build added logs only for verification, then those logs were removed before the final build.

Observed diagnostic behavior:

- HorizonLiveBus emitted continuously advancing persisted sequence numbers, including GNSS_RAW_MEASUREMENT and GNSS_STATUS.
- HorizonLiveUi received sampled signals at approximately 250 ms cadence with advancing sequence numbers.
- This proves the live signal path reached the UI independently of Room invalidation.
- The final installed build contains no diagnostic logging.

## Epistemic boundary

Verified:
- Acquisition, persistence, live signal transport, UI collection, GNSS domain update, and Overview/GNSS sky-state consistency.

Not verified:
- Physical heading-up orientation on this device, because the device has no rotation-vector sensor.
- Scientific orbital propagation accuracy (SGP4/SDP4).
- Long-run K8/interference behavior.

No satellite identity beyond the repository's verified constellation/SVID evidence was inferred.
No raw observation was rewritten or synthesized.
