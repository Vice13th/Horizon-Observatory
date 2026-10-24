# Provenance of the SGP4 verification data (raw data, immutable)

`SGP4-VER.TLE` (inputs) and `tcppver.out` (reference TEME states, WGS-72) are the published Vallado verification
set (AIAA 2006-6753, "Revisiting Spacetrack Report #3"). They were copied **byte-for-byte** from the PyPI source
distribution `sgp4-2.27.tar.gz` (path `sgp4-2.27/sgp4/`, MIT-licensed redistribution by Brandon Rhodes) on 2026-10-16.
No reference value was typed by hand. Byte equality with the CelesTrak original was NOT checked (UNVERIFIED).

Machine-checked by `OrbitCoreSgp4ReferenceVectorTest` (do not edit the data files; create a new dated set instead):

sha256 d246d1d9d768ace445a38a965713fa9ba52d80fd8a41a0502ff83d7acffe2881 SGP4-VER.TLE
sha256 687bf28dbe52df86e8e60ab5cb4a08d1aa3dbcaf4e63b1f7ab95f044fbe3833b tcppver.out
source-sdist-sha256 06d37247c6985739b707b8b39b6b83e06e8af783b2531e31965501ba983985f9 sgp4-2.27.tar.gz
