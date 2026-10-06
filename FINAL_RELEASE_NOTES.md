# HORIZON Observatory — Final Release Notes

## Scope

This package completes the planned software path through the Compose Observatory UI while preserving the evidence boundaries required by the engineering specification.

## Completed software path

```text
Capability discovery
      ↓
GNSS / Cellular / Sensors / PVT acquisition
      ↓
Typed domain observations
      ↓
Per-session sequence + monotonic time
      ↓
Room-backed durable handoff
      ↓
Evidence storage
      ↓
Integrity audit
      ↓
Local analysis
      ↓
Atomic export
      ↓
Compose Observatory UI
```

## Physical-device boundary

No software artifact can prove a hardware capability merely by compiling an Android API call. The SM-A075F verification stage therefore remains explicit and is documented in `docs/A07_DEVICE_VALIDATION.md`.

## Build boundary

The supplied repository did not contain `gradle-wrapper.jar`, and this execution environment does not provide the Android SDK or system Gradle. The package includes a Windows build script that can generate the wrapper and run the complete Android build/test sequence on a development machine.

## Evidence policy

No production code generates synthetic GNSS, cellular, or sensor observations. Missing fields remain explicit and capability states remain separate from evidence/observation provenance.

## Post-release correction

- Fixed `SessionSequencerTest.everySessionStartsAtOne`: the original test incorrectly called the same sequencer twice while expecting `1` both times. It now verifies two independent session sequencers each begin at `1`.
- Corrected `GnssAntennaInfo.Listener.onGnssAntennaInfoReceived` to Kotlin's Android API signature (`MutableList<GnssAntennaInfo>`). Registration is supported from API 30; `getGnssAntennaInfos()` remains guarded to API 31.
- Added `scripts/DIAGNOSE_BUILD_WINDOWS.ps1` to run the unit-test task and surface the Gradle root-cause section in a compact way.
