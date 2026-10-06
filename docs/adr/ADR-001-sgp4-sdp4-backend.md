# ADR-001: SGP4/SDP4 backend for orbit prediction

**Status:** Accepted (owner confirmed 2026-10-01); implementation BLOCKED on OrbitCore API access
**Date:** 2026-10-01
**Deciders:** vice (owner)

## Context
Predicted az/el for GNSS (MEO, ~12 h period) needs SGP4 with the SDP4 deep-space branch. The repo has the boundary (`Sgp4Sdp4Backend`) but no implementation; every prediction ends `ENGINE_UNAVAILABLE`. Rules: no hand-written unverified propagator; no two-body substitution; engine stays UNVERIFIED until published reference vectors (Vallado SGP4-VER.TLE / tcppver.out) pass.

## Decision
Adopt OrbitCore behind `Sgp4Sdp4Backend` **only if** all three hold, otherwise use Option B:
1. Compiles with Kotlin 1.9.24 / AGP 8.9.1.
2. Accepts the full OMM element set (incl. BSTAR, mean-motion convention).
3. Passes the Vallado suite in this repo's own tests within a documented tolerance.

## Options Considered
### Stage 2 bridge (T1)

The T1 integration uses `:orbitcore-bridge` as a separate Android library. The bridge depends on `com.parodison:orbit-core:0.1.0` with `runtimeOnly` and invokes the verified OrbitCore API reflectively. This keeps OrbitCore classes and Kotlin 2.4 metadata out of the bridge/app Kotlin compile and KAPT classpaths while preserving the existing `Sgp4Sdp4Backend` contract. `:app` depends on the bridge only through `t1Implementation`; primary/ts do not acquire the dependency.

The bridge maps all Horizon OMM fields available at the backend boundary: object name/id, epoch, mean motion, eccentricity, inclination, RAAN, argument of pericenter, mean anomaly, ephemeris/classification data, NORAD ID, element-set/revolution fields, BSTAR and mean-motion derivatives. OrbitCore output is mapped to Horizon TEME km / km/s state.

**Important:** the bridge is not marked verified against orbital reference vectors until a real OrbitCore 0.1.0 runtime test is executed.

## Option A: OrbitCore (`com.parodison:orbit-core`)
| Dimension | Assessment |
|---|---|
| Complexity | Low (dependency + adapter) |
| Cost | Free, Apache-2.0 (per project) |
| Scalability | n/a |
| Team familiarity | Kotlin, fits stack |
**Pros:** Kotlin, Android target, SDP4/deep-space stated, no hand-writing.
**Cons:** v0.1.0, ~1 month old, single author; own tests use "independent implementation" vectors, not necessarily Vallado; Kotlin-version compatibility and OMM input UNVERIFIED.

### Option B: Vendored Vallado reference port (with attribution)
| Dimension | Assessment |
|---|---|
| Complexity | High (port + maintain) |
| Cost | Dev time |
| Scalability | n/a |
| Team familiarity | Low |
**Pros:** The reference itself; license terms reportedly permissive with citation (second-hand, verify at CelesTrak).
**Cons:** A port is hand-writing; risk of transcription bugs; needs the same vector suite to trust.

### Not evaluated
Orekit and other JVM libraries.

### Evidence gathered 2026-10-01 (GitHub README / klibs.io; source not readable: robots-blocked)
- Stated: faithful port of the Vallado/Crawford/Hujsak/Kelso 2006 revision (WGS-72); SGP4 + SDP4 incl. GPS-like semi-synchronous; accepts TLE and CelesTrak JSON GP; high-level `Satellite` API (TEME position/velocity, look angles).
- Maturity: v0.1.0, 5 commits, 2 stars, 0 forks. Its own vectors come from an "independent implementation".
- Still UNKNOWN: exact API (class/constructor names), Kotlin-version compatibility, how OMM fields map in. No adapter was written: that would require inventing the API.

## Trade-off Analysis
Dependency risk (young library) vs. transcription risk (port). Both are neutralized only by the same external gate: reference vectors. So the vectors, not the choice, are the trust anchor; A is cheaper to try first.

## Consequences
- Easier: swapping backends (interface is stable); trust is test-driven.
- Harder: needs `SGP4-VER.TLE` + `tcppver.out` imported; never typed from memory.
- Revisit: Kozai/Brouwer mean-motion handling per library; accuracy of GP elements for GNSS must be measured, not assumed.

## Action Items
1. [ ] Import Vallado vectors into `app/src/test/resources/sgp4/`.
2. [ ] Spike OrbitCore (BLOCKED: needs API). Owner: run `curl -L -o oc.jar https://repo1.maven.org/maven2/com/parodison/orbit-core-jvm/0.1.0/orbit-core-jvm-0.1.0.jar; jar tf oc.jar` (artifact name unverified; browse the Maven Central dir if 404) and send the class list / the `Satellite` source.
3. [ ] Implement adapter; un-@Ignore `Sgp4ReferenceVectorTest`; document tolerance.
4. [ ] Only then set `EngineVerificationStatus.VERIFIED_AGAINST_REFERENCE_VECTORS`.
