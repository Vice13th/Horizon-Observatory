# GNSS Acquisition Loss Accounting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make GNSS callback-to-flow admission losses measurable without changing observations or weakening incomplete-session integrity semantics.

**Architecture:** Introduce a small acquisition admission-accounting component that wraps a non-suspending `SendChannel.trySend` operation and exposes immutable counters. The canonical GNSS measurements callback records N expected output rows, one per `eventArgs.measurements` entry (canonical code does not add a clock row here). Single-row status/navigation callbacks and collection-valued antenna paths account for their actual output cardinality. Do not redesign callback buffer capacity, silently drop fewer events, or alter persistence/session state machines in P0.

**Current implementation state:** live counters are in-memory during collection. The GNSS source starts a fresh diagnostics window per service session, captures a collection-local snapshot during flow cleanup, and places the final snapshot in the existing SESSION_STOPPING system-event details so it is retained in normal session persistence/export. The snapshot is not reconciled with queue/persistence outcomes and is not exposed in UI; abnormal process death may prevent the stop receipt from being written.

**Tech Stack:** Kotlin, Android `callbackFlow`, `kotlinx.coroutines.channels.SendChannel`, JUnit 4, existing Android Gradle flavors.

**Spec:** `docs/research/GNSS_INTERFERENCE_DETECTION_AND_RESILIENT_PNT_ROADMAP_2026-10-22.md`

## Global Constraints

- Preserve `RawObservation` payload, order, timestamp/provenance fields and event structure.
- Never switch `trySend` to a suspending `send` on Android callback threads.
- Do not change `INTEGRITY_FAILED`/incomplete-session logic.
- Count only callbacks Horizon receives; never claim Android/hardware generated callbacks not delivered to the app.
- Keep per-source/per-observation accounting distinct from session-wide queue-drain row totals.
- No RF transmission or jamming activity.

## Review Focus

- A full bounded flow buffer rejects a row and must increment capacity-rejected, not closed.
- A closed channel rejects a row and must increment closed-rejected.
- A construction exception does not increment send attempts but must increment construction failures.
- A measurements callback with zero N measurements records a received callback and expected cardinality 0; canonical code produces no measurement rows for an empty batch.
- Missing/invalid measurement content must not be rewritten to fabricated values; accounting does not touch timestamps/payload.

---

### Task 1: Admission-accounting primitive

**Files:**
- Create: `app/src/main/kotlin/horizon/observatory/acquisition/base/AcquisitionDiagnostics.kt`
- Create: `app/src/test/kotlin/horizon/observatory/acquisition/base/AcquisitionDiagnosticsTest.kt`

**Interfaces:**
- Consumes: `SendChannel<RawObservation>` plus a lazy `() -> RawObservation` constructor.
- Produces: `AcquisitionDiagnostics.offer(...)`, `callbackStarted(expectedObservations)`, `callbackFinished(startNanos)`, `recordSingleCallback(...)`, and immutable `AcquisitionDiagnosticsSnapshot`.

- [x] **Step 1: Write failing unit tests** covering accepted sends, capacity rejection, channel closure, and constructor failure. The initial run failed at compile-time because the production class was absent (expected RED).
- [x] **Step 2: Run focused test** and verify the missing production class caused the failure.
- [x] **Step 3: Implement non-blocking counters** with atomics/ConcurrentHashMap and `trySend`; group counts by observation type. Counters remain in memory only.
- [x] **Step 4: Re-run focused test**. Five tests passed (including mixed fan-out).
- [x] **Step 5: Commit** after full review — committed as `34f00e6f2b1beb1458aa0e617089cf3e066f0eff`.

### Task 2: Integrate GNSS callbacks without changing payloads

**Files:**
- Modify: `app/src/main/kotlin/horizon/observatory/acquisition/gnss/GnssObservationSource.kt`
- Create: `app/src/test/kotlin/horizon/observatory/acquisition/gnss/GnssAdmissionAccountingTest.kt`

**Interfaces:**
- Consumes: Task 1's `AcquisitionDiagnostics` and existing `RawObservation` constructors.
- Produces: a `GnssObservationSource`-owned diagnostics snapshot for callback-flow admission accounting. Counters match current canonical fan-out; no clock row is added to the measurement callback.

