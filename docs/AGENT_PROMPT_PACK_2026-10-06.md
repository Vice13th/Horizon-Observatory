# HORIZON — AGENT PROMPT PACK / 2026-10-06

Copy/paste this block into the next engineering agent session.

---

You are continuing Vice13th/Horizon-Observatory from the canonical checkpoint:

HORIZON_CHECKPOINT_2026-10-06_GNSS_FOUNDATION_VERIFIED

## BOOTSTRAP

Read, in order:
1. AGENTS.md
2. docs/CHECKPOINT_2026-10-06_GNSS_FOUNDATION_VERIFIED.md
3. docs/HANDOFF_2026-10-06_GNSS_FOUNDATION.md
4. docs/verification/device-receipts/HORIZON_MEASUREMENT_ROOM_READBACK_8cf1c36f-8387-4bc9-b850-22d2c754d5e9.md
5. docs/HORIZON_MASTER_ROADMAP.md
6. docs/VERIFICATION_MATRIX.md

Do not search chat history for missing repository facts. Inspect the repository and the actual local worktree.

## CURRENT TRUTH

The following are VERIFIED:
- fresh SM-A075F / Android 16 / API 36 GNSS acquisition;
- session 8cf1c36f-8387-4bc9-b850-22d2c754d5e9;
- 3,250 observations;
- contiguous sequence 1..3250;
- 3,250/3,250 ingestion timestamps, non-decreasing;
- 25 bounded raw GNSS rows with constellation/SVID/C/N0/SV time/pseudorange-rate/ADR;
- explicit JSON nulls for carrier phase fields;
- Room readback;
- export/readback/checksum;
- GNSS_SEMANTIC_NORMALIZATION_V1;
- 10 bounded normalized payload keys;
- DERIVED classification;
- rawPayloadPreserved=true;
- 1 direct instrumentation test with 0 failure/error;
- 167 JVM tests with 0 failures/errors and 3 pre-existing skips;
- export SHA-256 b5697add8563c8380f85f7bc2bc403a5041a2a9f7d3194cc11883b922174e64e.

## CURRENT UNKNOWN / UNVERIFIED

- NORAD mapping = UNKNOWN; resolver is empty-table; never guess.
- device-specific UTC edge case = UNVERIFIED.
- K8 physical interference / long-run validation = PENDING.
- target orientation behavior may be hardware-limited.

source_timestamp_regressions=150 is a diagnostic observation, not an ingestion-order failure.

## FROZEN

Do not change:
- OrbitCore / SGP4 / SDP4 / propagation contract;
- raw evidence semantics;
- Observation Bus / Room / export semantics;
unless a fresh evidence-backed defect explicitly requires it.

## ACTIVE MISSION

Current active lane = UI/UX modernization only.

The scientific core is frozen. Improve presentation, not the measurement truth.

Priority:
1. inspect local UI state;
2. reproduce current UI behavior;
3. identify the canonical state/projection seam;
4. modernize satellite panels / skyplot / typography / hierarchy / responsiveness;
5. measure runtime cost;
6. run focused tests;
7. build;
8. real-device verify when available;
9. update receipt/docs;
10. commit/push.

## ENGINEERING CONTRACT

Use:
INSPECT → RECONCILE → PLAN → IMPLEMENT → TEST → BUILD → VERIFY → DOCUMENT → NEXT GATE

Rules:
- correctness > speed;
- evidence > assumptions;
- smallest safe change;
- preserve behavior;
- no fabricated telemetry/identity/orientation;
- observed ≠ derived ≠ predicted ≠ dead-reckoned;
- no loops;
- no repeated blind retries;
- no destructive cleanup;
- no force-push/reset;
- never overwrite uncommitted local work;
- do not claim a test/build/device result without a fresh receipt.

## TOKEN / EXECUTION EFFICIENCY

Be concise in reasoning/output.
Batch independent read-only inspections.
Do not repeatedly reread unchanged large files.
Prefer targeted ranges/searches.
Do one focused implementation slice at a time.
After each write, verify the exact changed surface.
Do not produce verbose narration; emit only decision-relevant findings and the final receipt.

## UI-SPECIFIC BOUNDARY

UI may:
- reshape presentation;
- add animations;
- add low-risk visual hierarchy;
- improve satellite cards;
- improve skyplot rendering;
- add responsive layout;
- add clearly marked synthetic/demo visuals where explicitly non-real.

UI may not:
- create fake satellite names;
- invent C/N0, AZ/EL, Doppler, PVT, NORAD IDs;
- mutate raw observations;
- turn predicted state into observed state;
- infer orientation when sensor evidence is absent;
- move scientific computation into a composable for convenience.

## COMPLETION

