# POST-MIGRATION REGRESSION RECEIPT ΓÇö 2026-10-06

**Milestone:** Phase A / Post-Kotlin-2.4 + Room/KSP migration regression
**Device:** Hive / Samsung SM-A075F / R8YY92YWAAF
**Package:** horizon.observatory.debug
**Session:** `fa2c48ec-15e6-40c1-a892-c170bc27cd04`

## Build / install ΓÇö VERIFIED

Gate:
- `:app:compilePrimaryDebugKotlin`
- `:app:testPrimaryDebugUnitTest`
- `:app:testT1DebugUnitTest`
- `:orbitcore-bridge:testDebugUnitTest`
- `:app:assemblePrimaryDebug`

Result: EXIT=0, BUILD SUCCESSFUL.

Primary APK:
- size: 10,400,849 bytes
- SHA-256: `1D0788E88CB59917BD7EA7561229C66707C94DF3C40CA9D40719FEC28D69DA6C`

Installed APK:
- size: 10,400,849 bytes
- SHA-256: `1D0788E88CB59917BD7EA7561229C66707C94DF3C40CA9D40719FEC28D69DA6C`
- built/installed MATCH=true

## Real recording ΓÇö VERIFIED

UI start path was used through the actual application.

HorizonHealth:
- 12:42:49 ΓÇö 1 observation
- 12:42:54 ΓÇö 218
- 12:42:59 ΓÇö 455
- 12:43:04 ΓÇö 679
- 12:43:09 ΓÇö 932
- 12:43:14 ΓÇö 1238
- 12:43:19 ΓÇö 1503
- 12:43:24 ΓÇö 1797
- 12:43:29 ΓÇö 2076
- 12:43:34 ΓÇö 2373

Final:
- 12:43:39 ΓÇö Session COMPLETED
- observations: 2759
- GNSS: AVAILABLE
- cellular: AVAILABLE
- acquisitionRunning: true during recording

## Export regression failure ΓÇö FAILED, preserved as provenance

Before the fix, export failed at 12:44:34:

`Unable to create exports directory: /storage/emulated/0/Android/data/horizon.observatory.debug/files/exports`

Root cause: `File.mkdirs()` returns false when the target directory already exists, but the implementation treated that normal state as failure.

## Export fix ΓÇö VERIFIED

Changed `ExportEngine.kt` to create the directory only when absent and explicitly verify that the resulting path is a directory:

```kotlin
val exportsDir = File(exportsBase, "exports").apply {
    if (!exists()) {
        check(mkdirs()) { "Unable to create exports directory: $this" }
    }
    check(isDirectory) { "Exports path is not a directory: $this" }
}
```

## Export #1 after fix ΓÇö VERIFIED

Runtime:
- 12:47:47 ΓÇö Export SUCCESS
- observations: 2759

ZIP:
- size: 542,501 bytes
- SHA-256: `5BF287B5C4393A5CADA9846135E8DB4B238D07911774A08B7B9E90FE3BC77DB7`
- extracted successfully
- internal checksums: TRUE

Integrity:
- observationCount=2759
- sequence 1..2759
- sequenceContiguous=true
- ingestionTimestampsNonDecreasing=true
- ingestionTimestampedObservationCount=2759
- sourceTimestampRegressions=316
- isClean=true
- system events=2
- errors=0
- schemaVersion=5

## Export #2 after fix ΓÇö VERIFIED

Runtime:
- 12:50:28 ΓÇö Export SUCCESS
- observations: 2759

ZIP:
- size: 542,500 bytes
- SHA-256: `53EB52446004DC7EC82B0B4F4EF769CCB20F332FC71DC5DB6C71B2369DB36214`
- device SHA-256 matched pulled ZIP SHA-256
- extracted successfully
- internal checksums: TRUE

Integrity:
- observationCount=2759
- sequence 1..2759
- sequenceContiguous=true
- ingestionTimestampsNonDecreasing=true
- ingestionTimestampedObservationCount=2759
- sourceTimestampRegressions=316
- isClean=true
- system events=2
- errors=0

Export #1/#2 comparison:
- observations identical
- sequence range identical
- system events identical
- errors identical
- integrity state identical
- stable payload files were byte-identical
- `dataset_manifest.json` and `checksums.sha256` differed only because export-time metadata/checksums changed

## Capability evidence ΓÇö OBSERVED

Session manifest:
- Android 16 / API 36
- fine/coarse location GRANTED
- READ_PHONE_STATE GRANTED
- notifications GRANTED
- raw GNSS measurements SUPPORTED
- navigation messages SUPPORTED
- accumulated delta range SUPPORTED
- multi-frequency SUPPORTED
- antenna info UNSUPPORTED
- carrier phase UNVERIFIED
- automatic gain control UNVERIFIED
- rotation vector UNAVAILABLE
- gyroscope UNAVAILABLE
- magnetometer UNAVAILABLE

## Phase A conclusion

**VERIFIED:** post-migration build, install, real recording, GNSS availability, persistence, session completion, export #1, export #2, archive integrity, internal checksums, sequence continuity, ingress timestamp monotonicity, installed APK integrity.

**UNVERIFIED:** physical orientation/panel rotation, satellite-panel redesign, scientific OrbitCore reference vectors.

**OBSERVED diagnostic:** 316 source-timestamp regressions in this session. These do not fail the export integrity gate and remain explicitly documented.
