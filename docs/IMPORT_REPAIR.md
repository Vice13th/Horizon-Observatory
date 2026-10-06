# HORIZON — ObservatoryService Import Repair

## Incident

A previous PowerShell edit injected a malformed import:

`import .SessionLifecycleState`

This caused `kaptGenerateStubsDebugKotlin` to fail with a parser error.

After replacing it with the fully qualified import, compilation exposed a second problem: the current `ObservatoryService.kt` import block had lost multiple domain/acquisition/storage imports.

## Repair method

The imports were reconstructed from the actual Kotlin source tree rather than guessed.

The restored symbols include:

- `CoroutineScope`
- `SessionRepository`
- `ObservationPersistenceQueue`
- `LocationFixSource`
- `GnssObservationSource`
- `CellInfoSource`
- `SensorObservationSource`
- `HealthReporter`
- `SessionSequencer`
- `HealthSnapshot`
- `IntegrityAudit`
- `RawObservationView`
- `TimestampEngine`
- `RawObservation`
- `ObservationType`
- `CapabilityState`
- `EvidenceStatus`
- `ObservationProvenance`
- `TimestampDomain`
- `SessionLifecycleState`

## Result

The project owner subsequently reported that the compile step succeeded.

This checkpoint preserves the repaired import block in source.