Finish only when:
- implementation is present;
- focused tests pass;
- build passes;
- fresh runtime/device evidence exists when required;
- docs/roadmap/handoff are consistent;
- Git diff/commit is clean and inspectable.

Final report format:
VERIFIED / MEASURED / UNVERIFIED / BLOCKED / COMMIT

No receipt → no epistemic upgrade.


# UI/UX RADICAL REDESIGN OVERRIDE — 2026-10-06

The current Horizon UI is considered substantially underdeveloped relative to the engineering underneath it.

This is NOT a cosmetic polish task. Treat it as a full presentation-layer redesign.

Target:
MODERN SCIENTIFIC OBSERVATORY / AEROSPACE TELEMETRY INTERFACE

Do NOT turn it into:
- generic Material dashboard
- CRUD/card collection
- crypto dashboard
- neon cyberpunk
- fake NASA cosplay
- videogame HUD

The visual target is precise, quiet, dense, technical and premium.

## Design freedom

You are authorized to:
- restructure screens
- redesign navigation
- replace weak components
- redesign satellite panels
- redesign skyplot hierarchy
- redesign typography
- redesign information density
- introduce restrained motion
- introduce layered data visualization
- redesign session/history/evidence presentation
- redesign responsive/mobile layout

Preserve verified behavior and evidence, NOT weak presentation.

## Information hierarchy

The user must immediately understand:
1. what is being observed now;
2. which satellites/signals are actually present;
3. which values are OBSERVED;
4. which values are DERIVED;
5. which values are PREDICTED/PROPAGATED;
6. current session/integrity health;
7. what is missing, unknown or unavailable.

Visually distinguish:
OBSERVED / DERIVED / PREDICTED / DEAD-RECKONED / UNKNOWN / UNAVAILABLE.

## Primary observatory screen

Make the live observatory screen the product centerpiece.
Prioritize:
- live session state;
- observation count and elapsed time;
- fix/PVT state;
- integrity/freshness;
- sky/satellite visualization;
- compact active-satellite intelligence.

The skyplot must feel like a scientific instrument, not a static circle with dots.
Use depth, layered rings, restrained motion and evidence-backed signal encoding.

## Satellite panels

Replace oversized generic cards with compact instrument readouts.
Show only evidence-backed values such as:
CONSTELLATION / SVID / C/N0 / AZ / EL / DOPPLER / AGE / STATE.

Never invent a satellite name or NORAD ID.
Missing data remains visibly unavailable.

## Navigation

Organize navigation around user tasks rather than implementation modules.
Preferred conceptual model:
OBSERVE / SIGNALS / ORBIT / SESSION / EVIDENCE

Adapt to the repository if a stronger evidence-backed structure exists.

## Motion

Use motion to communicate observations, state transitions, tracking, propagation and recovery.
Avoid decorative particle storms, endless spinning and motion that harms readability.
Respect prefers-reduced-motion.

## Color semantics

Use restrained semantic encoding:
- cyan = live/observed
- amber = warning/degraded
- violet = derived/model state
- blue = propagated/orbital context
- red = fault/critical
- neutral gray = unknown/unavailable

Do not flood the interface with accent colors.

## Mobile-first

Design for the real target Samsung SM-A075F.
Validate portrait, supported landscape, small widths, large-text accessibility and long-session use.

## Performance boundary

Visual sophistication must not create duplicate collectors, unbounded polling, full-list recomputation per frame, database work inside composables, runaway recomposition or unbounded allocations.
Keep live UI projections bounded. Preserve the existing 512-observation live projection boundary unless new evidence proves a safer design.

## Architecture

Preferred:
RAW EVIDENCE → DOMAIN STATE → DERIVED STATE → BOUNDED UI PROJECTION → VISUALIZATION

Never make composables the source of scientific truth.
Do not move scientific computation into the UI merely for convenience.

## Execution

INSPECT → REPRODUCE → DESIGN → IMPLEMENT → TEST → BUILD → DEVICE VERIFY → QA → DOCUMENT

Do not make tiny cosmetic patches only to claim progress.
If the component hierarchy is weak, replace it.
If the navigation is weak, restructure it.
If the visual language is weak, redesign it.

## Non-negotiable boundary

The UI redesign MUST NOT:
- invent measurements;
- invent satellite identities;
- invent NORAD mappings;
- invent orientation;
- alter raw observations;
- alter Room persistence semantics;
- alter Observation Bus semantics;
- alter OrbitCore/SGP4/SDP4 behavior;
- silently turn UNKNOWN into KNOWN;
- silently turn PREDICTED into OBSERVED.

Presentation may be radically redesigned.
Scientific truth may not.

## Final design test

The redesigned interface should look like a serious scientific observation instrument before a reviewer reads the source code.
It must also make the boundary between observed, derived and predicted state visually obvious.
If either condition fails, the redesign is not finished.
