# HORIZON — Next Phase: GNSS Domain Foundation

Date: 2026-09-24

## Scope

This phase establishes a first-class GNSS domain without nesting satellite data under antenna or cellular concepts.

### Domains

- Satellite observations
- Measurement evidence
- Navigation messages
- GNSS clock
- PVT
- GNSS antenna evidence
- GNSS correlation / derived analysis

## Implemented in this checkpoint

- `SatelliteCorrelationKey`
- `SatelliteEvidence`
- `GnssAntennaEvidence`
- `GnssDomainSnapshot`
- `GnssCorrelationEngine`
- Unit tests covering:
  - status/raw temporal correlation
  - constellation + SVID separation
  - status-only / raw-only evidence states
  - navigation-message association

## Correlation policy

1. Constellation + SVID are mandatory for satellite identity.
2. Carrier frequency is an optional disambiguator.
3. Raw and status records are joined only within a bounded monotonic-time window.
4. Raw observations are never modified.
5. Derived `SatelliteEvidence` is separate from raw persistence.
6. Missing fields remain null / unavailable.
7. Navigation messages remain independent raw observations and are only associated in derived analysis.
8. Antenna information remains a separate GNSS subdomain.

## Explicit non-goals

This phase does not estimate:

- antenna gain
- radiation pattern
- phase-center calibration when not exposed by Android
- sector azimuth
- satellite trajectory from synthetic models
- RF attenuation from unsupported assumptions

## Verification status

Source implementation created: VERIFIED by inspection.

Unit test source created: VERIFIED by inspection.

Full Android Gradle build in the container: BLOCKED because the checkpoint intentionally contains no Gradle wrapper JAR and the container has no system Gradle installation.

Physical-device verification: UNVERIFIED until the modified checkpoint is built and installed on SM-A075F.


## Automated completion in this checkpoint

The GNSS domain is now connected to session analysis and the GNSS Observatory UI.

- Session analysis produces a `GnssDomainSnapshot` from persisted observations.
- The UI consumes correlated satellite evidence rather than reading only the latest raw status row.
- The GNSS view exposes correlated sky position, C/N0-vs-elevation evidence, constellation summaries, satellite match state, and a separate antenna evidence panel.
- Raw observations remain displayed separately from derived evidence.
- Session export now includes `gnss_domain_snapshot.json`, `satellite_evidence.csv`, and `gnss_antenna_evidence.json`.
- Export readback verifies that the GNSS-derived files are present and structurally readable.

### Automation boundary

Everything in this source-level phase is prepared automatically. The next hard verification gate is the user's Windows Android build (`testDebugUnitTest` / `assembleDebug`) followed by physical SM-A075F installation and a new real session. No physical-device success is claimed by this source-only checkpoint.
