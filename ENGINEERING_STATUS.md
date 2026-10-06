# HORIZON — CANONICAL CURRENT STATUS / 2026-10-06

> **Canonical remote source:** Vice13th/Horizon-Observatory / main  
> **Current remote HEAD at reconciliation:** e719d88f6cd7e4dc2c83fa29750009356ce7154e  
> **Current checkpoint:** HORIZON_CHECKPOINT_2026-10-06_GNSS_FOUNDATION_VERIFIED

## Current verdict

**GNSS evidence foundation: VERIFIED / CLOSED.**  
**K0–K7 resilience software integration: VERIFIED / DEVICE-OBSERVED where explicitly receipted.**  
**Overall project: PARTIAL** because NORAD identity resolution, device-specific UTC edge cases, physical orientation evidence, scientific OrbitCore reference-vector accuracy, and K8 physical/long-run validation remain open.

**Reconciliation rule:** older sections below are historical receipts. They must not override the latest dated receipts or the current verification matrix.

**Historical workspace paths** recorded in older handoffs, including `C:\Horizon\horizon_stage2`, are evidence of a prior local workspace only. Never assume those local changes exist on the current machine or were pushed to main without a Git receipt.

## Latest verified device/runtime evidence

- Target: **Samsung SM-A075F / Android 16 / API 36**
- Latest runtime session: **10,961 observations / 0 error events**
- Sequence: **1..10,961 contiguous**
- Ingestion timestamps: **non-decreasing**
- Integrity audit: **clean**
- GNSS evidence observed: raw measurements, navigation messages, ADR, multi-frequency
- Cellular fresh-update API: supported
- Antenna information: unsupported on the observed target
- Carrier phase capability: unverified
- AGC capability: unverified

Fresh GNSS foundation closure separately verified **3,250 observations** end-to-end through acquisition → Observation Bus → Room → readback → export → checksum → semantic normalization.

## Current UI truth

UI redesign is authorized and remains **presentation-layer only**.

Current source evidence shows the observatory UI has correlated observed azimuth/elevation sky projection, explicit orientation-decision plumbing, compact satellite evidence derived from stored GNSS state, bounded live-recording projection, and Room-backed observation flow.

The historical **UI B1 handoff from C:\Horizon\horizon_stage2 is NOT a GitHub/main receipt**. Its claimed receipt file is not present on main and therefore cannot be used as current repository evidence.

## Open gates

- **NORAD mapping:** UNKNOWN; resolver intentionally remains empty-table.
- **Device-specific UTC edge case:** UNVERIFIED.
- **Physical orientation / panel rotation:** UNVERIFIED/BLOCKED on the target evidence currently available.
- **Scientific OrbitCore reference vectors:** UNVERIFIED.
- **K8 physical interference / long-run resource validation:** PENDING.
- **Release readiness:** NOT CLAIMED.

---
## STATUS

**PARTIAL — see the canonical current-status block above. Historical receipts below remain immutable evidence.**

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


## K5-K7 SOFTWARE RECEIPT — 2026-10-06

**K5 Emergency Navigation:** persistent enable/disable controller is implemented and wired into the existing foreground `ObservatoryService`. When enabled, the service requests `START_STICKY`; on null restart intent it can resume the service path. Device invocation from ADB shell was correctly rejected because the service remains `exported=false`; this is a security boundary, not a crash. UI-triggered lifecycle and screen-off/background continuity remain DEVICE-UNVERIFIED.

**K6 Reception Optimization:** explicit ON/OFF policy and deterministic policy engine implemented. It only affects derived trust policy; raw observations are untouched. No RF/antenna gain is claimed.

**K7 Horizon Resilient Location:** explicit read-only `ContentProvider` contract implemented at `content://horizon.observatory.resilient-location/latest`, protected by a dedicated read permission. Published snapshots preserve navigation state and provenance. Android instrumentation verified the provider contract on SM-A075F. Android mock/test-location injection is intentionally not implemented/claimed.

Receipt: `:app:connectedPrimaryDebugAndroidTest` — 14 tests completed, 0 failures; 2 migration tests skipped by existing contract.


