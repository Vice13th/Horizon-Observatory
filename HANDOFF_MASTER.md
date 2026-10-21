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
- **This handoff’s intended repository path:** https://github.com/Vice13th/Horizon-Observatory/blob/main/HANDOFF_MASTER.md. It returned 404 before creation was attempted; verify after commit.

## Current repository observations
- **VERIFIED:** GitHub connector is authenticated as the repository owner; `Vice13th/Horizon-Observatory` is public and `main` is the default branch.
- **VERIFIED:** `README.md` on `main` was fetched successfully. It records the 2026-10-14 debug-signed pre-release APK, SHA-256 `327756805061763A2CD691E1F4D46ACDE1DE352E26A7E9594A46DFE779F72D33`, and says it is not a production release.
- **VERIFIED:** `ENGINEERING_STATUS.md` on `main` was fetched successfully. Overall project verdict is PARTIAL; NORAD identity resolution, UTC edge cases, physical orientation, independent OrbitCore reference-vector accuracy, and K8 physical/long-run validation remain open.
- **VERIFIED:** `main/HANDOFF_MASTER.md` returned GitHub API 404 before the create-file call.
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
- **VERIFIED:** Notion connection reaches workspace “A’s Space”; workspace-user listing returned the connected person and Notion MCP bot only. This is not a project contact roster.
- **UNVERIFIED:** designated reviewer, project contact list, CODEOWNERS path, and direct contact details. PR #7 and #8 metadata show no requested reviewers. Do not invent contacts.
- GitHub, Notion search/fetch, and Open Context personal-library search succeeded in this run. A registered tool is not proof that every provider is authenticated, within quota, or works for each task.
- Prior handoff notes say Consensus was used for the roadmap; Elicit previously returned API access denied. Retest before relying on either. BetterContext, Zotero, and Superpowers callable access remained unverified in earlier tool inspection. Open Context is a different product from BetterContext.
- Prior context results include “Horizon Observatory State Reconciliation Handoff”, “Horizon Observatory SGP4 Runtime Verification Handoff — 2026-10-06”, “Horizon Observatory GNSS Foundation Verified Handoff — 2026-10-06”, and “Horizon Observatory GNSS Measurement Validation Gate — 2026-10-06”.

## Immediate next actions
1. Re-fetch this file from GitHub `main` after the attempted write and verify that the path resolves.
2. Inspect actual patches, checks, and CI for PR #7 and #8; review before any merge.
3. Obtain explicit authorization before merging; do not infer approval from tool approval or the instruction to start.
4. After P0 code/test review, implement P1 in a separate branch with tests for API capability, event-level AGC, missingness semantics, and API 26–32 guards.
5. Inspect repository collaborators, reviewer metadata and CODEOWNERS locations to build a verified contact roster.
6. Update the Notion Field Test Handoff only after preserving its full existing contents and confirming the page-update contract; append changes rather than overwriting historical evidence.
7. Preserve `C:\Projects\Horizon` unchanged. Keep private databases/WAL/SHM, precise location traces, and raw session exports out of public docs and research uploads.

## Evidence conventions
Use **VERIFIED** only for facts freshly checked against the named source. Use **OBSERVED** for tool-returned state not independently cross-checked. Use **MEASURED** only for tests/metrics actually executed and captured. Mark **INFERRED**, **ASSUMED**, or **UNVERIFIED** explicitly. Retain historical receipts with their original provenance.