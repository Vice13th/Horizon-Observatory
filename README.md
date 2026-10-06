# HORIZON Observatory — Final Engineering Baseline

Horizon is a local-first Android scientific observation platform. The application preserves raw observations and provenance first; derived analysis and UI are downstream.

## Target

- Device: Samsung Galaxy A07 / SM-A075F
- Android: 16 / API 36
- minSdk: 26
- compileSdk / targetSdk: 36
- Application ID: `horizon.observatory`

## Architecture

```text
Android APIs
    |
    +--> GNSS acquisition
    +--> Cellular acquisition
    +--> Sensor acquisition
    +--> Location/PVT acquisition
              |
              v
        Domain observations
              |
              v
      SessionSequencer (per session)
              |
              v
     Room-backed handoff queue
              |
              v
        Room evidence store
              |
              +--> Analysis
              +--> Export
              +--> Compose Observatory UI
```

## Integrity guarantees

1. Session lifecycle transitions are validated and persisted with compare-and-set semantics.
2. Observation sequence numbers are allocated per session, not process-wide.
3. `(sessionId, sequenceNumber)` is unique in the evidence store and pending queue.
4. Android monotonic timestamps are propagated from acquisition into persistence/export when exposed.
5. Pending observations are durably staged before being drained into the evidence table.
6. Drain moves each observation to evidence and deletes its pending row in one Room transaction.
7. Domain models are mapped explicitly to persistence entities.
8. Capability/evidence/provenance metadata use typed enums in the domain layer.
9. Production acquisition never fabricates GNSS, cellular, or sensor values.
10. Raw, normalized, derived, and model-level data are kept conceptually separate.

## Acquisition

- GNSS raw measurements
- GNSS satellite status
- GNSS navigation messages
- GNSS antenna information where exposed
- GNSS/PVT location fixes
- GSM/WCDMA/LTE/NR cell information where Android exposes it
- runtime sensor discovery and event acquisition

All optional capabilities are treated as runtime facts. `UNVERIFIED` is used where documentation alone is insufficient to establish device behavior.

## UI

The Compose Observatory exposes:

- Overview
- GNSS
- Cellular
- Sensors
- Timeline
- Session history
- Diagnostics / capability report

The UI only visualizes stored/observed data; it does not become the measurement engine.

## Export

A session export is generated as an atomic ZIP containing:

- `dataset_manifest.json`
- `observations.json`
- `observations.csv`

The export verifies Room count, JSON count, CSV row count, and manifest count before finalization.

## Build

The repository intentionally records a missing `gradle-wrapper.jar` condition. On a development machine with Gradle 8.11.x available, run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\BUILD_WINDOWS.ps1
```

The script generates a wrapper if needed, then runs the Android build and unit tests.

## A07 validation

After a successful build and with the physical SM-A075F connected by ADB:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\VERIFY_A07.ps1
```

That script checks device identity/API level, installs the debug APK, checks the declared permissions, and captures an initial logcat diagnostic snapshot.

## Verification status

The engineering package is structurally complete through the Compose Observatory stage. **Physical-device verification remains an evidence gate, not a claim.** The current container has no Android SDK/ADB and cannot honestly mark the SM-A075F runtime stage as VERIFIED.

## Windows build bootstrap

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\BUILD_WINDOWS.ps1
```

If `gradle-wrapper.jar` is absent and no system Gradle is installed, the script bootstraps Gradle 8.11.1 into `.gradle-bootstrap` and generates the wrapper automatically.
