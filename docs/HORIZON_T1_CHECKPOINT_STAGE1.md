# HORIZON T1 CHECKPOINT
Stage: 1 (INSPECT / GROUND TRUTH) — **BLOCKED at OrbitCore API boundary**
Date: 2026-10-01

## IDENTITY
Application identity: HORIZON T1 = flavor `t1`, applicationId `horizon.observatory.t1[.debug]`, label "HORIZON T1" (replaces my earlier unrequested-by-contract `pr2` flavor; reversible, 3 lines in app/build.gradle.kts).
Isolation status: PARTIAL. Flavors `primary` (`horizon.observatory`) and `ts` keep their old ids/labels and would collide with other Horizon installs. Build/install ONLY `assembleT1Debug`. USER DECISION NEEDED whether to relabel/retire `primary`/`ts` in this branch.

## VERIFIED (read from repo)
Kotlin 1.9.24, AGP 8.9.1, Room 2.6.1, Compose BOM 2024.12.01 / compiler 1.5.14. No OrbitCore reference anywhere (no dependency, no adapter). `Sgp4Sdp4Backend` boundary + `UnavailableSgp4Sdp4Backend` exist. `app/src/test/resources/sgp4/` holds only README.md (no SGP4-VER.TLE, no tcppver.out). ADR-001 Accepted; adapter blocked.

## OBSERVED
This sandbox: Maven Central returns HTTP 403 (egress blocked); no javap/jar, no Gradle cache. OrbitCore GitHub source: robots-blocked.

## USER-PROVIDED / README-ONLY (not upgraded)
OrbitCore: coordinate `com.parodison:orbit-core:0.1.0`; claims Vallado-2006 port, SGP4+SDP4, TLE and CelesTrak JSON GP input, `Satellite` API, Android target. Artifact structure, API signatures, Kotlin 1.9.24 compatibility: UNKNOWN.

## CHANGES
app/build.gradle.kts: flavor pr2 -> t1. docs: this file. Nothing else.

## TESTS / BUILD
None run (UNVERIFIED).

## BLOCKED
Reason: OrbitCore artifact cannot be resolved here.
Evidence missing: published artifact list, class/member signatures, input API (TLE/OMM/GP), output types, time/coordinate representation.
Verifiable now: repo state above.
Exact next artifact required (any one): (a) output of on your machine
  `curl -L -o oc.jar https://repo1.maven.org/maven2/com/parodison/orbit-core-jvm/0.1.0/orbit-core-jvm-0.1.0.jar` (name unverified; browse .../com/parodison/ for the real one) then `javap -cp oc.jar -public <each class from "jar tf oc.jar">`; or (b) the `Satellite` source.
Also needed for Stage 3: SGP4-VER.TLE + tcppver.out in app/src/test/resources/sgp4/.

## NEXT AUTHORIZED STAGE
Stage 2 only after the API evidence above is supplied.
