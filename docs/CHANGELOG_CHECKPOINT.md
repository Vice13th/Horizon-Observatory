# HORIZON — Checkpoint Changelog

## Pre-existing engineering foundations

- session state machine hardened
- monotonic timestamp propagation and integrity clock
- session-scoped sequence integrity
- persistence queue / drain behavior
- domain/entity separation
- capability/provenance metadata
- Room persistence
- integrity audit
- verified export package and read-back checks
- runtime capability scanner
- physical-device validation workflow
- UI navigation/export/logging refinements

## Next Phase

### GNSS

- multi-satellite matrix data is surfaced from persisted status/raw records
- elevation and azimuth are preserved from status observations
- navigation-message acquisition/persistence is supported
- AGC observations are preserved and classified
- AGC is explicitly reported as constant when the measured values are constant

### Cellular

- fresh cell-info request pathway
- callback pathway
- acquisition trigger metadata
- source age metadata
- serving-cell preference in derived RSRP analysis

### Tests

- exact-constant AGC test
- serving-cell RSRP analysis test

## Immediate compile correction

The first Next Phase compile exposed a symbol mismatch in `SessionAnalysisEngine.kt` (`latestStatus`). A focused fix was applied; the user then reported successful `compileDebugKotlin`.

## 2026-09-24 — GNSS Domain Automation Completion

Completed source-level automation after the GNSS domain foundation:

- Integrated `GnssDomainSnapshot` into session analysis.
- Added latest-satellite evidence, history points, constellation summaries, and signal statistics.
- Reworked GNSS Observatory UI to consume correlated evidence.
- Added observed C/N0 vs elevation visualization.
- Added separate antenna evidence panel and dedicated Antenna Observatory screen.
- Added derived GNSS export artifacts and export readback checks.
- Expanded GNSS correlation tests.

External build/device verification remains the next gate.
## 2026-09-24 — STOP Race / Import Repair Checkpoint

- Added `SessionLifecycleState` import to `ObservatoryService.kt`.
- Restored missing coroutine/domain/acquisition/storage imports from the source tree.
- Added serialized STOP handling via `stopMutex`.
- Added lifecycle re-check so duplicate/non-recording STOP intents are ignored.
- Preserved the existing drain + integrity-audit + close pipeline.
- Included latest real-device session evidence `c40cd3d7-f824-4e05-bfa3-95707110ea97` with 10961 observations and zero error events.
- Latest manual run reported no crash; slight end-of-run slowness remains under investigation.


## 2026-10-07 — First Device-Verified APK Release

Published the first public HORIZON Observatory APK as GitHub Release v1.0.0-observatory.

- Target commit: a6181fb6259f463e1314f5103d0bfd84ed7cf024
- Asset: Horizon-Observatory-v1.0.0-observatory.apk
- Device: Samsung SM-A075F / Android 16 / API 36
- APK SHA-256: 81CE8B59491525A7EC0DA91BDA3C5C0DBDEDDAAB310EC0FB93E311FE3F474A45
- GitHub-published asset digest independently matched the local APK digest.
- APK remains a Release Asset only; it is not committed to the repository.
- Release does not claim production status or final scientific validation.
- Open gates remain NORAD mapping, UTC edge cases, OrbitCore reference-vector accuracy, physical orientation, and K8 long-duration/interference validation.
