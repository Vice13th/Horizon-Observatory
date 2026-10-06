# HORIZON — Known Limitations at Checkpoint

1. The current checkpoint is not a final release.
2. No new APK is bundled as part of this checkpoint.
3. Physical antenna modelling is intentionally deferred.
4. Carrier phase remains unverified in the device capability snapshot.
5. Antenna info is reported unsupported by the device capability snapshot.
6. AGC capability is marked unverified in the capability snapshot even though 6.0 dB AGC samples were measured in both latest sessions. This distinction is intentional.
7. One recent session contained navigation-message observations; the other did not. `SUPPORTED` capability does not imply every session contains navigation messages.
8. GNSS status and raw measurements remain separate evidence streams. A unified satellite correlation layer is the next task.
9. Source timestamp regressions are observed across asynchronous producers. They are retained as evidence and do not invalidate ingestion monotonic ordering.
10. Cellular source age varies. UI should present freshness/age rather than asserting an unconditional live state.

## Orbit prediction foundation (added with the orbit-prediction checkpoint)
See `docs/ORBIT_PREDICTION_ARCHITECTURE.md`. In short: no SGP4/SDP4 backend and no reference vectors are integrated
(predictions end `ENGINE_UNAVAILABLE`), the SVID→NORAD table is empty (satellites end `UNMATCHED`), Room schema
baselines 5/6/7 are not checked in (migration tests skip), the catalog is never fetched automatically, sensor-driven
device orientation is `Unavailable`, and build / tests / device are UNVERIFIED for this checkpoint.
