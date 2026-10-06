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

## Fresh Device Verification Addendum � 2026-10-07

- Repository: `main` at `3ed4f007e5c21a561d0449e1a55003f93b92396b`, matching `origin/main`.
- Unit test `:app:testPrimaryDebugUnitTest`: PASS, exit 0.
- Build `:app:assemblePrimaryDebug`: PASS, exit 0.
- APK SHA-256: `81CE8B59491525A7EC0DA91BDA3C5C0DBDEDDAAB310EC0FB93E311FE3F474A45`.
- Device: Samsung SM-A075F / Android 16 / API 36.
- Fresh recording: IDLE ? RECORDING/ACTIVE ? STOP ? COMPLETED/IDLE.
- Fresh live UI: 0 latest satellites shortly after start ? 43 latest satellites during the same active session.
- Completed session: 2341 persisted observations reported by Room runtime diagnostics; completed-state update succeeded.
- Crash scan: no material application crash signatures observed.
- Orientation: rotation-vector unavailable; physical heading remains unverified and UI remains truthful north-up.
- Sequence advancement: UNVERIFIED.
- Fresh SVID/constellation extraction: UNVERIFIED.
- Fresh Doppler/pseudorange-rate extraction: UNVERIFIED.
- Export/readback checksum reconciliation: UNVERIFIED.
- NORAD, UTC, OrbitCore, physical orientation, and K8 remain open scientific gates.


## Evidence-Closure Addendum — 2026-10-07

The remaining device evidence gaps were closed using the existing completed session `7f9066a6-17c6-4307-ac48-4274379742ec` and existing `ExportEngine`; no production/source/scientific behavior was changed.

- Export SUCCESS: `2341` observations.
- Counts reconciled: Room/runtime `2341`; manifest `2341`; `observations.json` `2341`; `observations.csv` `2341`.
- Sequence range `1..2341`; manifest and direct exported-JSON audit both show contiguous sequence numbers.
- Direct sequence advancement evidence: `394 → 448 → 485` in the same persisted session.
- Direct ingestion timestamp ordering audit: non-decreasing across all `2341` observations.
- Archive checksum: `7CFEC70335EA78788109DB6E64316E0A5D1E8F0024C645CC328633AD73D5556C`.
- Independent second ADB readback produced the identical archive SHA-256; checksum MATCH.
- Archive internal checksum verification: `13` entries, `0` mismatches.
- Fresh raw GNSS measurement evidence: `136` rows; observed Android `constellationType` values `1,3,5,6`.
- Fresh SVID evidence: `constellationType=1, svid=10` observed at sequences `394`, `448`, `485`, `529`, `570`.
- Fresh raw pseudorange-rate evidence: observed values include `-570.5701293676533`, `-571.730778755728`, and `-571.7120009486173` m/s. No Doppler estimate was substituted.
- Satellite evidence CSV: `182` rows; observed `constellationType` values `1,2,3,5,6`.
- Source timestamp regressions remain diagnostic (`262`); ingestion ordering remains clean.

Evidence status: SEQUENCE VERIFIED; SVID VERIFIED; CONSTELLATION VERIFIED as observed Android `constellationType`; PSEUDORANGE-RATE VERIFIED as raw observed field; EXPORT/READBACK/CHECKSUM VERIFIED.

NORAD, UTC edge cases, OrbitCore reference accuracy, physical heading/orientation, and K8 remain open scientific gates.


## Visual Device Evidence Addendum — 2026-10-07

A real physical-device screenshot was captured from the verified Samsung SM-A075F / Android 16 / API 36 while HORIZON Observatory was the resumed foreground activity.

- Capture state: completed OBSERVED SKY session; session control reported COMPLETED / IDLE.
- UI evidence: OBSERVATION BUS reported OBSERVED 58; OBSERVED SKY reported 58 latest satellites with correlated observed azimuth/elevation.
- Orientation text in the captured UI explicitly states north-up recorded data and that live orientation was not applied; no physical-heading claim is made.
- Raw device capture timestamp: 2026-10-07 03:04:29 +0330.
- Hero image: `docs/assets/horizon-observatory-device-verified.png`.
- Image dimensions: 720 × 1446 pixels; the crop removes only the Android status/navigation chrome and does not alter application content.
- Hero image SHA-256: `A6BE2027F07881245F8545A0C9FA067EB832050A2C397B66B435109A4751AFD5`.
- The screenshot was taken directly from the running application via the authorized physical-device ADB framebuffer capture path; no generated or synthetic UI/data was used.
- No scientific values, satellite identities, geometry, orientation claims, or telemetry were edited.

This visual evidence is documentation-only and does not close NORAD, UTC, OrbitCore reference-vector, physical orientation, or K8 scientific gates.
