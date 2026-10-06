# HORIZON — TOOL-FIRST UI DESIGN EXECUTION CONTRACT
## 2026-10-06

Status: CANONICAL / ACTIVE for B1/B2 UI/UX work.

Purpose:
Prevent visual redesign work from being reduced to ad-hoc Compose styling. For B1/B2, the workflow is DESIGN → VISUAL REVIEW → IMPLEMENT → DEVICE VERIFY.

## 1. TOOL-FIRST GATE

Before changing UI code, inspect the capabilities exposed by the current runtime.

Preferred tools:
1. Figma — primary visual design, design system, screen structure and reusable components.
2. MagicPath — interactive prototyping, alternate layout exploration and rapid visual iteration.
3. tldraw — structural wireframes when useful.
4. Canva / Adobe — visual assets only when genuinely useful and actually available.
5. Image generation — supporting assets only; never a substitute for UI/UX design.

Do not claim a tool was used unless the runtime actually exposed and exercised it.

If a preferred tool is unavailable:
- record TOOL UNAVAILABLE;
- use the strongest available alternative;
- do not fabricate tool usage.

## 2. MANDATORY DESIGN PASS

B1/B2 is a design-first task.

Before substantial implementation, create or update a real visual design/prototype covering, as applicable:
- OBSERVE
- SIGNALS
- ORBIT
- SESSION
- EVIDENCE
- primary observatory screen
- satellite signal panels
- skyplot
- selected-satellite detail
- loading/degraded/empty/unavailable states
- evidence/provenance presentation

This is a radical presentation redesign, not cosmetic polishing.

## 3. VISUAL TARGET

MODERN SCIENTIFIC OBSERVATORY / AEROSPACE TELEMETRY INTERFACE.

Precise, quiet, dense, technical, premium.

Avoid:
- generic Material dashboard;
- CRUD/card wall;
- crypto-dashboard aesthetics;
- neon cyberpunk;
- fake NASA cosplay;
- videogame HUD;
- decorative motion without information value.

## 4. EXPLORATION

When design tooling permits:
- produce one primary direction;
- produce one materially different alternate;
- compare hierarchy, density, readability, scientific credibility, mobile ergonomics, noise and implementation complexity;
- select one direction before broad implementation.

Do not produce many superficial variations.

## 5. SCIENTIFIC VISUAL SEMANTICS

Preserve:
OBSERVED / DERIVED / PREDICTED-PROPAGATED / DEAD-RECKONED / UNKNOWN / UNAVAILABLE.

Preferred restrained semantics:
- cyan = live / observed
- amber = warning / degraded
- violet = derived / model
- blue = propagated / orbital
- red = critical / fault
- neutral = unknown / unavailable

Never use visual treatment to imply observed certainty for derived or predicted state.

## 6. SATELLITE DATA FIREWALL

Allowed compact identity:
CONSTELLATION + SVID.

Evidence-backed telemetry only:
C/N0 / AZ / EL / DOPPLER / AGE / STATE.

Never invent:
satellite names, NORAD IDs, telemetry, missing values, orientation, heading, or signal state.

## 7. SKYPLOT

The skyplot must read as a scientific instrument, not a static circle with dots.

It may use:
- elevation rings;
- azimuth reference;
- constellation encoding;
- selected-satellite emphasis;
- signal-quality encoding;
- freshness;
- restrained evidence-backed motion.

Orientation-aware rotation is allowed only when valid orientation evidence exists.

No valid orientation source = no fake rotation.

## 8. MOBILE-FIRST

Primary target:
Samsung SM-A075F / Galaxy A07.

Design for the real device first:
- portrait;
- supported landscape;
- small width;
- large text/accessibility;
- long-session use.

Do not design a desktop dashboard and merely shrink it.

## 9. IMPLEMENTATION FIREWALL

Architecture:
RAW EVIDENCE → DOMAIN STATE → DERIVED STATE → BOUNDED UI PROJECTION → VISUALIZATION.

Do not:
- query Room from composables;
- move scientific computation into composables;
- add duplicate collectors;
- add polling loops;
- introduce unbounded allocations/recomputation;
- mutate raw observations;
- alter evidence semantics.

Keep the existing 512-observation live UI projection boundary unless new evidence proves a safer architecture.

Do not modify OrbitCore / SGP4 / SDP4 / propagation / Observation Bus / Room / export semantics during UI work unless a fresh evidence-backed defect requires intervention.

## 10. EXECUTION

INSPECT → REPRODUCE → DESIGN TOOL → VISUAL REVIEW → IMPLEMENT → TEST → BUILD → DEVICE VERIFY → VISUAL QA → DOCUMENT.

Design-tool output is part of the implementation evidence, not optional narration.

## 11. EVIDENCE

A design is not “verified” because code compiles.

Record:
DESIGN RECEIPT
- actual design tool used;
- design/prototype/file identifier when available;
- screens/components created;
- selected direction;
- material visual decisions.

IMPLEMENTATION RECEIPT
- changed files;
- diff/stat and scope classification;
- tests and exit codes;
- build command/result;
- APK path and SHA-256.

DEVICE RECEIPT
- device/model;
- package;
- install/launch result;
- screens exercised;
- observable UI behavior;
- screenshots/visual proof when reliable.

Anything lacking concrete evidence is UNVERIFIED.

## 12. HARD STOPS

Stop before broad implementation when:
- the current UI baseline was not inspected;
- an available design tool was skipped without a recorded reason;
- the visual design cannot be demonstrated;
- non-UI/scientific files changed unexpectedly;
- orientation is being fabricated;
- identity/telemetry is being fabricated;
- tests/build/device evidence is missing.

Do not repair uncertainty with assumptions.

## 13. TOKEN / EXECUTION EFFICIENCY

Be concise.
Batch independent read-only inspection.
Use targeted source ranges.
Do not repeatedly reread unchanged large files.
Do not narrate every trivial tool call.
Do one bounded implementation slice at a time.
After each write, verify only the changed surface.

Final report:
STATE / TOOLS USED / DESIGN OUTPUT / SELECTED DIRECTION / FILES CHANGED / TESTS / BUILD / APK SHA-256 / DEVICE / UNVERIFIED / NEXT BOUNDED STEP.

No receipt → no epistemic upgrade.