- [x] **Step 1: Test primitive fan-out** for expected/constructed/attempted/accepted/rejected counters and a bounded-channel batch. Actual Android callbacks are not mocked by this test.
- [x] **Step 2: Run full suite** and verify failures are not due to source edits.
- [x] **Step 3: Route existing GNSS sends through the helper** for measurements, status, navigation and antenna outputs. `trySend` stays non-blocking. Payloads/fields were inspected and preserved.
- [x] **Step 4: Run the full unit suite and APK assemble** `:app:testPrimaryDebugUnitTest :app:assemblePrimaryDebug --rerun-tasks`.
- [x] **Step 5: Review source diff and payload construction.** Follow-up review moved status and navigation payload serialization inside the diagnostics callback wrapper; see the dated review receipt below.

**Coverage limit:** a dedicated Android callback integration test was not added because system callback registration requires a different test seam. The shared admission component has direct unit coverage; all current source paths were inspected and the full unit/build tasks pass.

### Task 3: Record implementation evidence in docs

**Files:**
- Modify: `ENGINEERING_STATUS.md`
- Modify: `README.md`
- Modify: `docs/VERIFICATION_MATRIX_CURRENT.md` only if a new test/build receipt exists.

- [x] Record exact branch/test counts/skips and keep detector performance claims out of this patch.
- [x] Include the separate follow-up gate for API-33 event-level AGC and empty-collection/legacy compatibility semantics verified against official Android docs.

## Completion gate

- [x] `git diff --check` passes.
- [x] Focused primitive tests observed failing before the implementation and passing after.
- [x] Latest forced multi-flavor Gradle run passed (152 actionable tasks); primary unit report: 170 tests, 0 failures, 0 errors, 3 existing skips. T1 unit tests, OrbitCore bridge unit tests, and primary/T1/TS debug assembly also passed.
- [x] Changed files cover admission accounting, GNSS source/session-stop integration, direct unit tests, roadmap, plan, README and status; original `C:\Projects\Horizon` remains untouched.
- [x] Initial primitive commit recorded: `34f00e6f2b1beb1458aa0e617089cf3e066f0eff`; subsequent follow-up commit hashes are recorded in the dated receipts below.



---

## Follow-up review receipt — 2026-10-22

### Finding and change
- **OBSERVED:** onSatelliteStatusChanged built the per-satellite JSON array/object before entering recordSingleCallback; a runtime exception in a GNSS status accessor or JSON payload construction would bypass callback and construction-failure accounting.
- **OBSERVED:** onGnssNavigationMessageReceived similarly built the JSON payload and Base64 data before entering the diagnostics wrapper.
- **CHANGE:** moved both payload construction paths inside the recordSingleCallback block. This keeps payload construction/serialization within the existing try/catch(Exception) in offer, so expected construction exceptions increment constructionFailures, preserve expectedObservations=1, do not increment constructed/send-attempt counters, and still run callbackFinished in finally.
- **CHANGE:** added singleCallbackConstructionFailureIsCountedAndCallbackFinished, asserting callback count, in-flight cleanup, expected cardinality, no constructed/send-attempt row, and construction-failure type.
- **BOUNDARY:** this is a unit test of the shared diagnostics wrapper, not an Android framework callback integration test. The status/nav production callback accessors are not mocked end-to-end.

### Verification evidence
- **MEASURED:** :app:testPrimaryDebugUnitTest :app:assemblePrimaryDebug --rerun-tasks — BUILD SUCCESSFUL, 46 actionable tasks executed.
- **MEASURED:** JUnit XML for AcquisitionDiagnosticsTest reports 6 tests, 0 failures, 0 errors, 0 skipped; full report records 169 tests, 0 failures, 0 errors, 3 existing skips.
- **MEASURED:** rebuilt local debug APK SHA-256: 61879CA6F44CE965ABFF202B810016BB04381DDBE0C92B5E6C66D81100ED0732. Not installed, not device-tested, not a release artifact.
- **MEASURED:** git diff --check returned no whitespace errors. Git emitted only a working-tree LF-to-CRLF conversion warning for the edited test file.
- **OBSERVED:** build still emits the existing Gradle 8.11.1 compatibility warning and deprecated Android GNSS measurement property warnings.
- **UNVERIFIED:** current GitHub Actions result for the updated commit; physical device/session behavior; source-specific callback integration; persistence/session export of these counters; repeated callbackFlow collection lifecycle behavior.
- **STOP CONDITION:** do not claim GNSS interference detection, receiver-level completeness, persisted diagnostic reconciliation, or final field validation from this patch.