## K0-K7 INTEGRATION / K8 CONTROLLED-LOSS RECEIPT — 2026-10-06

**K0-K7 SOFTWARE INTEGRATION: VERIFIED. K8 HARDWARE VALIDATION: PENDING.**

`ResilienceRuntime` is wired into the serialized `ObservatoryService` ingress. Raw observations remain unchanged; resilience transitions, interference evidence, and non-KEEP trust decisions are persisted as DERIVED `SYSTEM_EVENT` rows. The runtime is session-scoped and deterministic replay is tested.

Device evidence on SM-A075F / Android 16: JVM regression green; Android instrumentation green (14 tests finished, 2 migration tests skipped); APK SHA-256 `D9E8C5838E3AE27D46706E5083184094B38489B58FB4B18D926615EC039B0B28`; controlled Location-off run produced `FULL_GNSS -> GNSS_DEGRADED -> GNSS_LOST -> INERTIAL_BRIDGING -> MULTI_SOURCE_FUSION -> RECOVERY -> FULL_GNSS`; clean-stop log recorded `Session COMPLETED`; WAL-consistent Room snapshot recorded 18 DERIVED rows in the latest 270-observation completed session.

The run did **not** validate physical interference/jamming/spoofing. Background/screen-off long-run and resource/battery measurements remain K8 follow-up gates.

## K0-K7 RUNTIME HARDENING RECEIPT — 2026-10-06

**IMPLEMENTED / REGRESSION VERIFIED:** fixed three concrete resilience-runtime defects found by source audit: Reception Optimization now derives freshness from measurement age without mutating raw observations; stale measurements are deterministically rejected; explicit recovery classification is evaluated before NORMAL so recovery is reachable; and repeated unchanged GNSS interference state no longer emits duplicate DERIVED resilience events.

**Verification:** `:app:testPrimaryDebugUnitTest :app:assemblePrimaryDebug` BUILD SUCCESSFUL; 162 unit tests, 0 failures, 0 errors, 3 existing reference-vector tests skipped. `:app:connectedPrimaryDebugAndroidTest` BUILD SUCCESSFUL; 14 tests finished on SM-A075F / Android 16, with 2 existing migration tests skipped. APK SHA-256: `2F3C43B74CBFF7D9D79C3415B19E1517E2F8D3ABE59092A9203B89DB797B0AB2`.

**Boundary:** no new physical-interference claim is made. K8 hardware/long-run validation remains pending.
## FINAL K RUNTIME HARDENING RECEIPT — 2026-10-06

Final source correction after review: interference classification ordering now gives strong JAM/SPOOF/DEGRADED evidence precedence over recovery flags, with explicit recovery remaining reachable before NORMAL. Regression coverage was extended for this precedence boundary.

Final receipt: `:app:testPrimaryDebugUnitTest :app:assemblePrimaryDebug` BUILD SUCCESSFUL; 163 unit tests, 0 failures, 0 errors, 3 existing reference-vector skips. APK SHA-256: `4641C4F855C8EB92E769E404171237CFBC11D2F345F611CBBB6DE92E590DBE0C`. `:app:connectedPrimaryDebugAndroidTest` BUILD SUCCESSFUL; 14 tests finished on SM-A075F / Android 16, 2 existing migration tests skipped. No new K8 physical-interference claim is made.
## PROPAGATION + EVIDENCE HARDENING RECEIPT — 2026-10-06

Source-audit fixes completed after real-device and OrbitCore regression:
- OrbitCore adapter now supplies deterministic non-null OMM identity fallbacks for missing object name/ID/classification, matching the library's non-null constructor contract.
- A deep-space GNSS-period regression now exercises OrbitCore at a 12-hour period and verifies `SDP4_DEEP_SPACE` plus finite state.
- Resilient-location altitude is now nullable; absent altitude remains absent instead of being inferred as 0 m.
- Observed vertical uncertainty is no longer copied from horizontal accuracy; the service requires explicit `verticalAccuracyMeters` for publishing a vertical uncertainty.
- Resilience trusted-PVT storage preserves absent altitude and absent vertical accuracy without fabricating measurements.

