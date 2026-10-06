# HORIZON — Next Task: GNSS Correlation Layer

## Objective

Convert independent platform evidence streams into a correlated, evidence-preserving satellite view.

## Inputs

- `GNSS_STATUS`
- `GNSS_RAW_MEASUREMENT`
- `GNSS_NAVIGATION_MESSAGE`

## Correlation key

Primary:

`constellationType + SVID + carrierFrequencyHz when available`

Do not use SVID alone because SVID is not globally unique across constellations.

## Output

A derived satellite evidence record should expose, where actually available:

- constellation
- SVID
- carrier frequency
- C/N0
- baseband C/N0
- elevation
- azimuth
- used-in-fix
- AGC
- ADR / measurement state
- source timestamp
- HORIZON ingestion timestamp
- evidence provenance

## Analysis views

1. C/N0 vs time
2. C/N0 vs elevation
3. per-satellite history
4. azimuth/elevation sky plot
5. AGC vs time
6. navigation-message stream

## Explicitly out of scope

- synthetic satellite positions
- synthetic signal variation
- antenna radiation pattern estimation
- antenna gain estimation
- inferred phase-center model
- inferred RF attenuation presented as measured
