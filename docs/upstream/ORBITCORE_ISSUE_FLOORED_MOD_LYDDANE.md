# Upstream issue draft â€” OrbitCore 0.1.0: SGP4/SDP4 error up to ~0.96 km on low-inclination deep-space element sets

Status: **draft, not filed.** Source of all numbers: `docs/verification/RECEIPT_2026-10-16_JVM_HARNESS_AND_SGP4.md` (finding F1; JVM-harness measurements, not Gradle/device).
`orbit-src/` is a vendored third-party copy and the propagation domain is frozen by `AGENTS.md`, so **nothing was changed here**; the canary test `OrbitCoreSgp4ReferenceVectorTest` pins today's behaviour.

## Summary
For element set 23599 ("ARIANE 42P+3 R/B", deep-space, inclination 0.121 rad < 0.2 rad so the Lyddane branch is used, e = 0.578) in the Vallado verification set
(`SGP4-VER.TLE` / `tcppver.out`), OrbitCore matches the reference to â‰¤ 8e-9 km up to t â‰ˆ 400 min, then deviates: 0.92 km at 420 min, peak 0.96 km at 460 min, 0.26â€“0.88 km up to 720 min
(velocity error up to 3.9e-4 km/s). The independent python-sgp4 2.27 agrees with the reference to 7.7e-9 km on the same set.

## Root cause (verified in the harness on a scratch copy)
`SecularPropagator.kt` lines 106â€“109 normalise node, argument of perigee, `xlm` and `mm` with Kotlin `Double.mod` (result â‰¥ 0), whereas the reference implementation uses C `fmod`
(sign of the dividend). In the Lyddane branch the node and argument of perigee enter non-periodically, so a 2Ï€ shift is not harmless. The onset coincides with the secular node changing sign.

## Proposed patch
```kotlin
val nodemMod = node % TWO_PI
val argpmMod = argPerigee % TWO_PI
val xlmMod   = xlm % TWO_PI
val mmMod    = (xlmMod - argpmMod - nodemMod) % TWO_PI
```
Controlled experiment (scratch copy only): replacing the four `.mod(TWO_PI)` by `% TWO_PI` gives 7.7e-9 km on 23599 at every row and â‰¤ 1.2e-7 km on the whole set.

## Impact (INFERRED, not measured on real GNSS elements)
Only low-inclination deep-space elements (e.g. geostationary SBAS or BeiDou GEO) reach this branch; ~1 km at 42 164 km range â‰ˆ 0.0014Â°, negligible for sky plots but a correctness defect.

## Reproduce
`bash tools/jvm_harness/run.sh bridge` (downloads Kotlin once) â†’ test `OrbitCoreSgp4ReferenceVectorTest`; data hashes in `orbitcore-bridge/src/test/resources/sgp4/PROVENANCE.md`.
