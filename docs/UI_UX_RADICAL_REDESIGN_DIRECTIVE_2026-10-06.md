# HORIZON — UI/UX RADICAL REDESIGN DIRECTIVE — 2026-10-06

## Status
AUTHORIZED / PRESENTATION-LAYER ONLY

The current Horizon UI is substantially underdeveloped relative to the engineering underneath it.
This is not a cosmetic polish task. Treat it as a full observatory-interface redesign.

## Target
MODERN SCIENTIFIC OBSERVATORY / AEROSPACE TELEMETRY INTERFACE

Precise, quiet, dense, technical, premium.

Avoid:
- generic Material dashboard
- card/CRUD wall
- crypto-dashboard aesthetics
- neon cyberpunk
- fake NASA cosplay
- videogame HUD

## Design freedom
You may:
- restructure screens and navigation;
- replace weak components;
- redesign satellite panels;
- redesign skyplot hierarchy;
- redesign typography and information density;
- add restrained motion;
- add layered data visualization;
- redesign session/history/evidence presentation;
- redesign responsive/mobile layout.

Preserve verified behavior/evidence, not weak presentation.

## Information hierarchy
Immediately communicate:
1. what Horizon observes now;
2. which satellites/signals are actually present;
3. OBSERVED values;
4. DERIVED values;
5. PREDICTED/PROPAGATED values;
6. session/integrity health;
7. missing/unknown/unavailable evidence.

Visually distinguish:
OBSERVED / DERIVED / PREDICTED / DEAD-RECKONED / UNKNOWN / UNAVAILABLE.

## Primary observatory screen
Make the live observatory the centerpiece:
- session state;
- observation count / elapsed time;
- PVT/fix state;
- integrity/freshness;
- sky/satellite visualization;
- compact active-satellite intelligence.

Skyplot must feel like a scientific instrument, not a static circle with dots.
Use depth, layered geometry, restrained motion and evidence-backed visual encoding.

## Satellite panels
Use compact instrument readouts.
Prefer:
CONSTELLATION / SVID / C/N0 / AZ / EL / DOPPLER / AGE / STATE

Never invent:
satellite names, NORAD IDs, telemetry, or missing values.

## Navigation
Organize around user tasks rather than implementation modules.
Preferred conceptual model:
OBSERVE / SIGNALS / ORBIT / SESSION / EVIDENCE

Adapt only when repository evidence supports a better structure.

## Motion
Use motion to communicate observations, tracking, state transitions, propagation and recovery.
Avoid decorative motion that harms readability.
Respect prefers-reduced-motion.

## Color
Use restrained semantic encoding:
cyan = live/observed
amber = warning/degraded
violet = derived/model
blue = propagated/orbital
red = fault/critical
neutral = unknown/unavailable

## Mobile
Design for Samsung SM-A075F.
Validate portrait, supported landscape, small widths, large-text settings, and long-session use.

## Performance
Never sacrifice runtime stability for visuals.
No:
- duplicate collectors
- unbounded polling
- full-list recomputation per frame
- database queries from composables
- runaway recomposition
- unbounded allocations

Keep the existing 512-observation live UI projection boundary unless new evidence proves a safer architecture.

## Architecture
Preferred:
RAW EVIDENCE → DOMAIN STATE → DERIVED STATE → BOUNDED UI PROJECTION → VISUALIZATION

Never make composables scientific sources of truth.
Do not move scientific computation into UI for convenience.

## Execution
INSPECT → REPRODUCE → DESIGN → IMPLEMENT → TEST → BUILD → DEVICE VERIFY → QA → DOCUMENT

Do not make tiny cosmetic patches merely to claim progress.
Replace weak component structures when necessary.

## Non-negotiable scientific firewall
The UI redesign MUST NOT:
- invent measurements;
- invent identities;
- invent NORAD mappings;
- invent orientation;
- alter raw observations;
- alter Observation Bus semantics;
- alter Room semantics;
- alter export semantics;
- alter OrbitCore / SGP4 / SDP4;
- turn UNKNOWN into KNOWN;
- turn PREDICTED into OBSERVED.

Presentation may be radically redesigned.
Scientific truth may not.

## Final design test
The result should look like a serious scientific observation instrument before a reviewer reads the source.
It must also make observed vs derived vs predicted state unmistakable.
