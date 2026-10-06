# HORIZON — DEVICE RECEIPT / GNSS MEASUREMENT → ROOM READBACK → EXPORT

Date: 2026-10-06
Target: Hive / Samsung SM-A075F / Android 16 / API 36
Session: 8cf1c36f-8387-4bc9-b850-22d2c754d5e9
Evidence status: VERIFIED / CLOSED for the acquisition-to-export chain

> This document records the fresh evidence supplied for the 2026-10-06 device session. It does not extend claims beyond the measurements and checks listed here.

## 1. Session integrity

- Observation count: 3,250
- Sequence: 1..3250, complete and contiguous
- Ingestion timestamp coverage: 3,250 / 3,250
- Ingestion timestamps: non-decreasing
- Source timestamp regressions: 150

The 150 source timestamp regressions are retained as diagnostic evidence. They are not classified as ingestion-order failures because measurements from different satellites do not form one globally monotonic source-time series.

## 2. Bounded raw GNSS evidence

A bounded sample of 25 raw GNSS rows was read back.

Preserved evidence includes:
- constellation
- SVID
- C/N0
- satellite time
- pseudorange-rate
- ADR

Optional carrier-phase fields preserve explicit JSON nulls:
- carrierPhase = null
- carrierPhaseUncertainty = null

No missing value is replaced with a guessed measurement.

## 3. Room persistence and export

Verified:
- Room readback
- ExportEngine execution
- checksum verification
- export readback

Export SHA-256:

b5697add8563c8380f85f7bc2bc403a5041a2a9f7d3194cc11883b922174e64e

## 4. Semantic normalization

Contract:

GNSS_SEMANTIC_NORMALIZATION_V1

Fresh device evidence:
- normalized_payload_keys_bounded=[10]
- classification: DERIVED
- rawPayloadPreserved=true

This establishes the intended architecture: normalization produces a derived bounded view while the original raw payload remains preserved.

## 5. Automated verification

Direct instrumentation:
- tests: 1
- failures: 0
- errors: 0

Full JVM suite:
- tests: 167
- failures: 0
- errors: 0
- pre-existing skips: 3

## 6. Remaining gates

### NORAD mapping
UNKNOWN

The resolver is currently an empty table. No constellation/SVID→NORAD mapping was inferred or guessed.

### Device-specific UTC edge case
UNVERIFIED

A dedicated target-device timestamp edge-case receipt is still required.

### K8 physical / long-run validation
PENDING

This receipt does not validate physical jamming/spoofing or long-run background/resource behavior.

## 7. Freeze boundary

This receipt does not alter or revalidate:
- OrbitCore scientific accuracy;
- SGP4/SDP4 numerical accuracy;
- physical RF behavior;
- NORAD identity resolution;
- device-specific UTC edge cases.

Those remain separate evidence gates.
