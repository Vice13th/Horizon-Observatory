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
