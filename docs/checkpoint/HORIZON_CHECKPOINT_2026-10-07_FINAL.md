# HORIZON CHECKPOINT — 2026-10-07 FINAL

## Repository
- Repository: Vice13th/Horizon-Observatory
- Branch: main
- Scope: accepted B3/B4 live-data/UI changes plus the ResilienceEngineTest nullability fix.

## Verified implementation
### Live observation path
- LiveObservationBus added as a post-persistence UI trigger.
- UI rereads authoritative bounded Room state on live signals.
- Room remains evidence source; the bus does not synthesize measurements.
- START recovery handles stale persisted RECORDING/STOPPING state.

### Satellite presentation
- Removed the presentation-layer `take(18)` cap from `observedSkyStateForUi()`.
- Current observed satellites with valid azimuth/elevation are rendered without the previous hard 18-item limit.
- Missing geometry remains excluded; no geometry is synthesized.
- Device verification reached 60 observed / 60 shown satellites.

### Startup branding
- AndroidX Core SplashScreen 1.2.0 integrated.
- Startup and post-splash themes coordinated with Horizon dark/teal visual identity.
- Dedicated Horizon splash vector and aligned launcher foreground added.
- Cold-start device capture verified.

### Resilience test correction
- `TrustedPvt.verticalUncertaintyM` remains nullable.
- `DeadReckoningBridge` preserves null when vertical uncertainty is absent.
- `ResilienceEngineTest` explicitly checks non-null fixture values before comparing uncertainty growth.
- No production measurement semantics were weakened or inferred.

## Verification receipts
- `:app:testPrimaryDebugUnitTest` PASS; exit code 0.
- `:app:assemblePrimaryDebug` PASS; exit code 0.
- APK installed successfully on Samsung SM-A075F / Android 16 / API 36.
- Final APK SHA-256: `81CE8B59491525A7EC0DA91BDA3C5C0DBDEDDAAB310EC0FB93E311FE3F474A45`
- App launch smoke check passed; no FATAL EXCEPTION / AndroidRuntime failure observed.

## Known warnings / non-blockers
- Gradle 8.11.1 deprecation warning for future Kotlin 2.5 compatibility; no build failure.
- Android SDK XML version mismatch warning; no build failure.
- Device has no rotation-vector sensor, so Horizon remains north-up and does not fake heading-up orientation.
- SGP4/SDP4 physical accuracy and long-run K8/interference validation remain outside this checkpoint.

## Evidence rules
- Raw observations unchanged.
- No synthetic satellite geometry added.
- No inferred missing measurement values.
- Provenance semantics preserved.
- Existing unrelated checkpoint/image/log artifacts were not staged.

## Next phase
1. Preserve this checkpoint as repository truth.
2. Continue GNSS observation-pipeline empirical validation.
3. Address remaining gates only with fresh evidence: export/readback reconciliation, satellite identity resolution, UTC/timestamp edge cases, and long-run/interference validation.
4. Keep propagation/scientific foundations frozen unless a new reproducible defect is demonstrated.
