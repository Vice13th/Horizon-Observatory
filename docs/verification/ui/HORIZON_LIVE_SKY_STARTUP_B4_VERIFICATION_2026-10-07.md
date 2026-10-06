# Horizon Live Sky + Startup UI B4 Verification — 2026-10-07

## Satellite-count root cause

The visible satellite count was hard-capped in observedSkyStateForUi():

- domain.latestSatelliteEvidence
- filtered to valid azimuth/elevation
- sorted by C/N0
- take(18)
- converted to ObservedSkyState

ObservedSkyState.from() itself has no 18-item limit. The GNSS correlation engine also returns the full latest-per-satellite set. Therefore the 18 value was a presentation-layer truncation, not a receiver or correlation limit.

## Fix

Removed the take(18) presentation cap.

The panel now derives its count from the complete current ObservedSkyState.points list. The panel explicitly reports:

- observed valid-geometry satellites
- shown satellites

When all valid observed points are rendered, these values are equal. Satellites without valid azimuth/elevation remain excluded and are reported separately; no position is synthesized.

## Device verification

Device: Samsung SM-A075F / Android 16 / API 36.

Installed APK SHA-256:
FC66EBA0E34A296D84BF60EBBF20573826A398A26B02AA7556EC18D79B5527FA

Verified on-device:

- Overview showed 60 latest satellites.
- Overview satellite panel showed 60 OBSERVED · 60 SHOWN.
- GNSS screen showed latest satellite evidence · correlated=60.
- GNSS Live Sky showed 60 latest satellites.
- No take(18) or 18 latest satellites remains in Kotlin source.
- The displayed count is therefore no longer locked to 18 and is sourced from the live correlated observed state.

## Startup / logo integration

Migrated the launcher startup experience to AndroidX Core SplashScreen:

- androidx.core:core-splashscreen:1.2.0
- launcher Activity calls installSplashScreen() before super.onCreate()
- manifest starts with Theme.Horizon.Starting
- splash background uses Horizon #061013
- splash icon uses a dedicated vector horizon_splash_icon
- post-splash Activity theme is Theme.Horizon
- launcher foreground was aligned to the same Horizon aperture/observer mark and semantic teal palette.

Cold-start capture on the device produced:

- 720x1600 frame
- approximately 96.3% exact Horizon background pixels (#061013)
- 25,463 pixels matching the cyan/teal logo color range
- center pixel #061013

This verifies that the cold-start frame used the coordinated dark Horizon background and visible teal/cyan branding.

## Build

:app:assemblePrimaryDebug — PASS.

Gradle emitted existing environment warnings about Gradle 8.11.1 being deprecated for future Kotlin 2.5 and an SDK XML version mismatch; these did not block the build.

## Unit tests

:app:testPrimaryDebugUnitTest — FAIL, unchanged pre-existing compilation error:

app/src/test/kotlin/horizon/observatory/resilience/ResilienceEngineTest.kt:68

The failure is a nullable Double? used where a non-null Double is required. No test source was modified.

## Integrity

- Raw GNSS observations were not rewritten.
- No synthetic satellite was added.
- No missing azimuth/elevation was inferred.
- Satellite identities remain the observed constellation/SVID evidence.
- Existing unrelated untracked checkpoint/image files were not added or modified.
- No secrets/credentials were found by the final diff scan.
- No commit was created.
