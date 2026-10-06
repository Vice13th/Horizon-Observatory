# HORIZON OBSERVATORY — TEST BUILD (orbit prediction foundation)

**LABEL: TEST / CANDIDATE. NOT A STABLE CHECKPOINT. NOT RELEASE-READY.**
Base: `HorizonObservatory_FINAL_MERGED__4_.zip` + orbit-prediction foundation. Full source in this archive.

Authoring status: **BUILD UNVERIFIED. UNIT TESTS NOT EXECUTED. DEVICE UNVERIFIED.**
The authoring environment had no JDK 17 / Gradle / Android SDK / network. Compile errors are possible.
Full architecture and status table: `docs/ORBIT_PREDICTION_ARCHITECTURE.md`.

## What you are testing
Also in this build: live device pose -> orientation-aware OBSERVED sky plot (presentation only; `docs/ORBIT_PREDICTION_ARCHITECTURE.md`).
Orbit chain: observed SVID -> identity resolver -> Room catalog (v7) -> propagation boundary -> TEME/ECEF/ENU -> az/el -> prediction layer state.
Expected runtime behaviour right now: **every prediction ends UNMATCHED or ENGINE_UNAVAILABLE** (empty identity table, no SGP4/SDP4 backend). That is correct, not a bug.
The observed layer (`SatelliteEvidence`, `CorrelatedSkyPlot`, `MainActivity`) is unchanged.

## Test steps (Windows PowerShell, repo root)

### 0. Wrapper (JAR is missing from every archive so far)
```powershell
gradle --version                      # must say 8.11.1
gradle wrapper --gradle-version 8.11.1 --gradle-distribution-sha256-sum f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6
Get-FileHash gradle\wrapper\gradle-wrapper.jar -Algorithm SHA256
.\gradlew.bat --version
```
Keep the pinned `distributionSha256Sum` above; do not remove or edit it.

### 1. Unit tests (JVM)
```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain --stacktrace
```
Report: compile errors (if any) and the pass/fail/skip counts. `Sgp4ReferenceVectorTest` is @Ignore (pending) by design.

### 2. Build both variants
```powershell
.\gradlew.bat :app:assemblePrimaryDebug :app:assembleTsDebug --stacktrace
```
Check: both APKs exist; confirm the build also exported `app\schemas\horizon.observatory.storage.room.AppDatabase\7.json` (kapt schema export check).

### 3. Schema baselines (needed before migration tests mean anything)
Do not hand-write them. Build MERGED3 (Room v5) and MERGED__4_ (Room v6) with schema export, commit their `5.json` and `6.json` next to `7.json`. Then:
```powershell
.\gradlew.bat :app:connectedPrimaryDebugAndroidTest --console=plain
```
`OrbitCatalogMigrationTest` SKIPS if a baseline is missing. A skip is NOT a pass.

### 4. Device smoke (optional)
```powershell
adb devices
adb install -r app\build\outputs\apk\primary\debug\*.apk
adb shell am start -n horizon.observatory.debug/.<launcher activity>   # adjust to your applicationId
adb logcat -d | Select-String "horizon"
```
Verify: app starts, a GNSS session records and STOPs as before, no crash from Room v6->v7 migration, observed sky plot unchanged.

### 5. Live sky plot check (on device, active session)
GNSS tab, session RECORDING, phone held roughly flat with a location fix:
- Orientation line should read `device-top-up` and the plot (N/E/S/W labels) should rotate as you turn; hold the phone near-vertical -> it must fall back to `north-up` with a reason, never freeze at a made-up heading.
- No fix yet -> `north-up ... declination unavailable`. Stop the session -> `north-up ... recorded data`.
- Compare the rotated N label with a trusted compass; report any offset (declination/magnetic interference).

## What to send back
1. `gradlew --version` output + wrapper JAR SHA-256.
2. Full tail of `testDebugUnitTest` (errors or counts).
3. Result of `assemblePrimaryDebug` / `assembleTsDebug`.
4. Whether `7.json` appeared in `app\schemas\...`.
5. Anything unexpected on device.

## Known open items (not regressions)
- No SGP4/SDP4 backend; no Vallado reference vectors imported (`app/src/test/resources/sgp4/README.md`).
- Identity table empty; catalog never fetched automatically; sensor orientation = Unavailable.
- Historical hash manifests are untouched and still do not match this source (reconciliation UNRESOLVED);
  this archive's own record: `docs/checkpoint/ORBIT_ARCHITECTURE_CHANGESET_2026-09-30.json`.
- `docs/checkpoint/orbit_foundation.patch` = diff against MERGED__4_ (applies cleanly, dry run).
