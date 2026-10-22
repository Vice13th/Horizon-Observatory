# Horizon Observatory — Master Handoff
**Updated:** 2026-10-22  
**Status:** PARTIAL — repository access verified; documentation updates are being recorded with source provenance.

## Canonical workspace and source links
- **GitHub repository:** https://github.com/Vice13th/Horizon-Observatory
- **Default branch:** `main`
- **Remote Windows working checkout:** `C:\Projects\Horizon-Observatory-work`
- **Original field source snapshot:** `C:\Projects\Horizon` — unversioned source copy; preserve unchanged, do not commit/push from it.
- **Notion field handoff:** https://app.notion.com/p/4009ccb5738f8181a04fde6ae49be3b4
- **Notion release-readiness audit:** https://app.notion.com/p/4009ccb5738f818da766c354bbc1328c
- **Notion multi-GNSS/session-export repair:** https://app.notion.com/p/4009ccb5738f8106a0e0e3dcae14c395
- **This handoff's intended repository path:** https://github.com/Vice13th/Horizon-Observatory/blob/main/HANDOFF_MASTER.md

## Current repository observations
- **VERIFIED:** GitHub connector is authenticated as the repository owner; `Vice13th/Horizon-Observatory` is public and `main` is the default branch.
- **VERIFIED:** `README.md` on `main` was fetched successfully. It records the 2026-10-14 debug-signed pre-release APK, SHA-256 `327756805061763A2CD691E1F4D46ACDE1DE352E26A7E9594A46DFE779F72D33`, and says it is not a production release.
- **VERIFIED:** `ENGINEERING_STATUS.md` on `main` was fetched successfully. Overall project verdict is PARTIAL; NORAD identity resolution, UTC edge cases, physical orientation, independent OrbitCore reference-vector accuracy, and K8 physical/long-run validation remain open.
- **VERIFIED:** `HANDOFF_MASTER.md` was created on `main` and re-fetched successfully; blob SHA `f9e204f696ab3b756ce943cea40885344340055b`.
- **OBSERVED:** Current GNSS branches include `docs/gnss-interference-resilience-roadmap-2026-10-22`, `feat/gnss-acquisition-loss-accounting-2026-10-22`, and historical `roadmap/gnss-resilience-2026-10-06`.

## Current pull requests
- **PR #7 — GNSS interference/resilient PNT roadmap:** https://github.com/Vice13th/Horizon-Observatory/pull/7
  - **VERIFIED:** open, draft, unmerged, reported mergeable. Base `main`; head `docs/gnss-interference-resilience-roadmap-2026-10-22`; head SHA `5834bc758a4faf039633a23a5f6da1052e6b0e67`; 3 files changed, +221 lines.
  - Proposal only; it does not implement or validate a detector.
- **PR #8 — GNSS acquisition admission-loss accounting:** https://github.com/Vice13th/Horizon-Observatory/pull/8
  - **VERIFIED:** open, draft, unmerged, reported mergeable. Base `main`; head `feat/gnss-acquisition-loss-accounting-2026-10-22`; head SHA `2721d88822e52ed5f347d8317928e708e5d65d08`; 7 files changed, +499/-28 lines.
  - PR description reports forced `:app:testPrimaryDebugUnitTest :app:assemblePrimaryDebug --rerun-tasks` success; 168 tests, 0 failures/errors, 3 pre-existing skips; `git diff --check` passed; APK SHA-256 `30F0635FCBA6DE6AFF3CC91F056D4472C2B1139B5F5D6A67BAEEC1DABB3D3FAB`.
  - **Limit:** test values are recorded in PR metadata, not independently rerun during this connector inspection. Counters remain source-local/in-memory and are not reconciled with persistence or included in exports/UI.
- No PR was merged in this run. Beginning work is not authorization to merge or destructively change source state.

## Field candidate evidence (Notion-sourced, not remeasured here)
The Notion Field Test Handoff records a candidate at `C:\Projects\Horizon\artifacts\Horizon-Parallel-FieldFix-Candidate-20261022.apk`.
- Recorded size: 11,327,244 bytes.
- Recorded SHA-256: `E6EFA09939F6B87A8775B805556EA1A79551674E093557DABEEFF4BD2E0CA6C2`.
- Recorded test result: `:app:testParallelDebugUnitTest` 443 tests, 0 failures/errors, 3 skipped; `:app:assembleParallelDebug` passed.
- Recorded install: `adb install -r` succeeded without uninstall/data clear.
- **Boundary:** debug field-test candidate, not production-ready. These are prior handoff receipts and were not freshly remeasured here.