- **MEASURED — cross-flavor regression:** `:app:testT1DebugUnitTest :propagation-contract:test :orbitcore-bridge:testDebugUnitTest :app:assembleT1Debug --rerun-tasks` completed `BUILD SUCCESSFUL` (70 actionable tasks executed). `:propagation-contract:test` has no test sources in this checkout; OrbitCore bridge debug unit tests executed successfully.
- **MEASURED — third app flavor:** `:app:assembleTsDebug` completed `BUILD SUCCESSFUL` (40 actionable tasks; 38 executed, 2 up-to-date).
- **Boundary:** these are local build/unit-test receipts, not Android instrumentation, install, launch, receiver behavior, or field validation.


---

## Session-scoped diagnostic receipt — 2026-10-22

### Implementation
- **CHANGE:** added beginSession(sessionId) to reset the GNSS accounting window at session start. Each callbackFlow collection captures its own diagnostics object, so a later session cannot reset counters that callbacks from an earlier collection still reference.
- **CHANGE:** flow cleanup captures the final collection-local snapshot after unregister attempts. A delayed cleanup writes the snapshot only if its diagnostics object is still the source's current session instance, preventing an older collection from overwriting a newer session snapshot.
- **CHANGE:** the existing SESSION_STOPPING system event now stores a gnssAcquisitionDiagnostics object containing session ID, callback/in-flight counts, expected/constructed/attempted/admitted/rejected counts, construction failures, callback durations, last failure when present, and per-observation-type counters. Because this uses the existing stop event, no extra sequence row is introduced; normal session persistence/export includes the receipt.
- **CHANGE:** AcquisitionDiagnosticsSnapshot.toJsonObject has a unit test asserting session identity and per-type admission counters survive serialization.

### Verification
- **MEASURED:** forced command :app:testPrimaryDebugUnitTest :app:assemblePrimaryDebug :app:testT1DebugUnitTest :app:assembleT1Debug :app:assembleTsDebug :propagation-contract:test :orbitcore-bridge:testDebugUnitTest --rerun-tasks completed BUILD SUCCESSFUL; 152 actionable tasks executed.
- **MEASURED:** primary JUnit HTML summary reports 170 tests, 0 failures, 3 ignored/existing skips; focused AcquisitionDiagnosticsTest XML reports 7 tests, 0 failures, 0 errors, 0 skipped.
- **MEASURED:** git diff --check returned no whitespace errors; only LF-to-CRLF working-tree warnings appeared.
- **MEASURED:** primary debug APK SHA-256: B45D3A6DC0274A2FFAE18C8E5F57C6E2110D73C306BEEC6CD9ACCCE2CA034CBF. It was not installed or device-tested and is not a release artifact.
- **OBSERVED:** Gradle 8.11.1 compatibility and deprecated Android GNSS API warnings remain.

### Boundaries and open gates
- **VERIFIED by code path:** the final counters are embedded in SESSION_STOPPING details, which are persisted as a system-event observation and included in the existing observation export path. No separate database schema or new sequence row was introduced.
- **UNVERIFIED:** normal physical-device stop/readback/export of this payload; abnormal process death before SESSION_STOPPING; late callbacks still in flight at snapshot time; repeated-flow lifecycle under real Android callback scheduling; source accepted-send vs queue/persistence/export reconciliation; UI exposure.
- Counters describe callbacks delivered to Horizon only. They do not estimate receiver/Android events never delivered, do not constitute persistence acknowledgements, and do not change fail-closed integrity semantics.
- Final device testing remains with the user; no installation, device test, RF activity, merge, or release occurred.
