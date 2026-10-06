# HORIZON — GNSS Domain Foundation Checkpoint

Date: 2026-09-24
Base: HORIZON_CHECKPOINT_2026-09-24

## What changed

This checkpoint starts the next GNSS architecture stage as an independent domain. Satellite evidence is not nested under antenna or cellular.

### Added
- `domain/gnss/GnssEvidenceModels.kt`
- `analysis/gnss/GnssCorrelationEngine.kt`
- `test/.../GnssCorrelationEngineTest.kt`
- `docs/NEXT_PHASE_GNSS_DOMAIN_FOUNDATION.md`
- `docs/NEXT_PHASE_AUTOMATION_BOUNDARY.md`
- `docs/GNSS_SOURCE_SANITY.md`
- `scripts/APPLY_GNSS_DOMAIN_FOUNDATION.ps1`

## Correlation rules

- Constellation + SVID identify the satellite.
- Carrier frequency is an optional discriminator.
- Status ↔ raw measurements are correlated within a bounded monotonic-time window.
- Raw observations are never changed.
- Navigation messages are associated only when surrounding evidence resolves the constellation; ambiguous cases remain ambiguous.
- Antenna evidence remains a separate GNSS subdomain.

## Verification

- New GNSS domain source + tests: isolated Kotlin compiler sanity PASS.
- Full Android Gradle build in this container: NOT EXECUTED / BLOCKED by unavailable wrapper JAR/system Gradle.
- Physical-device verification: NOT YET RUN for this change.


## Completed automatically after foundation

- GNSS correlation is integrated into `SessionAnalysisEngine`.
- GNSS Observatory UI now separates raw measurements, correlated satellite evidence, and antenna evidence.
- Export contains GNSS domain derived artifacts and readback checks.
- Extended GNSS domain unit coverage for latest-satellite and constellation summaries.

Next required external gate: run `:app:testDebugUnitTest` on the Windows project, then `assembleDebug`, install on SM-A075F, and perform a real-device session/export readback.
