# HORIZON — GNSS Resilience Master Roadmap

Date: 2026-10-06

## Status

**ROADMAP UPDATE ONLY — NO PRODUCTION CODE CHANGED**

This document is a planning/control artifact for the HORIZON Observatory repository.

Current repository baseline already contains:
- GNSS raw measurement/status/navigation acquisition
- cellular acquisition
- sensor acquisition
- PVT/location fixes
- per-session sequencing and lifecycle integrity
- Room-backed pending/evidence persistence
- timestamp/provenance models
- GNSS correlation analysis
- orbit/prediction infrastructure
- export/readback infrastructure
- Compose Observatory UI

The current UI surface is intentionally treated as **UNSTABLE / RECOVERY REQUIRED** for planning purposes. Do not use the current panel condition as justification for broad architectural rewrites.

---

# 1. PRIMARY GOAL

Evolve HORIZON from a GNSS observation application into a resilient, evidence-first positioning/observability platform by combining the useful capabilities found in:

- GPS Locker / GPS Keeper style session persistence
- GPSTest / GPS Status style diagnostics
- Android Raw GNSS tooling
- GNSS Logger / RINEX workflows
- GalileoPVT / independent PVT workflows
- GNSS + IMU + cellular resilience architectures

The project MUST remain:

- evidence-first
- local-first
- replayable
- provenance-preserving
- explicit about observed vs derived vs predicted data
- safe to evolve incrementally

---

# 2. NON-NEGOTIABLE SAFETY RULE

## DO NOT BREAK THE VERIFIED BASELINE

Until a gate explicitly permits it:

- do not rewrite the persistence layer
- do not replace the acquisition architecture
- do not replace Room
- do not replace the session state machine
- do not replace the sequencing model
- do not merge prediction into observation models
- do not refactor the entire UI
- do not change Gradle/toolchain versions without a dedicated compatibility gate
- do not introduce a new DI framework merely for convenience
- do not perform broad package renames
- do not perform speculative cleanup

Every change must be minimal, reviewable, reversible, and backed by verification.

---

# 3. CURRENT PRIORITY ORDER

The priority order is intentionally different from a normal feature roadmap:

1. Preserve data/integrity baseline
2. Recover the broken/unstable UI surface
3. Stabilize long-running acquisition/session behavior
4. Strengthen raw GNSS evidence
5. Strengthen time-domain integrity
6. Complete assistance and diagnostics
7. Complete scientific export/replay
8. Harden orbit/prediction/correlation
9. Add sensor/cellular fusion primitives
10. Add independent PVT
11. Add resilient navigation
12. Rebuild advanced UI on top of canonical state
13. Final long-run/device/release verification

**Rule:** feature velocity never outranks data integrity or runtime stability.

---

# 4. GATE 0 — REPOSITORY TRUTH / BASELINE LOCK

## Goal

Freeze an exact known baseline before additional feature work.

## Actions

Inspect the actual working tree and record:

- branch
- commit
- Gradle/toolchain
- modules
- database schema/version
- migration state
- acquisition sources
- service lifecycle
- current UI state flow
- export pipeline
- current tests
- current known failures

Create/update:

- docs/ARCHITECTURE_STATE.md
- docs/VERIFICATION_MATRIX.md
- docs/KNOWN_LIMITATIONS.md
- docs/HORIZON_ENGINEERING_JOURNAL.md
- docs/HORIZON_REQUIREMENTS_MATRIX.md
- docs/HORIZON_RISK_REGISTER.md

## Gate PASS

A reproducible baseline and rollback reference exists.

---

# 5. GATE 1 — PANEL RECOVERY / UI FIREWALL

## Goal

Make the existing panel reliable before adding visual features.

This is a **recovery gate**, not a redesign gate.

## Rules

During this gate:

- no new navigation features
- no new AR features
- no independent PVT UI
- no decorative redesign
- no large composable rewrite unless required to restore correctness

## Investigate first