Verification so far: primary JVM suite `163` tests, `0` failures, `0` errors, `3` existing reference-vector tests skipped; primary APK rebuilt successfully; OrbitCore bridge debug suite `2` tests, `0` failures/errors after adapter hardening.

Reference fixtures `SGP4-VER.TLE` and `tcppver.out` are vendored from the public Kshana validation fixture repository; SHA-256 are recorded in the working tree before commit. Scientific vector comparison against OrbitCore is not yet claimed until a dedicated parser/runner produces an independent receipt.
## 2026-10-06 DEVICE + PROPAGATION HARDENING RECEIPT

**Source hardening:** OrbitCore adapter now guarantees non-null OMM identity fields when upstream object name/ID/classification are absent, using deterministic provenance-preserving fallbacks. `TrustedPvt`, `NavigationEstimate`, and `ResilientLocationSnapshot` now preserve missing altitude and vertical uncertainty as `null`; the service no longer infers altitude `0 m` or copies horizontal accuracy into vertical uncertainty. JSON/provider serialization preserves explicit null altitude.

**Fresh verification:** primary JVM suite `163` tests, `0` failures, `0` errors, `3` existing reference-vector skips; fresh primary APK SHA-256 `9EB1F04505DEA594C4D1789A0BFD6B8CCA51ACFA953E9D8BDC80C3B2D6E2995A`; `:app:connectedPrimaryDebugAndroidTest` BUILD SUCCESSFUL on `SM-A075F / Android 16`, `14` tests finished, `2` existing migration tests skipped; OrbitCore bridge debug suite `2` tests, `0` failures, `0` errors.

**Scientific boundary:** `SGP4-VER.TLE` and `tcppver.out` are now vendored with verified SHA-256 (`D246D1D9D768ACE445A38A965713FA9BA52D80FD8A41A0502FF83D7ACFFE2881`, `687BF28DBE52DF86E8E60AB5CB4A08D1AA3DBCAF4E63B1F7AB95F044FBE3833B`). The files are provenance fixtures only at this receipt. No SGP4/SDP4 numerical accuracy claim is made until Horizon's dedicated vector runner compares OrbitCore states against the fixture rows.

## 2026-10-06 GNSS MEASUREMENT EVIDENCE CLOSURE

**STATUS: VERIFIED / CLOSED**

Fresh device session on Hive / Samsung SM-A075F / Android 16 / API 36 closed the GNSS evidence chain through Observation Bus, Room persistence, readback, export, checksum, and semantic normalization.

Canonical receipt:
docs/verification/device-receipts/HORIZON_MEASUREMENT_ROOM_READBACK_8cf1c36f-8387-4bc9-b850-22d2c754d5e9.md

Evidence:
- Session 8cf1c36f-8387-4bc9-b850-22d2c754d5e9
- 3,250 observations
- sequence 1..3250 contiguous
- ingestion timestamps 3,250/3,250 and non-decreasing
- 25 bounded raw GNSS rows with constellation/SVID/C/N0/SV time/pseudorange-rate/ADR
- explicit JSON nulls preserved for carrierPhase and carrierPhaseUncertainty
- Room readback + ExportEngine + checksum + export readback
- GNSS_SEMANTIC_NORMALIZATION_V1
- normalized_payload_keys_bounded=[10]
- DERIVED classification
- rawPayloadPreserved=true
- direct instrumentation: 1 test / 0 failure / 0 error
- full JVM: 167 tests / 0 failures / 0 errors / 3 pre-existing skips
- export SHA-256: b5697add8563c8380f85f7bc2bc403a5041a2a9f7d3194cc11883b922174e64e

source_timestamp_regressions=150 is retained as a diagnostic observation, not an ingestion-order failure.

Remaining:
- NORAD mapping UNKNOWN; resolver is intentionally empty-table and no mapping is guessed.
- Device-specific UTC edge-case UNVERIFIED.
- K8 physical interference/long-run validation PENDING.
- Orientation-dependent UI remains bounded by target-device sensor availability.

Freeze rule: UI work must not alter propagation, OrbitCore, SGP4/SDP4, or raw-evidence semantics without a new evidence-backed checkpoint.
