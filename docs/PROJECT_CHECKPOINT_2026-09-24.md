# HORIZON — Engineering Checkpoint

Date: 2026-09-24
Target: Samsung Galaxy A07 / SM-A075F
Android: 16 / API 36
State: **CHECKPOINT — NEXT PHASE ACQUISITION VERIFIED ON PHYSICAL DEVICE; CORRELATION LAYER NEXT**

## Purpose

This archive freezes the HORIZON source state after the Next Phase changes and the compile-fix that was required immediately afterward. It also preserves the two most recent real-device session exports as evidence.

This is a checkpoint, not a final release claim.

## Build evidence

The project owner executed:

```powershell
.\\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
```

Result: **SUCCESS** after fixing `SessionAnalysisEngine.kt` (`latestStatus` reference mismatch).

The checkpoint does not claim a fresh APK build from this archive because no APK was supplied as a checkpoint input and the current evidence requirement is source/build reproducibility.

## Next Phase implementation state

Applied source changes:

- `ui/MainActivity.kt`
  - GNSS observatory additions for multi-satellite matrix, sky view, C/N0 history, AGC history and navigation-message evidence.
  - Additional cellular freshness/trigger presentation.
- `acquisition/cellular/CellInfoSource.kt`
  - Fresh cell-info request path and acquisition provenance metadata.
- `acquisition/gnss/GnssObservationSource.kt`
  - GNSS status/navigation-message/raw measurement acquisition updates.
- `analysis/SessionAnalysisEngine.kt`
  - AGC classification, multi-satellite analysis, navigation-message analysis, serving-cell preference and cellular freshness metrics.
- `core/capability/CapabilityScanner.kt`
  - Runtime capability reporting used by the observatory.
- `test/.../SessionAnalysisEngineTest.kt`
  - Tests for constant AGC handling and serving-cell RSRP preference.

The compile-fix applied after the first Next Phase compile attempt is preserved as:

`docs/patches/HORIZON_NEXT_PHASE_FIX1.patch`

## Prior engineering foundations retained

The checkpoint retains the previously implemented foundations, including:

- explicit session state machine
- session-scoped sequencing
- separate source and ingestion timestamps
- ingestion monotonic integrity clock
- persistence queue
- domain/entity separation
- capability and provenance metadata
- Room persistence
- integrity audit
- verified export package generation and read-back checks
- runtime device/capability discovery
- GNSS, cellular and sensor acquisition
- physical-device verification workflow

## Evidence summary

Two real exported sessions are included under `evidence/sessions/`.

### Session 35f8d344-5d1b-46aa-a695-5112d50eec13

- 2,395 observations
- 207 GNSS raw measurements
- 38 GNSS status records
- 14 navigation-message records
- 21 GNSS fixes
- 27 cellular records
- elevation/azimuth entries: 2,057 / 2,057
- AGC samples: 207
- AGC observed: 6.0 dB, exactly constant
- C/N0: 13.731–35.0 dB-Hz; mean ≈ 20.819 dB-Hz
- cellular acquisition triggers: 18 `REQUEST_CELL_INFO_UPDATE`, 9 `TELEPHONY_CALLBACK`
- cellular source age: 7–5014 ms
- sequence 1..2395 contiguous
- ingestion timestamps non-decreasing
- integrity clean
- 246 source-timestamp regressions retained as source-time evidence
- zero error events

### Session f860845d-79cf-4e7d-b951-c00e0b237fe2

- 3,523 observations
- 129 GNSS raw measurements
- 60 GNSS status records
- 0 navigation-message records
- 1 GNSS fix
- 3 cellular records
- elevation/azimuth entries: 2,277 / 2,277
- AGC samples: 129
- AGC observed: 6.0 dB, exactly constant
- C/N0: 14.4–26.6 dB-Hz; mean ≈ 22.675 dB-Hz
- sequence 1..3523 contiguous
- ingestion timestamps non-decreasing
- integrity clean
- 174 source-timestamp regressions retained as source-time evidence
- zero error events

## Device capability evidence

From the session manifests:

- Samsung SM-A075F
- Android 16 / API 36
- raw GNSS measurements: supported
- navigation messages: supported
- accumulated delta range: supported
- multi-frequency: supported
- antenna info: unsupported
- carrier phase: unverified
- AGC capability: unverified in the capability snapshot, while real session observations contain a constant 6.0 dB AGC value
- cellular fresh-update API: supported

## Scientific/engineering interpretation boundary

This checkpoint does **not** model the physical antenna and does not infer antenna gain, radiation pattern, phase center, RF attenuation, jamming or spoofing from these sessions alone.

The next architectural step is correlation of independent GNSS evidence streams using a key based on constellation, SVID and carrier/frequency where available.

## Next step

Implement and test the GNSS correlation layer:

`GNSS_STATUS + GNSS_RAW_MEASUREMENT + NAVIGATION_MESSAGE -> unified satellite evidence view`

Then expose:

- C/N0 vs time
- C/N0 vs elevation
- per-satellite history
- sky plot
- AGC vs time
- navigation-message stream

No synthetic values are permitted.
