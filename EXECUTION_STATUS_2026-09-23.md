# HORIZON — End-to-End Execution Status

## Contract
Based on `HORIZON — UNIVERSAL MASTER EXECUTION PROMPT`.

## Environment discovery
| Capability | Status | Evidence |
|---|---|---|
| Filesystem read/write/modify | AVAILABLE | Candidate source modified and archived in working environment |
| Kotlin compiler | AVAILABLE | Parser + pure-domain compiler executed |
| JDK | AVAILABLE | JDK 21.0.11 in execution environment |
| System Gradle | UNAVAILABLE | `gradle` not present in execution environment |
| Android SDK | UNAVAILABLE | `ANDROID_SDK_ROOT`/`ANDROID_HOME` unset; no `android.jar` found |
| ADB | UNAVAILABLE in execution environment | `adb` not installed in execution environment |
| Physical device | UNVERIFIED here | Device evidence exists only from user-provided Windows session |

## Source changes in this candidate
- Fixed `CancellationException` import in `ObservatoryService`.
- Changed `RawObservation.capabilityState` default from `SUPPORTED` to `UNKNOWN`.
- Added explicit capability/evidence epistemic states required by the master contract.
- Reworked session lifecycle to `IDLE -> READY -> RECORDING -> STOPPING -> COMPLETED` with `CRASH_CLOSED` recovery state.
- Added Room migration `3 -> 4` to normalize legacy `ACTIVE/CLOSED` sessions.
- Made monotonic integrity validation session-global by sequence order instead of per-provider.
- Made sensor source status reflect actual discovered sensor availability.
- Made cellular live callbacks conditional on `READ_PHONE_STATE` while retaining fine-location-based cell snapshots.
- Added a session-scoped SQLite export containing session + observations.
- Added ZIP readback verification for manifest, JSON, CSV, and SQLite counts/session identity.
- Hardened the Room-backed queue against deleting a pending row after a conflicting insert unless the evidence row is actually present.
- Removed unnecessary hard dependency on `READ_PHONE_STATE` for the base cell observation availability decision; the permission remains available for live callback registration.

## Verification receipts
### PASS
- Kotlin parser sweep across `app/src/main/kotlin`: `SYNTAX_FILES_WITH_ERRORS=0`
- Kotlin parser sweep across `app/src/test/kotlin`: `SYNTAX_FILES_WITH_ERRORS=0`
- Pure-domain runtime smoke: `DOMAIN_STATE_TIMESTAMP_SMOKE=PASS`
- Test-source compilation against local JUnit stubs: `TEST_SOURCE_COMPILE=PASS`

### UNVERIFIED
- Android Gradle `compileDebugKotlin`
- KAPT / Room code generation
- `testDebugUnitTest` under Android Gradle
- `assembleDebug`
- APK creation on Windows
- APK installation
- Application launch
- Android 16 runtime behavior
- Samsung SM-A075F runtime evidence
- Real GNSS raw measurement stream
- Real GNSS navigation messages
- Real antenna information
- Real cellular live callback behavior
- Real sensor inventory
- Long-run session durability/battery behavior
- Physical export/readback

## Known historical build receipts from the user's Windows environment
The earlier Android build reached Kotlin compilation and exposed real source errors in `MainActivity.kt`; those syntax errors have since been removed in this candidate. A separate missing `CancellationException` import was also fixed during source audit.

## Completion gate
The project is **NOT fully end-to-end verified** until the Windows build, APK install, physical SM-A075F runtime session, export, readback, and final artifact checks produce receipts.

## Next concrete gate
Run:

```powershell
.\\gradlew.bat clean :app:compileDebugKotlin --console=plain --stacktrace
```

Only after that passes:

```powershell
.\\gradlew.bat testDebugUnitTest assembleDebug --console=plain --stacktrace
```
