# Horizon — Phase 0.5 Integrity Core

## Implemented

- Session state machine moved into a single pure `SessionStateMachine` authority.
- Lifecycle persistence now uses compare-and-set semantics in Room.
- `ACTIVE -> STOPPING -> CLOSED` is enforced by the repository; `closeSession()` no longer skips the STOPPING state.
- Sequence allocation moved to a session-scoped `SessionSequencer`.
- Observation DB uniqueness is enforced on `(sessionId, sequenceNumber)`.
- Monotonic Android elapsed-realtime timestamp is now a first-class domain field and is persisted to `ObservationEntity`.
- Evidence metadata is typed in the domain (`CapabilityState`, `EvidenceStatus`, `ObservationProvenance`) and serialized by name at persistence boundaries.
- Observation mapping is isolated from the repository in `ObservationMapper.kt`.
- Observation handoff is now a Room-backed pending queue.
- Queue drain is transactional: evidence insert + queue delete occur in one Room transaction.
- Service startup recovers pending observations before crash-closing stale sessions.
- Service shutdown closes the bounded bus, waits for the consumer to finish, transitions to STOPPING, drains persistence, and only then closes the session.
- Manual DI composition root added through `HorizonContainer`.
- Service startup no longer requires `READ_PHONE_STATE`; cellular observation degrades independently when that optional capability/permission is unavailable.
- Unit tests added for state transitions, session-scoped sequencing, concurrency, and typed evidence defaults.
- Project target/compile SDK raised to API 36; AGP/wrapper metadata raised to an Android 16-compatible baseline.

## Not yet verified

- Android build: the repository still lacks `gradle/wrapper/gradle-wrapper.jar` and this environment has no local Android SDK installation.
- Room migration execution on an actual database file.
- Runtime behavior on Samsung Galaxy A07 / SM-A075F.
- GNSS raw measurement support.
- Android 16 foreground-service behavior on the target device.
- Long-running queue durability under process death.

## Important boundary

This phase does not add fake measurements, satellite data, sensor values, or hardware capabilities. Real-device verification remains mandatory before those capabilities can be marked AVAILABLE_NOW/MEASURED.

## Android 16 build baseline

The build is now configured for `compileSdk = 36` and `targetSdk = 36`. Official Android 16 setup guidance requires AGP 8.9.0-rc01 or newer; the project metadata was therefore raised to AGP 8.9.0 and Gradle 8.11.1. This remains unverified here because the wrapper JAR and local SDK are absent.
