# Verification and Build Report

Evidence cut: 2026-10-18. This is a documentation refresh; no new APK build or test execution is claimed by this refresh.

## Latest recorded release-tree gate
- JVM command: :app:testParallelDebugUnitTest --rerun-tasks --no-daemon; recorded pass.
- Test XML: 52 suites, 352 tests, 0 failures, 0 errors, 3 skipped. Three skipped methods are SGP4 reference-vector tests; skipped is not pass.
- APK assembly: :app:assembleParallelDebug; recorded BUILD SUCCESSFUL.
- Candidate APK: artifacts/release/Horizon-Parallel-debug-20261018.apk, 10900108 bytes, SHA-256 EE8204D1BD0D56FAD00C3C4D4D659AE5FDDB7FE4164A8E4189D0CFA29A00B4FE.
- Signer certificate SHA-256: 1e14d3b1acb546e0caadb8504e9872fe5242909bfee6c45e4ba4abb4859db5e6.
- Prior device verification: Samsung SM-A075F / Android 16 / API 36; installed base APK was pulled back and matched candidate hash. Cold launch and Export Center were observed during that prior gate.
- Current read-only ADB snapshot: serial [device serial redacted] connected; exit-info records PID 16209 exiting LOW_MEMORY at 2026-10-18 23:44:51 device-local time (RSS 106 MB); subsequent pidof returned no app PID and Android launcher was top-resumed. No relaunch, reinstall, data clear or session inspection was performed.

## Persisted-session export
- One persisted session exported successfully: 93,754 observations.
- JSON count: 93,754; CSV count: 93,754; CSV sequences 1..93,754 with zero gaps.
- Ingestion timestamps are non-decreasing; source timestamps have 44,938 regressions (~47.93%). Source-clock chronology/root cause remains unresolved.
- Embedded SQLite integrity ok, one session row, 93,754 distinct observations.
- ZIP: 14 entries; CRC passes; 13 embedded SHA-256 checks pass.
- Private export ZIP: 23107090 bytes, SHA-256 f5f9af0876d6cb5a0058afe911d5c5824d13a3c6e7f2d8c9c60484aef7fe48e2. Excluded from Git and integrated archive.
- Memory sampling: 14 periodic dumpsys meminfo samples; largest observed Java heap 229,972 KiB, PSS 356,519 KiB. These are sampled values, not profiler-measured peak memory. Prior LOW_MEMORY exits remain unexplained.

## Warnings and unpassed gates
The recorded build used Gradle 8.11.1; a future Kotlin Gradle Plugin 2.5.0 upgrade warning recommends Gradle 8.14.4. SDK metadata v4 was encountered by a parser that understands through v3. These warnings did not block the recorded build; clean-machine reproducibility and warning cleanup remain open.
No fresh connected instrumentation run in the latest gate. Room v5/v6 migration methods remain skipped because schema baselines are missing. Three SGP4 reference-vector methods remain skipped. No outdoor ground-truth GNSS or controlled interference/multipath campaign was completed. Time-valid SVID-to-NORAD mapping and propagation accuracy were not certified. Parallel flavor omits the OrbitCore bridge; operational SGP4/SDP4 prediction is not claimed. Device orientation sensors were reported unavailable. SAF folder/save and clean-machine reproducibility remain unverified.

Conclusion: release build and tested-session export integrity are verified at the stated scope. The product remains an engineering / field-test candidate, not scientifically release-closed or production-certified.

## Supplemental verification checkpoint - 2026-10-22

**Scope:** Fresh local validation of the current main checkout at baseline commit 84ba5b728a323dcdc6bd2dd8ab1e77daa329c118. This checkpoint is additive; the historical 2026-10-18 receipt above is not overwritten.

- **VERIFIED:** Command .\gradlew.bat :app:testPrimaryDebugUnitTest :app:assemblePrimaryDebug --no-daemon completed with exit code 0 (BUILD SUCCESSFUL, 46 actionable tasks).
- **MEASURED:** JUnit XML aggregate: 352 tests, 0 failures, 0 errors, 3 skipped, across 52 XML suites.
- **MEASURED:** PRE-change APK `app/build/outputs/apk/primary/debug/app-primary-debug.apk`; bytes 10864138; SHA-256 `2F9B3EE4A7C3BBD99D80251DFF0F80C8D514B4DF6ED1F5D5EC69E95E63627E52`. This artifact predates the timestamp-source edits.
- **OBSERVED warnings:** Gradle 8.11.1 is deprecated for future Kotlin Gradle Plugin releases; Android SDK tooling reported it understands SDK XML through v3 while encountering v4; source compilation reported deprecated GNSS AGC/carrier-phase API calls and several Kotlin nullability/exhaustiveness warnings. No compile/test/build error was reported.
- **NOT VERIFIED:** installation, runtime behavior, GNSS/AGC event attribution on hardware, screenshots, accessibility/visual appearance, long-duration capture, and scientific ground-truth performance. This was a build/test run only.
- **Reproduction context:** Windows PowerShell on authorized remote device Hive; project root source/Horizon-Parallel; variant primaryDebug.

## Supplemental target-checkout verification - 2026-10-22 (after timestamp-source change)

**Scope:** Correct checkout `C:ProjectsHorizonparallell-worksourceHorizon-Parallel`; build only. Historical 2026-10-18 evidence above remains unchanged.

- **VERIFIED:** `.\gradlew.bat :app:testPrimaryDebugUnitTest :app:assemblePrimaryDebug --no-daemon` completed with exit 0 and `BUILD SUCCESSFUL` (46 actionable Gradle tasks; 5 executed, 41 up-to-date).
- **MEASURED:** 53 XML suites, 356 tests, 0 failures, 0 errors, 3 skipped.
- **MEASURED:** current APK `app/build/outputs/apk/primary/debug/app-primary-debug.apk`; 10,908,390 bytes; SHA-256 `A2D3E864B2E0CF8898728427E6587721E3B1AB8E9DCBA1B05CB1967726F85D86`.
- **VERIFIED:** new `GnssEventTimestampTest` has four passing tests. It checks API 26/28 callback-time fallback, API 29 unavailable receiver-clock field, and valid API 29 receiver-clock use.
- **CHANGE:** measurement callback now guards API-29+ receiver elapsed-realtime methods and persists `eventTimestampSource`. Callback-receipt monotonic time is explicitly distinguishable from receiver-reported measurement time.
- **VERIFIED:** `git diff --check` exit 0; Git emitted LF/CRLF normalization warnings only.
- **OBSERVED warnings:** Gradle 8.11.1 future Kotlin plugin compatibility warning; SDK metadata v4/tooling parser mismatch; deprecated per-measurement AGC and carrier-phase APIs remain.
- **NOT VERIFIED:** no install, fresh device session, screenshots, visual/accessibility review, API 26â€“28 actual-runtime test, Room/export round trip, endurance test or calibrated RF test.
- **Conclusion:** software test/build gate passes at the stated scope. Device compatibility and scientific/production validation remain open; no detector-performance claim is implied.
