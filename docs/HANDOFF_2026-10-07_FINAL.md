# Horizon Observatory â€” Final Handoff â€” 2026-10-07

## Goal
Preserve the verified Horizon Observatory state after the accepted B3/B4 live-data/UI changes and the ResilienceEngineTest fix so the next session can continue from repository truth without reopening closed scientific boundaries.

## Decisions
- Room/persisted ObservationEntity remains authoritative evidence; LiveObservationBus is only a UI trigger.
- The previous 18-satellite UI limit was presentation truncation and has been removed.
- Satellites without valid azimuth/elevation remain unplotted rather than receiving synthetic positions.
- Startup branding uses the same Horizon dark/teal visual language as the main instrument UI.
- Nullable vertical uncertainty remains nullable in production and the test explicitly asserts its presence for the chosen fixture.
- No orientation-aware claim is made when the device lacks a rotation-vector sensor.

## Verified
- `:app:testPrimaryDebugUnitTest` PASS.
- `:app:assemblePrimaryDebug` PASS.
- APK installed on Samsung SM-A075F / Android 16 / API 36.
- Launch smoke check passed.
- APK SHA-256: `81CE8B59491525A7EC0DA91BDA3C5C0DBDEDDAAB310EC0FB93E311FE3F474A45`.
- Device observed 60 latest satellites in the accepted B4 verification run.

## Open questions / next work
- Complete any still-open export/readback evidence reconciliation.
- Obtain authoritative SVID-to-NORAD mapping evidence before identity claims.
- Exercise UTC/timestamp edge cases.
- Run long-duration K8/interference validation.
- Keep propagation foundations frozen unless new reproducible evidence requires change.

## Touched artifacts
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/kotlin/horizon/observatory/acquisition/service/ObservatoryService.kt`
- `app/src/main/kotlin/horizon/observatory/core/di/HorizonContainer.kt`
- `app/src/main/kotlin/horizon/observatory/storage/queue/ObservationPersistenceQueue.kt`
- `app/src/main/kotlin/horizon/observatory/ui/MainActivity.kt`
- `app/src/main/kotlin/horizon/observatory/live/LiveObservationBus.kt`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/drawable/horizon_splash_icon.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/test/kotlin/horizon/observatory/resilience/ResilienceEngineTest.kt`
- `gradle/libs.versions.toml`
- `docs/verification/ui/HORIZON_LIVE_DATA_B3_VERIFICATION_2026-10-07.md`
- `docs/verification/ui/HORIZON_LIVE_SKY_STARTUP_B4_VERIFICATION_2026-10-07.md`
- `docs/HORIZON_MASTER_ROADMAP.md`
- `docs/checkpoint/HORIZON_CHECKPOINT_2026-10-07_FINAL.md`
- `docs/HANDOFF_2026-10-07_FINAL.md`

## Fresh Verification Addendum — 2026-10-07 02:10–02:14 local device time

### Repository / artifact
- Canonical HEAD and origin/main remained `3ed4f007e5c21a561d0449e1a55003f93b92396b`.
- `:app:testPrimaryDebugUnitTest` completed `BUILD SUCCESSFUL`, exit 0.
- `:app:assemblePrimaryDebug` completed `BUILD SUCCESSFUL`, exit 0.
- Primary APK SHA-256 independently re-computed as `81CE8B59491525A7EC0DA91BDA3C5C0DBDEDDAAB310EC0FB93E311FE3F474A45`.
- Installed package reports versionName `1.0.0-observatory`, versionCode `1`.

### Fresh real-device evidence
- Target remained Samsung SM-A075F / Android 16 / API 36.
- Application launched with `MainActivity` resumed and no material application crash signatures in the inspected logcat window.
- A new active recording session was started from IDLE. UI entered `RECORDING` / `ACTIVE` and displayed `LIVE / RECORDING`.
- Fresh live UI changed from `0 latest satellites` shortly after start to `43 latest satellites` during the same active session. This is direct fresh live-state change; it is not animation evidence.
- The completed session then reported `56 latest satellites` in the persisted/observed sky view and returned to `COMPLETED` / `IDLE`.
- Android Room diagnostics for that completed session reported `2341` persisted observations via `SELECT * FROM observations WHERE sessionId = ? ORDER BY sequenceNumber ASC`; the same runtime issued a successful `SELECT COUNT(*) FROM observations WHERE sessionId = ?`.
- Stop path executed `UPDATE sessions SET endTimeMs = ?, lifecycleState = 'COMPLETED' ...` successfully.
- No FATAL EXCEPTION, AndroidRuntime, OutOfMemoryError, SecurityException, SQLiteException, or IllegalStateException was observed in the inspected crash log.
- Rotation-vector sensor remains unavailable; UI continues to state that live orientation is unavailable and uses north-up rather than claiming physical heading.

### Evidence gaps intentionally preserved
- Direct `t1 sequence=N` ? `t2 sequence=M` evidence was not captured; sequence advancement remains UNVERIFIED.
- Fresh runtime SVID/constellation samples were not independently extracted from the evidence path; remain UNVERIFIED.
- Fresh Doppler/pseudorange-rate values were not independently extracted; remain UNVERIFIED.
- Export/readback checksum reconciliation was not completed; persistence count is measured from Room runtime diagnostics only.
- NORAD, UTC edge cases, OrbitCore reference-vector accuracy, physical orientation, and K8 validation remain open.

### Integrity
- No production code, tests, Gradle/configuration, scientific state, raw evidence, or user artifacts were modified for this verification run.
- This addendum records only newly observed evidence; historical receipts remain unchanged.
