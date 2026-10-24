# Geometric identity hint (research prototype)

**Evidential status:** plausible, testable hypothesis â€” implemented as an isolated prototype
(`GeometricIdentityMatcher`, `GeometricIdentityAssistant`), surfaced only as the *Identity hints* report. **UNVERIFIED on real data.**

## Problem
Receiver measurements carry (constellation, SVID); orbit catalogs carry NORAD IDs. The link changes over time and the app
must not guess it. Building the operator table by hand is slow, and without it every prediction stops at UNMATCHED.

## Idea
The receiver reports each satellite's azimuth/elevation. The right catalog object, predicted by SGP4/SDP4 for the same
observer and time, should lie within a few degrees of that direction, while other objects are typically much farther away.
Orbit-prediction error (~1â€“3 km/day at ~20 000 km range â‰ˆ 0.01Â°) and frame error (< 1 km) are tiny compared with the
likely receiver az/el uncertainty.

## Method
For every observed satellite *i* and every predicted catalog object *j* above the horizon, separation
`Î¸áµ¢â±¼ = atan2(|aáµ¢ Ã— bâ±¼|, aáµ¢ Â· bâ±¼)` between unit direction vectors. A hint is **CONSISTENT** only if
1. best `Î¸ â‰¤ 3Â°` (provisional), 2. the runner-up candidate is â‰¥ 4Â° farther (provisional), and
3. the match is mutual (the candidate's closest observed satellite is *i*, with the same margin).
Otherwise AMBIGUOUS or NO_CANDIDATE. Output rows use `method = GEOMETRIC_CONSISTENCY_HINT`, `confidence = UNVERIFIED`.

## Invariants preserved
Hints never reach `SatellitePredictionUseCase` (UNVERIFIED mappings stop the chain by design); the importer refuses
`VERIFIED` for any method except `OPERATOR_SUPPLIED_TABLE`; the operator must re-enter a row as their own evidence-backed
`OPERATOR_SUPPLIED_TABLE` row to use it. The assistant reads the catalog and engine and writes nothing.

## Known weaknesses (what could falsify it)
- Android az/el may be computed from almanac and be several degrees wrong, or quantised â†’ NO_CANDIDATE/AMBIGUOUS.
- Chance alignment between different constellations when the catalog contains many objects (single epoch only).
- Thresholds are uncalibrated.

## Experiments to run (needs a device + a trusted mapping)
1. Log residual distribution `Î¸` for known-correct pairs over â‰¥ 1 h and several constellations â†’ calibrate thresholds.
2. Measure false-positive rate: shift observed directions by random offsets / use a wrong catalog subset.
3. Multi-epoch extension: require consistency at â‰¥ 3 epochs a few minutes apart (a true match stays close, a chance match drifts).
Success = â‰¥ 95 % of known pairs CONSISTENT with zero wrong CONSISTENT in the negative controls.
