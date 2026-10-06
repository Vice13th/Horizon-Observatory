# HORIZON — Post STOP-Race-Fix Checkpoint

Date: 2026-09-24

This directory is the engineering checkpoint after the STOP transition race and `ObservatoryService` import repair.

## Important

This is not a production release. The package records the current source of truth and the evidence available at this point.

## Latest runtime evidence

Session `c40cd3d7-f824-4e05-bfa3-95707110ea97` completed on Samsung SM-A075F / Android 16 API 36 with:

- 10961 observations
- 0 error events
- contiguous sequence 1..10961
- non-decreasing ingestion timestamps
- 1480 source timestamp regressions preserved as source-time evidence

## Latest manual runtime report

The application launched without an observed crash. The user reported some slowness near the end of the run. Performance is the next investigation target.

## Next verification cycle

Run unit tests, assemble/install the exact checkpoint, perform a 2–5 minute controlled session, stress repeated STOP handling, capture PSS/RSS trend, then verify export/readback/integrity again.
