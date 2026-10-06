# HORIZON — Automation Boundary

Date: 2026-09-24

## Completed automatically

The current source checkpoint has been advanced through the source-level GNSS domain stage.

Implemented automatically:

- First-class GNSS domain models for satellite evidence, navigation association, antenna evidence, history points, constellation summaries, and signal statistics.
- Time-bounded correlation of `GNSS_STATUS` and `GNSS_RAW_MEASUREMENT` by constellation + SVID with optional carrier-frequency discrimination.
- Explicit `STATUS_ONLY`, `RAW_ONLY`, and `STATUS_AND_RAW_MATCH` evidence states.
- Explicit `ASSOCIATED`, `AMBIGUOUS`, and `NOT_ASSOCIATED` navigation-message association states.
- Integration of the GNSS domain snapshot into `SessionAnalysisEngine`.
- GNSS Observatory UI based on correlated evidence rather than a single raw status row.
- Sky plot from observed azimuth/elevation.
- Observed C/N0 vs elevation plot.
- Constellation summaries and correlated satellite evidence matrix.
- Dedicated Antenna Observatory screen.
- Separate antenna evidence from satellite evidence.
- GNSS-derived export artifacts: `gnss_domain_snapshot.json`, `satellite_evidence.csv`, `gnss_antenna_evidence.json`.
- Export readback checks for the new derived artifacts.
- Additional unit-test source coverage.
- Source-level Kotlin compile sanity checks for the GNSS domain and session-analysis layer.

## External verification boundary

The next actions require the user's Windows Android environment and physical target device:

1. `:app:testDebugUnitTest`
2. `assembleDebug`
3. install APK on Samsung SM-A075F
4. run a new physical-device session
5. export the session
6. read back and verify the new GNSS derived export artifacts

No physical-device verification is claimed for the source changes above.
