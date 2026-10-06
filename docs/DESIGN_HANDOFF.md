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
