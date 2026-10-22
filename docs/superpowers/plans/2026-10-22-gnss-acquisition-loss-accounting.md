# GNSS Acquisition Loss Accounting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make GNSS callback-to-flow admission losses measurable without changing observations or weakening incomplete-session integrity semantics.

**Architecture:** Introduce a small acquisition admission-accounting component that wraps a non-suspending `SendChannel.trySend` operation and exposes immutable counters. The canonical GNSS measurements callback records N expected output rows, one per `eventArgs.measurements` entry (canonical code does not add a clock row here). Single-row status/navigation callbacks and collection-valued antenna paths account for their actual output cardinality. Do not redesign callback buffer capacity, silently drop fewer events, or alter persistence/session state machines in P0.

**Observed implementation state:** source-local counters are in-memory only; the snapshot is not yet persisted or exposed in UI/session audit. Queue/persistence losses remain separate follow-up work.

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
- [x] Full `:app:testPrimaryDebugUnitTest` and `:app:assemblePrimaryDebug` passed in a forced 46-task run; 168 tests, 0 failures/errors, 3 skips.
- [x] Changed files are limited to admission accounting, direct unit tests, roadmap, plan, README and status; original `C:\Projects\Horizon` remains untouched.
- [x] Commit hash recorded: `34f00e6f2b1beb1458aa0e617089cf3e066f0eff`.



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