## Research roadmap and technical boundaries
1. **P0 — acquisition admission-loss accounting:** track source callbacks, expected fan-out, accepted sends, capacity rejection, closed-channel rejection, construction failures, and persistence outcomes where possible. Preserve fail-closed incomplete-session semantics.
2. **P1 — API-aware AGC:** use Android API 33+ event-level `GnssMeasurementsEvent.getGnssAutomaticGainControls()` where available; retain API 26–32 guards/fallbacks and explicit unsupported/empty/unknown semantics.
3. **P2 — explainable multi-indicator anomaly state machine:** distinguish `INTERFERENCE_SUSPECTED`, `SPOOFING_SUSPECTED`, `RECEIVER_OR_PIPELINE_DEGRADED`, `INSUFFICIENT_DATA`, and `PNT_UNAVAILABLE`; validate with independent ground truth.
4. **P3 — resilient PNT fallback:** preserve observed/derived/predicted/dead-reckoned provenance, uncertainty, freshness, and stale-fix expiry.
5. **P4 — controlled validation:** device/session-separated tests, independent GNSS ground truth, authorized shielded RF lab work only, long-run/resource tests, and confidence intervals.
6. Software cannot guarantee persistent satellite lock under effective RF interference. Do not build transmitters/jammers or conduct over-the-air interference testing.

## Open release/scientific gates
- NORAD mapping remains UNKNOWN until authoritative, time-valid mapping evidence is available.
- Device-specific UTC edge cases remain UNVERIFIED.
- Physical orientation/panel rotation remains UNVERIFIED/BLOCKED for currently available target sensor evidence.
- Scientific OrbitCore SGP4/SDP4 reference-vector accuracy remains UNVERIFIED.
- K8 physical interference and long-duration resource/battery validation remains PENDING.
- Connected instrumentation/Room migration coverage, API 26–28 compatibility, reproducible production build/signing, SBOM/license/CVE/security review, and clean-machine verification remain gates.
- Local source-copy notes identify meaningful divergence from canonical `main`; do not transplant a local acquisition source file wholesale. Use a fresh canonical checkout and test-first changes.

## Contacts and access
- **VERIFIED:** connected GitHub identity is `Vice13th`; repository permissions include admin, maintain, push and pull.
- **VERIFIED:** Notion connection reaches workspace “A's Space”; workspace-user listing returned the connected person and Notion MCP bot only. This is not a project contact roster.
- **UNVERIFIED:** designated reviewer, project contact list, CODEOWNERS path, and direct contact details. PR #7 and #8 metadata show no requested reviewers. Do not invent contacts.
- GitHub, Notion search/fetch, and Open Context personal-library search succeeded in this run. A registered tool is not proof that every provider is authenticated, within quota, or works for each task.
- Prior handoff notes say Consensus was used for the roadmap; Elicit previously returned API access denied. Retest before relying on them. BetterContext, Zotero, and Superpowers callable access remained unverified in earlier tool inspection. Open Context is a different product from BetterContext.
- Prior context results include “Horizon Observatory State Reconciliation Handoff”, “Horizon Observatory SGP4 Runtime Verification Handoff — 2026-10-06”, “Horizon Observatory GNSS Foundation Verified Handoff — 2026-10-06”, and “Horizon Observatory GNSS Measurement Validation Gate — 2026-10-06”.

## Immediate next actions
1. Review actual PR #7 and #8 patches; check concurrency/correctness, callback-accounting semantics, and research-source provenance.
2. Inspect CI and branch protection. Empty status/workflow results do not prove no required checks exist.
3. Obtain explicit authorization before merge; tool approval is not merge authorization.
4. After P0 code/test review, implement P1 in a separate branch with tests for API capability, event-level AGC, missingness semantics, and API 26–32 guards.
5. Inspect repository collaborators, reviewer metadata and CODEOWNERS locations to build a verified contact roster.
6. Existing Notion Field Test Handoff remains unchanged; append dated observations if future updates are needed, rather than overwriting historical evidence.
7. Preserve `C:\Projects\Horizon` unchanged. Keep private databases/WAL/SHM, precise location traces, and raw session exports out of public docs and research uploads.

