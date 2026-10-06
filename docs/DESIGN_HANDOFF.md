# Horizon Observatory — Visual / Loading / Easter Egg Handoff

## Purpose
Make Horizon Observatory unmistakably scientific, atmospheric, and instrumentation-driven while remaining part of the VICE13TH LABS family.

## Shared VICE13TH LABS DNA
Use the parent DNA—dark cinematic foundation, strict hierarchy, premium spacing, material depth, restrained motion—without sharing product palettes.

## Horizon visual identity
Direction: **Frosted Glass + Atmospheric Scientific HUD**.
Base: space-black / deep navy.
Primary instrumentation accent: cyan/teal.
Secondary semantic accent: restrained amber for telemetry/warning conditions only.
The visual language should suggest scientific instruments, sky geometry, telemetry, and observation—not a generic dashboard.

## Loading states — REQUIRED
Design and audit:
- application startup
- GNSS/session initialization
- sensor/acquisition startup
- processing/correlation
- orbit/propagation calculations
- history/export/replay operations
- failure/recovery transitions

Loading states must clearly distinguish **waiting, acquiring, processing, complete, degraded, and failed** states. Do not use fake telemetry as decoration.

## Easter Eggs — REQUIRED
Easter Eggs should reinforce observation/scientific discovery themes: constellation references, subtle orbital motifs, hidden technical details, or carefully authored instrument behaviors.
They must never be confused with live measurements, predicted state, or real evidence.

Any Easter Egg that can be mistaken for real GNSS/orbit telemetry is forbidden or must be unmistakably labeled as decorative.

## Anti-patterns
No random purple/green recoloring, fake scientific data, decorative telemetry presented as evidence, noisy HUD clutter, generic neon dashboard cards, or excessive glow that reduces scientific readability.

## Verification gate
Audit loading and transient states on real device / representative runtime where available, capture screenshots, and separate design intent from verified behavior.

> WORKFLOW: INSPECT → REPORT → IMPLEMENT → VERIFY
> BEFORE IMPLEMENTATION: Read AGENTS.md, CHECKPOINT.md, and the current roadmap/technical-gap document. Reconcile this handoff with those sources before changing code.

## Execution hardening

### OBSERVED implementation anchors
- Primary UI source: app/src/main/kotlin/horizon/observatory/ui/MainActivity.kt
- UI is Compose + Material 3.
- HorizonTheme is currently the central theme entry point.
- The current primary color is purple (#C875FF) and the current surface/background are dark.
- Inspected resources contain launcher colors but no dedicated custom splash implementation was identified.

### CURRENT baseline versus TARGET
The purple Compose accent is the current baseline. The target is cyan/teal scientific instrumentation with restrained amber semantics. Migrate the theme coherently through shared tokens; do not recolor individual cards independently.

### Evidence boundary
Observed, raw, normalized, derived, predicted/propagated, inferred, unavailable and unverified states are semantic contracts. Visual treatment must not merge them or create decorative values that look measured.

### Loading hard rule
Do not invent a fake boot sequence. There is no verified dedicated custom splash in the inspected resource tree. Design only real startup, capability, permission, acquisition, analysis, propagation, export, empty, degraded and recovery states unless a real new lifecycle state is deliberately implemented.

### Sky/evidence hard rule
The sky plot, satellite panels, C/N0, elevation, azimuth, fix status and orbit state remain backed by real available observations or explicitly derived state. Decorative geometry must never masquerade as a measurement.

### Change boundary
A visual task must not rewrite acquisition, Room persistence, timestamping, analysis, orbit propagation, resilience, or domain models. Keep Compose/Material 3 and use theme/components rather than duplicating per-screen styles.

### Device boundary
The target is the Samsung Galaxy A07 / SM-A075F on Android 16/API 36. Do not claim device-level visual verification without physical-device evidence.

### Stop and report
Stop if a visual request would require fabricated telemetry, collapsing observed and predicted state, a new heavy graphics dependency, or an unverified custom startup architecture.

### Evidence required before completion
Verify portrait and landscape, cold start, permission/capability states, active GNSS acquisition, rotating sky plot/satellite panels, analysis/propagation, export, empty/degraded/error states, history/replay and Easter Eggs. Separate runtime evidence from design intent.
