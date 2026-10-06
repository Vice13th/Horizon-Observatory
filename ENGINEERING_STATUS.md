# HORIZON — Final Engineering Status

## STATUS

**PARTIAL — implementation-complete baseline, physical-device verification pending.**

The repository now contains the intended end-to-end architecture from acquisition through persistence, analysis, export, diagnostics, and Compose Observatory UI. The final status deliberately remains PARTIAL because the available execution environment does not contain Android SDK/ADB and no physical Samsung SM-A075F session was performed here.

## CHANGED

### Integrity Core

- Session state machine enforced through validated transitions and Room compare-and-set lifecycle updates.
- Session-scoped sequence allocation.
- Unique `(sessionId, sequenceNumber)` persistence constraint.
- Monotonic timestamp propagation into observation entities and export.
- Room-backed pending-observation queue.
- Transactional queue drain.
- Crash-recovery path for unclosed sessions.
- Domain-to-entity mapping.
- Typed evidence/capability/provenance metadata.
- Manual dependency-injection composition root.

### Acquisition

- GNSS measurements callback.
- GNSS status callback.
- GNSS navigation message callback.
- GNSS antenna information when exposed.
- GNSS/PVT location fixes.
- Cellular observation with explicit refresh request on supported APIs and timestamps.
- Runtime sensor discovery and sensor event acquisition.
- Explicit unavailable/null semantics; no fabricated values.

### Analysis

- Session-level counts.
- C/N0 and RSRP aggregates.
- Visible satellite aggregate.
- Sequence-gap audit.
- Per-provider monotonic timestamp audit.
- Sensor magnitude aggregate.
- Unified timeline projection.

### Export

- Manifest with device/capability/permission metadata.
- JSON raw observations.
- CSV tabular view.
- Count reconciliation before finalization.
- Atomic ZIP finalization.

### UI

Compose Observatory shell with:

- Overview
- GNSS
- Cellular
- Sensors
- Timeline
- Session history
- Diagnostics / capability evidence

## VERIFIED IN THIS ENVIRONMENT

- Source-tree inspection.
- Pure Kotlin domain compilation.
- Session sequence concurrency smoke test.
- Session lifecycle state-machine tests.
- Integrity audit tests.
- SQLite migration logic smoke verification performed during Phase 0.5 work.
- Static checks for removal of the obsolete in-memory acquisition bus from the production path.

## NOT VERIFIED IN THIS ENVIRONMENT

- Full Gradle Android build.
- APK installation.
- Android 16 runtime behavior.
- Room generated-code compilation through Gradle.
- Samsung SM-A075F physical-device behavior.
- Actual GNSS raw measurement availability.
- Actual GNSS navigation message availability.
- Actual GNSS antenna information availability.
- Actual ADR/carrier-phase/AGC exposure.
- Actual cellular freshness semantics on the target firmware.
- Actual sensor inventory.
- Long-run battery/memory behavior.
- Real export against a physical-device session.

## RELEASE GATE

The package should not be labelled field-verified until all of the following are captured on the SM-A075F:

1. build + install success;
2. capability report;
3. permission matrix;
4. GNSS raw/status/nav/antenna result;
5. cellular observation result and timestamp/freshness evidence;
6. sensor inventory/result;
7. session start/stop/recovery evidence;
8. sequence audit with zero gaps/duplicates;
9. monotonic timestamp audit;
10. export count reconciliation;
11. long-run session evidence.


## B0 — PANEL RECOVERY FIREWALL — 2026-10-06

**VERIFIED:** bounded live observation projection on SM-A075F; complete-session persistence preserved.

Live RECORDING UI now consumes a maximum 512-observation projection. Completed sessions continue to consume the full Room snapshot. Real-device instrumentation completed 11 tests with zero failures; the active session reached 512 live observations and later completed with 9,522 persisted observations. APK SHA-256: `4CCE1A611F596A574C6FE9A215A588A189C61843C0FE8B8F4564BA66D8D4B3DA`. Launch/record/complete crash buffers were empty.

**UNVERIFIED/BLOCKED:** physical heading/panel rotation on this target because no rotation-vector sensor is available. Performance remains a follow-up measurement: the debug gfxinfo aggregate showed 1,139 frames and 94 janky frames (8.25%).


## K FAST-TRACK — 2026-10-06 FOUNDATION RECEIPT

**K0: IMPLEMENTED / DEVICE CAPABILITY OBSERVED**

The existing capability scanner already persists a session-start capability report. A typed `CapabilityContract` now provides explicit AVAILABLE / UNAVAILABLE / UNKNOWN states with provenance and observation time for raw GNSS, C/N0, AGC, pseudorange/received-SV-time, Doppler, ADR/carrier phase, navigation messages, multi-constellation/frequency, IMU/orientation, cellular, last-trusted PVT, timestamps and measurement age. No unavailable hardware capability is inferred.

**K1-K4: DOMAIN ENGINES IMPLEMENTED + JVM TESTED; SERVICE INTEGRATION PENDING**

Implemented deterministic interference evidence assessment, measurement trust ranking, debounced navigation continuity state machine, and last-trusted-PVT dead-reckoning bridge. Raw evidence is not mutated. Interference labels remain hypotheses. Dead-reckoned estimates carry explicit `DEAD_RECKONED_FROM_LAST_TRUSTED_PVT` provenance and growing uncertainty.

Receipt: `:app:testPrimaryDebugUnitTest :app:testT1DebugUnitTest` — BUILD SUCCESSFUL, including `ResilienceEngineTest` 6/6. A prior RED receipt caught and fixed an empty-evidence state-classification bug before this green run.

**Not yet a K0-K4 PASS:** the new engines are not yet wired into `ObservatoryService`/Room-derived resilience events.