## Evidence conventions
Use **VERIFIED** only for facts freshly checked against the named source. Use **OBSERVED** for tool-returned state not independently cross-checked. Use **MEASURED** only for tests/metrics actually executed and captured. Mark **INFERRED**, **ASSUMED**, or **UNVERIFIED** explicitly. Retain historical receipts with their original provenance.

## Review checkpoint — 2026-10-22 (fresh remote inspection)

### PR #8 source review
- **VERIFIED:** Re-fetched the PR head source files directly from branch `feat/gnss-acquisition-loss-accounting-2026-10-22`. `AcquisitionDiagnostics.kt` blob SHA: `c75d2ed8bffed4e753d246cc78fcd680f28478f1`; `GnssObservationSource.kt` blob SHA: `36ecbdf3de8e48fd7f957885abebe1ed1195d577`; focused test blob SHA: `aaac527c3296e75bf8aad57d1154cf8b614587f4`.
- **OBSERVED:** The five focused tests assert accepted sends, capacity rejection, closed-channel rejection, constructor failure, and batch fan-out. They do not exercise the production GNSS status callback's payload-building path or assert status/nav callback exceptions and registration lifecycle diagnostics.
- **OBSERVED — review concern:** In `onSatelliteStatusChanged`, elapsed-time and satellite `JSONArray/JSONObject` payload construction occurs before `diagnostics.recordSingleCallback(...)`; if that construction throws, no callback/start/failure counter records it. The payload path is therefore outside the diagnostics boundary.
- **OBSERVED — metric semantics:** measurement batches increment expected rows by `eventArgs.measurements.size`; antenna batches by list size; status and navigation callbacks each call `recordSingleCallback` (expected one). There is no direct count of per-family callback events, and no counters for callback payload failures before `offer`.
- **OBSERVED — lifecycle concern to test:** `GnssObservationSource` owns a single diagnostics object for the source instance and its default session ID is `UNASSIGNED`. Snapshots therefore accumulate across repeated flow collections and are not intrinsically tied to a particular persisted session unless callers supply/associate additional context (the inspected constructor currently does not).
- **OBSERVED — exception boundary:** `offer` catches `Exception` from observation construction but does not catch failures from `channel.trySend` itself. This avoids broad Throwable swallowing, but expected exception/cancellation behavior should be explicitly tested/documented.
- **VERIFIED from PR metadata:** PR #8 body claims 168 unit tests passed and a debug APK hash, but this session did not run Gradle locally; retain as author-reported evidence, not freshly MEASURED.

### CI and merge gates
- **VERIFIED:** PR #8 head SHA `2721d88822e52ed5f347d8317928e708e5d65d08` has zero GitHub Actions runs returned for its exact head SHA; check-runs endpoint reports `total_count: 0`; combined status state is `pending` with zero statuses. PR #7 head SHA `5834bc758a4faf039633a23a5f6da1052e6b0e67` has zero returned workflow runs.
- **VERIFIED:** The reachable `android-ci.yml` file on branch `ci/android-minimal-20261012` includes PR triggers but only for changes to `app/**`, `orbitcore-bridge/**`, `propagation-contract/**`, Gradle/build files, or the workflow itself. PR #7 touches docs/README/status only, so it does not match that path filter. PR #8 does change `app/**`, so no run remains unexplained.
- **OBSERVED:** Workflow listing returns one historical failed run for PR #5 on 2026-10-12, not for #7/#8.
- **VERIFIED:** Public `/rulesets` endpoint returned `[]`. **UNVERIFIED:** branch-protection endpoint was denied with HTTP 403; absence of required checks cannot be concluded.
- **UNVERIFIED:** Fetching `.github/workflows/android-ci.yml` from `main` returned 404 while fetching it at `ci/android-minimal-20261012` succeeded. Reconcile whether the workflow is absent from main or the connector's contents route is inconsistent before treating it as canonical CI configuration.

