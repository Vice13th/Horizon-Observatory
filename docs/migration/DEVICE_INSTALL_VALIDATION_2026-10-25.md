# Device install validation — 2026-10-25

## Objective and scope
Install the current canonical `main` debug build on the connected Samsung SM-A075F and record repository, artifact, signature, package, installation, and startup evidence. This is a debug/device-smoke validation, not production release approval.

## Evidence
- **VERIFIED repository:** `main` at `57d177adaade025fd8d2a85809f08fd808a43d0b`; working tree was clean before creating this report. The report is recorded locally in documentation commit `ec1f97b09c8a5ab9429243c27e18727fff059c51`.
- **VERIFIED build:** `:app:assemblePrimaryDebug` succeeded using Gradle Wrapper 8.11.1 and JDK 17. Build time observed in local output: 17 seconds. Gradle emitted a deprecation warning recommending 8.14.4 for future Kotlin 2.5.0 compatibility, and an SDK XML version warning (SDK tool understood version 3, found version 4); neither stopped this build.
- **VERIFIED artifact:** `app/build/outputs/apk/primary/debug/app-primary-debug.apk`, length 10913290 bytes, SHA-256 `d43734d3758fa5ed6ffae5ba798f971600254e2243a5dadc3b1ccbf906da4eeb`.
- **VERIFIED metadata:** package `horizon.observatory.debug`; app label `Horizon Observatory`; versionCode `1`; versionName `1.0.0-observatory`; minSdk 26; targetSdk 36; compileSdk 36; launcher activity `horizon.observatory.ui.MainActivity`; native ABI reported by installed package `arm64-v8a`.
- **VERIFIED signature:** `apksigner verify --verbose --print-certs` returned `Verifies`; APK Signature Scheme v2 true. Debug signer certificate SHA-256: `1e14d3b1acb546e0caadb8504e9872fe5242909bfee6c45e4ba4abb4859db5e6`. This is a debug certificate, not a production release key.
- **VERIFIED device:** ADB serial `R8YY92YWAAF`; model `SM-A075F`; Android 16, API 36; `adb install -r` returned `Success`.
- **VERIFIED installed package:** `pm path horizon.observatory.debug` returned the installed `base.apk`; `dumpsys package` reported versionCode 1, versionName `1.0.0-observatory`, minSdk 26, targetSdk 36, installation/update time `2026-10-25 00:04:31` (device-local time).
- **VERIFIED launch smoke:** `adb shell monkey -p horizon.observatory.debug -c android.intent.category.LAUNCHER 1` injected one event successfully. Activity Manager showed `horizon.observatory.debug/horizon.observatory.ui.MainActivity` as `ResumedActivity`, and `pidof` returned PID `15918` at observation time.
- **OBSERVED logs:** The bounded post-launch log scan found no `FATAL EXCEPTION` marker for the app. A subsequent crash-buffer query returned no app exception entries. The broader log scan included standard AndroidRuntime startup logs from the `monkey` launcher process; these are not app crash evidence.

## Limits / not tested
- No release APK was installed; release APK variants are unsigned pending production signing.
- No destructive operation: existing app data was not cleared; no package uninstall, database reset, live GNSS capture, or RF transmission was performed.
- No full interactive UI walkthrough or scientific accuracy/ground-truth test was performed. Startup/resumed activity is only a launch smoke test.
- GitHub workflow/status API returned no status records for main HEAD `57d177adaade025fd8d2a85809f08fd808a43d0b` or the documentation-only validation commit `ec1f97b09c8a5ab9429243c27e18727fff059c51`; therefore CI for these exact heads remains **UNVERIFIED**. Prior PR-head CI passing is not substituted for these commits.
- Device battery was observed at approximately 11% during the install session; further endurance/GNSS tests should wait until charging is stable.

## Result
**PASS for build + APK signature/metadata + install + launcher startup smoke. PARTIAL for full acceptance:** production signing, CI on exact main HEAD, scientific/field validation, and broader interaction coverage remain open.

Generated at 2026-10-24T20:36:42Z.
