# HORIZON — Next Build Gate / UI Phase

## Current checkpoint

HORIZON_CHECKPOINT_2026-10-06_GNSS_FOUNDATION_VERIFIED

The GNSS acquisition → Observation Bus → Room → readback → export → semantic-normalization evidence chain is already VERIFIED/CLOSED. Do not rerun it merely as ceremony when no relevant source surface changed.

## Active next gate: UI / Observatory presentation

Work only in presentation/state-projection boundaries.

Required sequence:

1. Inspect actual local working tree before editing.
2. Identify the canonical live/persisted projection feeding the affected UI.
3. Run a focused UI/unit regression before changes when practical.
4. Implement one bounded UI slice.
5. Run focused tests.
6. Assemble the affected build.
7. Install/run on SM-A075F when available.
8. Capture screenshot/runtime/performance evidence where relevant.
9. Update checkpoint/handoff/matrix only when evidence changes.
10. Commit and push with an explicit receipt.

## UI acceptance priorities

- Dynamic satellite information remains evidence-backed.
- No fabricated satellite names or telemetry.
- No raw evidence mutation.
- No duplicate collectors or unbounded polling.
- Long sessions remain stable.
- Orientation behavior is only enabled when a valid orientation source exists.
- Responsive/mobile layout remains readable.
- Scientific propagation and persistence contracts remain unchanged.

## Deferred gates

- NORAD mapping: UNKNOWN.
- Device-specific UTC edge case: UNVERIFIED.
- K8 physical interference / long-run validation: PENDING.

No receipt → no epistemic upgrade.