### Research traceability review
- **VERIFIED:** Consensus full records were fetched for:
  - Spens et al., “Detecting GNSS Jamming and Spoofing on Android Devices” (2022), *NAVIGATION: Journal of the Institute of Navigation*, DOI `10.33012/navi.537`; the paper describes Android-native comparison methods including AGC and C/N0, network-vs-GNSS location and GNSS-vs-system time.
  - Rustamov, Minetto & Dovis, “Improving GNSS Spoofing Awareness in Smartphones via Statistical Processing of Raw Measurements” (2023), *IEEE Open Journal of the Communications Society* 4:873–891; the record supports a separate spoofing-consistency path based on cross-correlation in satellite C/N0 time series.
  - Tegedor et al., “Monitoring Radio Frequency Interference Affecting GNSS Using Android Smartphones” (2026 record / European Navigation Conference 2025); the record explicitly warns that small AGC variations may not indicate interference and smartphone models differ.
- **VERIFIED:** Official Android API reference for `GnssMeasurementsEvent.getGnssAutomaticGainControls()` says the API reports a collection, reports it when measurement engine runs (even before satellite acquisition on supporting implementations), and returns an empty collection on unsupported devices. The `GnssAutomaticGainControl` reference says AGC behavior depends on hardware design and that changes are more meaningful within a given hardware design than global absolute values.
- **UNVERIFIED:** The linked 2026 conference paper record was found at Consensus, but a primary proceedings/full-text source, exact final publication metadata, and peer-review/version status were not independently checked in this pass.
- **VERIFIED / boundary:** Roadmap proposals and target metrics remain proposed targets, not achieved Horizon results; no detector or physical interference test is claimed.

### Research tool access observed
- **OBSERVED:** Exa returned useful official Android API content. SciSpace returned relevant paper metadata. Consensus search plus fetch succeeded for three records. Acumen request failed internally. Elicit and Sider results were included in a multi-call response but output truncation prevented reliable per-provider auditing; do not label their full workflow validated.
- No edits to either PR branch, no comments or reviews posted, no merge, no Gradle run, and no device operation were performed in this checkpoint.


## Development continuation checkpoint — 2026-10-22 (PR #8 follow-up)

### Source change
- **VERIFIED:** commit `51e3db5420b3f5cef206ece936292b21d3282942` was pushed to `feat/gnss-acquisition-loss-accounting-2026-10-22`; PR #8 head now points to this commit.
- **CHANGE:** GNSS satellite-status and navigation-message payload construction now occurs inside `AcquisitionDiagnostics.recordSingleCallback`, ensuring exceptions during payload construction/serialization are included in the existing construction-failure accounting boundary.
- **CHANGE:** added `singleCallbackConstructionFailureIsCountedAndCallbackFinished` to verify expected cardinality, failure classification, no false constructed/send-attempt counts, and cleanup of the in-flight callback counter.
- Raw observation fields, timestamps, provenance, callback fan-out, non-blocking `trySend`, and fail-closed session-integrity semantics were not intentionally changed.

### Fresh local verification (Hive / Windows canonical checkout)
- **MEASURED:** `:app:testPrimaryDebugUnitTest :app:assemblePrimaryDebug --rerun-tasks` — BUILD SUCCESSFUL, 46 tasks; 169 tests, 0 failures, 0 errors, 3 existing skips.
- **MEASURED:** `:app:testT1DebugUnitTest :propagation-contract:test :orbitcore-bridge:testDebugUnitTest :app:assembleT1Debug --rerun-tasks` — BUILD SUCCESSFUL, 70 actionable tasks. `:propagation-contract:test` has no test sources in this checkout.
- **MEASURED:** `:app:assembleTsDebug` — BUILD SUCCESSFUL, 40 actionable tasks (38 executed, 2 up-to-date).
- **MEASURED:** `git diff --check` passed before commit. Primary debug APK SHA-256: `61879CA6F44CE965ABFF202B810016BB04381DDBE0C92B5E6C66D81100ED0732`.
- **Boundary:** local APK was not installed or device-tested; it is not a release artifact. Gradle 8.11.1 compatibility and deprecated Android GNSS measurement API warnings remain.

### PR and CI state after push
- **VERIFIED:** PR #8 remains open/draft/unmerged and is currently reported mergeable. Latest head SHA: `51e3db5420b3f5cef206ece936292b21d3282942`.
- **OBSERVED:** exact-head Actions workflow list and combined commit statuses returned empty. This is not a green CI receipt; remote CI is still unverified.
- **VERIFIED:** PR #8 body now includes the new finding, fix commit, test totals, cross-flavor results, and artifact boundary.
- No merge, review submission, device install, or RF operation occurred.

