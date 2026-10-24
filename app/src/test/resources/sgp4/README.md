# SGP4/SDP4 reference vectors (NOT YET IMPORTED)

Place the published Vallado verification inputs/outputs here (from the CelesTrak page for
AIAA 2006-6753 "Revisiting Spacetrack Report #3"):

- `SGP4-VER.TLE`   (element sets, near-earth and deep-space)
- `tcppver.out`    (reference state vectors)

No numeric reference values are stored in this repository yet and none may be typed from memory.
`Sgp4ReferenceVectorTest` is @Ignore'd until these files exist and a concrete backend is integrated.
Tolerances must be chosen and documented when the comparison is implemented.

## UPDATE 2026-10-16
The published files were imported byte-for-byte (with hashes) into `orbitcore-bridge/src/test/resources/sgp4/` (see `PROVENANCE.md` there) and are exercised against the real OrbitCore by `OrbitCoreSgp4ReferenceVectorTest` in `:orbitcore-bridge`. The `@Ignore`d tests in this module stay pending because the app unit-test classpath has no concrete backend.