- stale state after rotation
- collector/UI polling behavior
- excessive work on the main thread
- oversized Compose state scope
- repeated database snapshots
- unnecessary full-list recomputation
- state invalidation/recomposition storms
- satellite panel refresh correctness
- service/activity lifecycle coupling
- rendering cost of skyplot
- long-session UI memory retention
- export UI side effects leaking into the main state

## Target architecture

\`\`\`text
Acquisition
    ↓
Canonical persisted/live state
    ↓
UI state mapper
    ↓
Small feature screens/components
\`\`\`

NOT:

\`\`\`text
Composable
    ↓
database reads
    ↓
analysis
    ↓
business logic
    ↓
render
\`\`\`

## Acceptance

- rotation does not lose live state
- satellite data refreshes without reopening screen
- no duplicate collectors
- no unbounded polling
- UI remains responsive during acquisition
- no crash introduced
- raw observation values remain unchanged

**UI feature freeze remains active until this gate passes.**

---

# 6. GATE 2 — GNSS SESSION RESILIENCE

Absorb the useful GPS Locker concept without pretending it is RF amplification.

## Capabilities

- foreground location service
- correct Android lifecycle
- persistent GNSS request
- screen-off operation
- background execution within platform rules
- service recreation recovery
- provider state monitoring
- GNSS started/stopped
- first fix / TTFF
- safe STOP handling
- duplicate callback protection
- stale callback detection
- session continuity
- explicit recovery state

## Important

GPS Locker-style behavior is:

\`\`\`text
preserve/recover receiver session
≠
increase RF power
\`\`\`

## Acceptance

Controlled device run covering:

- start
- acquire
- screen off
- background
- return foreground
- rotation
- stop
- restart

No orphan callbacks, duplicate session, or sequence corruption.

---

# 7. GATE 3 — RAW GNSS EVIDENCE

Complete and verify the raw GNSS evidence pipeline.

## APIs

- GnssMeasurement
- GnssClock
- GnssMeasurementsEvent
- GnssNavigationMessage
- NMEA
- GnssStatus

## Fields where hardware/API supports them

- SVID
- constellation
- state
- C/N0
- carrier frequency
- azimuth
- elevation
- received satellite time
- received-time uncertainty
- pseudorange rate
- pseudorange-rate uncertainty
- ADR
- ADR state
- carrier phase when exposed
- multipath
- clock bias
- clock drift
- full bias
- leap-second context
- navigation message data

## Rule

Capability unavailable on a device is a runtime fact, not a failure.

No fabricated values.

## Acceptance

\`\`\`text
RAW
 → NORMALIZED
 → PERSISTED
 → READBACK
\`\`\`

with count and provenance reconciliation.

---

# 8. GATE 4 — TIME DOMAIN HARDENING

Create strict timestamp-domain handling.

Track separately:

- elapsed realtime
- GNSS clock
- UTC
- satellite time
- measurement event time
- persistence time
- export time

Every timestamp must declare its domain.

Detect and preserve:

- source timestamp regression
- ingestion regression
- clock discontinuity
- stale measurement
- leap-second changes
- unknown conversion

Never silently "fix" raw source timestamps.

---

# 9. GATE 5 — ASSISTANCE / A-GNSS

Investigate and integrate supported assistance mechanisms.

Candidates:

- A-GPS assistance
- time assistance
- PSDS/XTRA-style assistance
- SUPL
- almanac/ephemeris assistance
- assistance reset
- assistance freshness

Vendor-specific or root-only mechanisms remain optional adapters.

## Measurement requirement

If assistance is claimed to improve acquisition:

\`\`\`text
before
→ assistance action
→ after
→ TTFF / acquisition comparison
\`\`\`

No improvement claim without measurement.

---

# 10. GATE 6 — SCIENTIFIC EXPORT + REPLAY

Expand export beyond a UI convenience.

Targets:

- JSON
- CSV
- NMEA
- RINEX where feasible
- GPX/KML as track products

Required metadata:

- session id
- device/capability metadata
- observation provenance
- timestamp domain
- schema version
- integrity result
- counts

## Replay

Same input dataset should produce reproducible outputs for deterministic stages.

Replay must never alter the original raw evidence.

---

# 11. GATE 7 — SATELLITE IDENTITY + CORRELATION

Normalize satellite identity:

\`\`\`text
constellation
+ SVID
+ optional carrier/band
+ epoch
→ normalized satellite identity
\`\`\`

Keep ambiguity explicit.

Where justified:

- SVID → canonical satellite/catalog identity
- observation ↔ predicted satellite correlation

No blind SVID-only mapping when ambiguity exists.

---

# 12. GATE 8 — ORBIT / PREDICTION HARDENING

Continue the existing orbit work rather than introducing a second propagation stack.

Target:

- OMM/TLE ingestion
- SGP4/SDP4 boundary
- existing OrbitCore bridge
- satellite propagation
- topocentric conversion
- visibility prediction

HARD RULE:

\`\`\`text
ObservedSatelliteState
        ≠
PredictedSatelliteState
\`\`\`

Prediction may explain or correlate observations but must never overwrite them.

---

# 13. GATE 9 — SENSOR OBSERVATION FOUNDATION

Stabilize sensor streams before attempting full fusion.

Sources:

- accelerometer
- gyroscope
- magnetometer
- rotation/orientation
- barometer when available

For each source preserve:

- timestamp
- accuracy/quality where available
- sensor identity
- availability
- value
- dropout
- reset/uncertainty information

No giant filter yet.

First create trustworthy sensor evidence.

---

# 14. GATE 10 — CELLULAR OBSERVATION FOUNDATION

Keep cellular independent from GNSS.

Capture where Android exposes it:

- registered cell
- neighboring cells
- GSM
- WCDMA
- LTE
- NR
- signal measurements
- timing information where available
- freshness metadata

Do not hide source disagreement.

---

# 15. GATE 11 — PVT / QUALITY ENGINE

Maintain distinct products:

- Android/platform PVT
- independent PVT
- dead reckoned state
- fused PVT

Create evidence-backed quality metrics.

Candidates:

- satellite count
- used-in-fix ratio
- C/N0 distribution
- geometry
- temporal stability
- residuals
- source agreement
- measurement age

Never invent an accuracy score.

---

# 16. GATE 12 — INDEPENDENT PVT

Only begin after raw measurement quality and timestamps are stable.

Pipeline:

\`\`\`text
raw measurements
→ satellite state
→ corrections
→ weighted observation set
→ solution
→ residuals
→ quality
\`\`\`

First goal is observability and comparison, not replacing Android's location output.

---

# 17. GATE 13 — RESILIENT NAVIGATION

Long-term differentiator.

State machine:

- FULL_GNSS
- GNSS_DEGRADED
- PARTIAL_GNSS
- GNSS_LOST
- INERTIAL_BRIDGING
- CELL_AIDED
- MULTI_SOURCE_FUSION
- RECOVERY

Inputs:

- remaining GNSS evidence
- IMU
- cellular evidence
- last trusted PVT
- velocity
- heading
- time
- prediction models

No source gets unconditional authority.

Every state transition requires measurable evidence.

---

# 18. GATE 14 — ANOMALY / TRUST ENGINE

Detect:

- stale observations
- timestamp regression
- impossible movement
- C/N0 collapse
- identity mismatch
- ephemeris/prediction mismatch
- PVT disagreement
- sensor dropout
- cellular contradiction
- clock discontinuity

Each anomaly:

- type
- severity
- source
- timestamp
- evidence
- confidence
- lifecycle

Raw evidence remains preserved.

---

# 19. GATE 15 — SECOND UI BUILD

Only after the data/fusion foundations are stable.

Rebuild the panel around canonical live state.

Recommended surface:

## Overview

- session
- GNSS state
- fix
- integrity
- source health

## GNSS

- live satellite list
- constellation summaries
- C/N0
- used-in-fix
- azimuth/elevation
- age

## Sky

- observed satellites
- predicted satellites
- explicit provenance

## Cellular

- registered + neighboring cell evidence

## Sensors

- sensor availability + live streams

## Timeline

- source events
- anomalies
- state transitions

## History

- session summaries
- integrity

## Diagnostics

- capability report
- unsupported-field explanation

UI must be a projection of state, not a state owner.

---

# 20. GATE 16 — LONG-SESSION HARDENING

Test at least:

- 15 min
- 30 min
- 60 min
- 2 h
- 4 h
- 8 h

Measure:

- memory
- CPU
- database growth
- event throughput
- dropped events
- callback count
- ANR
- crashes
- UI responsiveness
- battery behavior

A "stable" label requires evidence.

---

# 21. GATE 17 — DEVICE / RELEASE GATE

Target:

Samsung SM-A075F / Android 16 / API 36

Required evidence:

1. build
2. install
3. launch
4. permissions
5. capability report
6. GNSS status
7. raw GNSS measurements
8. navigation messages
9. antenna information where available
10. cellular
11. sensors
12. session lifecycle
13. STOP/restart
14. sequence integrity
15. timestamp integrity
16. export/readback
17. rotation/background
18. long session

Only then can a field-verified release claim be made.

---

# 22. REQUIREMENT PRIORITY

## P0 — Protect

- integrity
- persistence
- timestamps
- lifecycle
- no fabricated data
- crash prevention

## P1 — Build next

- panel recovery
- session resilience
- raw GNSS
- readback
- long-session stability

## P2

- assistance
- NMEA/RINEX
- satellite correlation
- prediction hardening

## P3

- sensor/cell observation
- quality/anomaly engine
- independent PVT

## P4

- resilient navigation
- multi-source fusion

## P5

- AR
- advanced visualizations
- UX polish

---

# 23. CHANGE CONTROL

For every change record:

- requirement id
- current behavior
- desired behavior
- exact files
- rationale
- risk
- expected regression surface
- tests
- build
- device evidence
- rollback point

Prefer:

**one subsystem → one focused change → verify → document → next subsystem**

---

# 24. ANTI-LOOP CONTRACT

No action may repeat without a changed hypothesis.

Retry budget:

1. diagnose
2. alternate valid method
3. stop and document

Never:

- rebuild endlessly
- reinstall endlessly
- regenerate source repeatedly
- refactor to escape an error
- convert UNKNOWN into PASS
- claim device behavior from compilation

Every execution cycle has:

- start state
- success condition
- failure condition
- retry limit
- exit condition
- next action

---

# 25. EPISTEMIC LABELS

Use:

- VERIFIED
- OBSERVED
- MEASURED
- USER-ASSERTED
- INFERRED
- UNVERIFIED
- UNKNOWN

Rule:

**NO RECEIPT → NO EPISTEMIC UPGRADE**

---

# 26. CHECKPOINT CONTRACT

After every gate, create/update:

- engineering journal entry
- verification matrix
- risk register
- checkpoint metadata

A checkpoint is valid only when:

- repository is coherent
- tests relevant to the change are run
- build state is known
- device verification status is explicit
- rollback point is identifiable

---

# 27. IMMEDIATE NEXT STEPS

The next execution sequence is intentionally narrow:

### NOW

**Gate 0 — Baseline Lock**

Then:

**Gate 1 — Panel Recovery**

Then:

**Gate 2 — GNSS Session Resilience**

Then:

**Gate 3 — Raw GNSS Evidence**

No new advanced UI work before Gate 1 passes.

No independent PVT before Gates 3–8 provide trustworthy inputs.

No resilient navigation before PVT/quality/state foundations are established.

---

# 28. DEFINITION OF DONE

HORIZON is done only when:

- raw evidence is trustworthy
- timestamps are coherent
- sessions survive real device conditions
- persistence is integrity-safe
- GNSS/CELL/IMU remain separately observable
- observed and predicted data are distinct
- PVT sources are distinguishable
- fusion has provenance/confidence
- degradation is detectable
- recovery transitions are explicit
- long sessions are measured
- rotation/lifecycle are verified
- export and replay are verified
- final APK/device behavior is evidenced

Visual polish is never a substitute for scientific integrity.

---

# 29. DOCUMENT STATUS

This file is a roadmap/control-plane document.

It does not authorize production-code changes by itself.

Production implementation must pass the gate order above and preserve the known-good baseline.

END
