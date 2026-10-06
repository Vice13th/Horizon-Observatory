# HORIZON — CURRENT ENGINEERING HANDOFF / 2026-10-06

## Start here

Read in this order:

1. AGENTS.md
2. docs/CHECKPOINT_2026-10-06_GNSS_FOUNDATION_VERIFIED.md
3. docs/verification/device-receipts/HORIZON_MEASUREMENT_ROOM_READBACK_8cf1c36f-8387-4bc9-b850-22d2c754d5e9.md
4. docs/HORIZON_MASTER_ROADMAP.md
5. docs/VERIFICATION_MATRIX.md
6. docs/AGENT_PROMPT_PACK_2026-10-06.md

Do not use chat history as code truth.

## Current verdict

GNSS FOUNDATION = PARTIAL overall.

The acquisition-to-export evidence gate is VERIFIED/CLOSED. Scientific identity/timestamp and hardware resilience gates remain open.

### Verified chain

GNSS → Observation Bus → Room → readback → ExportEngine → checksum/readback → semantic normalization

### Canonical receipt

Session:
8cf1c36f-8387-4bc9-b850-22d2c754d5e9

Export SHA-256:
b5697add8563c8380f85f7bc2bc403a5041a2a9f7d3194cc11883b922174e64e

JVM:
167 tests / 0 failures / 0 errors / 3 pre-existing skips

Instrumentation:
1 test / 0 failure / 0 error

## Frozen

Do not modify as part of the current UI phase:

- OrbitCore adapter / backend behavior
- SGP4 / SDP4
- propagation contract
- raw evidence schema/semantics
- Observation Bus persistence semantics
- Room schema unless a new evidence-backed defect requires it
- export evidence semantics

## Open

1. NORAD mapping — UNKNOWN
   - resolver is empty-table;
   - no guessed SVID→NORAD identity;
   - next design must carry lookup provenance.

2. UTC edge-case — UNVERIFIED
   - requires device-specific instrumentation;
   - do not promote documentation-only reasoning to VERIFIED.

3. K8 physical/long-run validation — PENDING
   - physical interference/degradation;
   - background/screen-off behavior;
   - resource/battery/long-run measurements.

4. Orientation-dependent UI
   - target-device sensor availability limits verification;
   - never fake heading/rotation when a valid source is absent.

## Immediate execution lane

### Lane A — UI / UX

Highest priority.

Work only in presentation/state-projection boundaries. Existing scientific state remains canonical.

Targets:
- satellite panel redesign;
- compact evidence-backed identifiers;
- skyplot hierarchy;
- dynamic refresh without duplicate collectors;
- rotation behavior only where a valid orientation source exists;
- long-session rendering performance;
- polished mobile layout.

### Lane B — Documentation

Every material UI change updates:
- checkpoint/handoff as needed;
- roadmap;
- verification matrix when evidence changes;
- receipt if a fresh runtime receipt is captured.

### Lane C — Deferred science

NORAD and UTC remain explicit debt. Do not opportunistically reopen propagation.

## Stop conditions

Stop the current UI slice before continuing when:
- a raw-evidence contract would change;
- a persistence schema would change;
- propagation behavior would change;
- a fabricated measurement/identity would be required;
- local worktree cannot be inspected and the proposed edit would overwrite UI source;
- a verification failure is being hidden or reclassified.

## Required completion receipt for UI work

Record:
- exact commit SHA;
- changed files;
- focused test result;
- build result;
- device result when executed;
- screenshot/visual result when reliable;
- performance measurement when relevant;
- explicit UNVERIFIED/BLOCKED items.

No receipt → no epistemic upgrade.
