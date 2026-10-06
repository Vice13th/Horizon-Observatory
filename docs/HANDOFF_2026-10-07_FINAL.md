# Horizon Observatory — Final Handoff — 2026-10-07

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