### Remaining P0/P1 gates
1. Add a test seam for actual Android status/navigation callback payload conversion and exception behavior.
2. Decide and test diagnostics lifecycle/session association across repeated flow collections; current counters are source-local/in-memory and default session ID is `UNASSIGNED`.
3. Add reconciliation with queue/persistence outcomes before claiming end-to-end admission completeness.
4. Confirm canonical CI workflow/branch protection; no remote check run is currently verified.
5. Keep API-33 event-level AGC, missingness semantics, multi-indicator state machine, PNT fallback, and independent field ground truth as separate future milestones.
6. User conducts final device testing; no field-test or release-ready claim is made.

This dated section supersedes the earlier “no Gradle run / no PR edits” statement for this continuation only; that earlier statement remains a correct receipt for its original checkpoint.


## Session-scoped acquisition diagnostics — 2026-10-22

### Change and verification
- **VERIFIED:** PR #8 feature branch commit `d30a44b4a1617ebcfbfc80c07cbde631a976c42d` adds session-scoped GNSS acquisition diagnostics and stores the final snapshot in the existing `SESSION_STOPPING` system-event details.
- **CHANGE:** `GnssObservationSource.beginSession(sessionId)` resets the diagnostics window per service session. Each cold `callbackFlow` collection captures its own diagnostics instance; delayed cleanup only publishes a final snapshot if that instance is still current, avoiding overwriting a newer session's snapshot.
- **CHANGE:** `AcquisitionDiagnosticsSnapshot.toJsonObject()` serializes callback/in-flight counts, expected/constructed/send/admission/rejection/failure counters, callback durations, optional last failure, and per-observation-type counts. The existing stop event is reused, so no extra sequence number/row is allocated for diagnostics.
- **CHANGE:** added a serialization unit test and persisted the session-tagged receipt through the existing observation/session export path. This is not a queue/persistence acknowledgement and does not replace fail-closed integrity checks.

### Fresh local verification
- **MEASURED:** forced multi-flavor Gradle command `:app:testPrimaryDebugUnitTest :app:assemblePrimaryDebug :app:testT1DebugUnitTest :app:assembleT1Debug :app:assembleTsDebug :propagation-contract:test :orbitcore-bridge:testDebugUnitTest --rerun-tasks` completed `BUILD SUCCESSFUL`; 152 actionable tasks executed.
- **MEASURED:** primary JUnit report: 170 tests, 0 failures, 0 errors, 3 existing skips. Focused `AcquisitionDiagnosticsTest`: 7 tests, 0 failures/errors/skips.
- **MEASURED:** `git diff --check` passed. Primary debug APK SHA-256: `B45D3A6DC0274A2FFAE18C8E5F57C6E2110D73C306BEEC6CD9ACCCE2CA034CBF`. Not installed/device-tested; not a release artifact.
- **OBSERVED:** Gradle 8.11.1 compatibility and deprecated Android GNSS API warnings remain. `:propagation-contract:test` has no test sources in this checkout.

### State and remaining gates
- **VERIFIED:** PR #8 remains open, draft, unmerged, and reported mergeable; current implementation commit is `d30a44b4a1617ebcfbfc80c07cbde631a976c42d`. The branch also contains a merge from current `main` to keep the handoff history aligned.
- **OBSERVED:** exact-head GitHub Actions runs and combined statuses remain empty. This is not a green remote-CI receipt.
- **UNVERIFIED:** actual Android callback integration and late-callback behavior at stop; physical-device stop/readback/export; abnormal process death before `SESSION_STOPPING`; accepted-send versus queue/database/export reconciliation; UI exposure.
- The final snapshot is written only through the normal stop event. It is not guaranteed to survive process death before that event and may report nonzero callbacks-in-flight if platform callbacks are still running during cleanup.
- Next P0 gate: add an injectable seam for actual status/navigation callback conversion and lifecycle testing; then reconcile source admission counters with persistence outcomes. P1 event-level AGC remains a separate milestone with explicit unsupported/empty/missingness semantics and API 26–32 guards.
- User will conduct final device testing. No install, device test, RF activity, merge, or release occurred.
