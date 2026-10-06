# HORIZON — Current Verification Matrix

> Current repository truth: main at the latest source checkpoint. Historical receipts remain preserved; this matrix records the strongest currently evidenced state.

| Gate | Status | Basis |
|---|---|---|
| Canonical release target commit | VERIFIED | GitHub Release v1.0.0-observatory targets a6181fb6259f463e1314f5103d0bfd84ed7cf024 |
| Primary unit-test gate | VERIFIED | :app:testPrimaryDebugUnitTest PASS / exit 0 in final device-evidence closure |
| Primary APK assemble | VERIFIED | :app:assemblePrimaryDebug PASS / exit 0 in final device-evidence closure |
| Target device install | VERIFIED | Samsung SM-A075F / Android 16 / API 36 |
| Application launch | VERIFIED | Physical-device launch smoke check passed |
| Live GNSS observation path | VERIFIED | Active-session UI freshness and persisted observation evidence |
| Sequence continuity | VERIFIED | Exported session sequence 1..2341, contiguous |
| Sequence advancement | VERIFIED | Direct persisted sequence evidence: 394 → 448 → 485 |
| SVID evidence | VERIFIED | Real constellationType=1, svid=10 observed at multiple sequences |
| Constellation evidence | VERIFIED | Observed Android values include 1,2,3,5,6 across evidence layers |
| Pseudorange-rate evidence | VERIFIED | Raw Android pseudorangeRateMetersPerSecond observed; no Doppler inference |
| Room/export count reconciliation | VERIFIED | Room/runtime, manifest, JSON and CSV each report 2341 |
| Export/readback checksum | VERIFIED | Export SHA-256 7CFEC703... matched independent readback |
| Published APK asset | VERIFIED | Exact real filename and GitHub asset digest match local APK SHA-256 |
| Published APK filename | VERIFIED | Horizon-Observatory-v1.0.0-observatory.apk |
| Public release availability | VERIFIED | GitHub Release v1.0.0-observatory is published |
| NORAD identity mapping | UNKNOWN | No authoritative time-valid SVID→NORAD resolver receipt |
| UTC/device edge cases | UNVERIFIED | Dedicated target-device receipt still required |
| OrbitCore reference-vector accuracy | UNVERIFIED | Runtime/conformance evidence does not establish scientific numerical accuracy |
| Physical orientation / heading | UNVERIFIED | Rotation-vector evidence unavailable on target device |
| K8 long-duration/interference validation | PENDING | Requires bounded long-run physical validation |
| Production/final scientific validation claim | NOT CLAIMED | Release is a device-verified artifact with explicit open scientific gates |

## Release artifact identity

- GitHub Release: v1.0.0-observatory
- Release target: a6181fb6259f463e1314f5103d0bfd84ed7cf024
- APK: Horizon-Observatory-v1.0.0-observatory.apk
- APK SHA-256: 81CE8B59491525A7EC0DA91BDA3C5C0DBDEDDAAB310EC0FB93E311FE3F474A45
- Device: Samsung SM-A075F / Android 16 / API 36

The published GitHub asset was independently downloaded and its digest matched the local verified APK. No APK was committed to the source tree.
