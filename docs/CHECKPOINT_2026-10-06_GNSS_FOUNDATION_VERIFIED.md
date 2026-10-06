# HORIZON — CHECKPOINT 2026-10-06 / GNSS FOUNDATION VERIFIED

Checkpoint ID: HORIZON_CHECKPOINT_2026-10-06_GNSS_FOUNDATION_VERIFIED
Repository: Vice13th/Horizon-Observatory
Target: Samsung SM-A075F / Android 16 / API 36
Canonical device session: 8cf1c36f-8387-4bc9-b850-22d2c754d5e9
Canonical evidence receipt: docs/verification/device-receipts/HORIZON_MEASUREMENT_ROOM_READBACK_8cf1c36f-8387-4bc9-b850-22d2c754d5e9.md

## 1. Checkpoint verdict

VERIFIED / CLOSED

The current GNSS measurement evidence chain is closed for:

GNSS acquisition → Observation Bus → persistence → Room readback → export → checksum/readback → semantic normalization

This checkpoint does not claim final GNSS scientific completion, final orbital accuracy, NORAD identity resolution, device-specific UTC edge-case closure, or K8 physical interference validation.

## 2. Fresh device evidence

- 3,250 observations.
- Sequence is exactly contiguous: 1..3250.
- Ingestion timestamp coverage: 3250/3250.
- Ingestion timestamps are non-decreasing.
- 25 bounded raw GNSS rows were inspected.
- Preserved evidence includes constellation, SVID, C/N0, satellite time, pseudorange-rate, and ADR.
- carrierPhase and carrierPhaseUncertainty remain explicit JSON nulls where absent.
- Room readback was verified.
- ExportEngine and export readback were verified.
- Export SHA-256: b5697add8563c8380f85f7bc2bc403a5041a2a9f7d3194cc11883b922174e64e.

## 3. Semantic normalization

Contract:
GNSS_SEMANTIC_NORMALIZATION_V1

Fresh device evidence:
- normalized_payload_keys_bounded=[10]
- classification: DERIVED
- rawPayloadPreserved=true

Interpretation: normalization is a derived view over preserved raw evidence; it is not a destructive replacement of the raw payload.

## 4. Test evidence

Direct instrumentation: 1 test / 0 failure / 0 error.

Full JVM suite: 167 tests / 0 failures / 0 errors / 3 pre-existing skips.

No new scientific claim is inferred from these test counts.

## 5. Timestamp semantics

Fresh session reported:
source_timestamp_regressions=150

This is retained as a diagnostic observation. It is not classified as a sequence or ingestion failure because independent satellite measurements do not form one globally monotonic source-time stream. The integrity boundary remains sequence continuity and monotonic ingestion time.

## 6. Open gates

### NORAD mapping — UNKNOWN

The resolver is intentionally an empty table. No constellation/SVID→NORAD mapping is fabricated or guessed.

Required next evidence:
- authoritative mapping source,
- time-valid mapping semantics where needed,
- explicit lookup provenance,
- deterministic resolver tests.

### Device-specific UTC edge case — UNVERIFIED

Required next evidence:
- dedicated target-device instrumentation around the relevant timestamp conversions,
- explicit edge-case receipt,
- no reuse of generic documentation as device proof.

### K8 hardware / long-run — PENDING

Physical interference/degradation and long-run background/resource validation remain separate gates.

## 7. Frozen domains

The following remain frozen for this checkpoint:

- raw GNSS evidence schema/semantics;
- Observation Bus;
- Room persistence path;
- export evidence path;
- OrbitCore;
- SGP4 / SDP4;
- propagation contract;
- scientific propagation behavior.

UI changes must consume the existing canonical state and may not silently change these domains.

## 8. UI authorization

B0 panel-recovery firewall is closed.

The next active track is UI/UX only, with evidence-preserving constraints:
- dynamic satellite panels,
- skyplot presentation,
- layout/readability,
- responsive rendering,
- performance measurement,
- orientation-aware behavior only where valid sensor evidence exists.

No fabricated satellite identity or telemetry is permitted.

## 9. Workspace note

A temporary local window.xml was reported as remaining in the worktree. It is not evidence, is not part of this checkpoint, and should not be promoted to Git without explicit need and inspection.

## 10. Source of truth hierarchy

1. Current repository source on the checked-out branch.
2. This checkpoint.
3. Canonical device receipt.
4. Verification matrix / engineering status.
5. Current handoff.
6. Older historical reports.

Chat history is not a source of repository truth.
