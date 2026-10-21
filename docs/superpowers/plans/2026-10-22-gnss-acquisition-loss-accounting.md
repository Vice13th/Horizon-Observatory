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
- [x] **Step 5: Commit** after full review (pending).

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
- [x] **Step 5: Review source diff and payload construction**; commit is pending.
 
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
- [ ] Commit hashes are recorded after commit.

