# Horizon Parallel - Outdoor Session Capture & Acceptance Protocol

**Use this document during the next real outdoor recording session.** It is independent of the UI changelog and technical architecture reference. Its purpose is to state what the application should do, what evidence to collect, and how to distinguish a product defect from an unavailable platform capability or a scientifically unverified result.

**Application:** Horizon Parallel
**Package:** horizon.observatory.parallel.debug
**Target device used in the current verification:** Samsung SM-A075F / Android 16 / API 36
**Test class:** real-device, real-world GNSS acquisition; not emulator-only or mocked input
**Status before field run:** cold launch and UI navigation are verified; an outdoor GNSS session has not been verified by this release build. The Parallel flavor also lacks the runtime OrbitCore bridge; predicted orbit requests may report `ENGINE_UNAVAILABLE` independently of GNSS measurement arrival.

## 1. Evidence policy

The field run must preserve real platform evidence. Do not inject coordinates, manufacture satellite rows, force a status flag to make the screen appear populated, or interpret model predictions as receiver observations.

Use the following evidence labels when recording results:

- **OBSERVED:** directly exposed by the phone/platform and present in an observation row or capability report.
- **DERIVED:** computed deterministically from recorded observations, such as a summary or correlation.
- **DERIVED_PREDICTED:** generated from an orbit catalog and propagation model, not measured by the receiver.
- **DEAD_RECKONED:** projected from a previous trusted position/velocity under the declared continuity method and its uncertainty limits.
- **UNKNOWN / UNAVAILABLE / NOT REPORTED:** no trustworthy value is available. This is a valid result, not a numeric zero.
- **UNVERIFIED:** the available data or tests have not established the behavior or scientific accuracy.

Do not claim PASS from a screenshot alone. Use exported observations, report manifests and checksums for data-integrity checks.

## 2. Pre-flight checklist

Complete this before starting the recording:

- [ ] Confirm the installed application label is **Horizon Parallel** and package is **horizon.observatory.parallel.debug**.
- [ ] Confirm device is the intended Samsung SM-A075F and note Android release/API level from the System screen.
- [ ] Charge the phone sufficiently for the whole run and close unrelated GNSS/navigation apps that may compete for chipset use.
- [ ] Go to an open-sky location with a safe stationary observation point. Record whether buildings, tree canopy, metal roofing, vehicles, or other obstruction surrounds the phone.
- [ ] Enable Android Location/GPS.
- [ ] In **Overview -> PERMS**, grant Fine Location (keep Coarse Location as the app requests) and Read Phone State if you want cellular observations. Grant notification permission if prompted.
- [ ] Re-open **System & Capabilities** after permission approval. Record each permission's actual granted/denied state; do not infer permission state from the presence of a manifest declaration.
- [ ] Capture the initial System screen and capability report. Capture the current app version/APK hash from the build receipt or package manifest.
- [ ] Confirm the Overview page shows a current device manufacturer/model, Android release and API even if no session exists. The System header is expected to use runtime Build identity until valid session metadata exists.
- [ ] Record initial values: active session state, observation bus status/count, GNSS history state, current satellite count, and whether orientation is available.

### Current target-device sensor caveat

The captured device capability report for this phone reported:
- GNSS raw measurements: supported;
- GNSS navigation messages: supported;
- accumulated delta range (ADR): supported;
- multi-frequency signals: supported;
- antenna information: unsupported;
- gyroscope, magnetometer, rotation-vector, gravity and linear-acceleration sensor classes: unavailable in that captured inventory.

Re-check this on the day of the test. Device firmware, permissions and runtime capability reports are the authority. With no usable rotation-vector/orientation source, a north-up skyplot with an explicit orientation-unavailable reason is an acceptable outcome. Do **not** file a bug solely because the plot fails to rotate with the phone when the required sensor is absent.

## 3. Recommended capture sequence

Use separate sessions for different test conditions so environmental changes are explainable.

### Run A - stationary open-sky baseline

1. Place the phone with as much open sky as practical. Keep its position/orientation still during the baseline.
2. Open Horizon Parallel and capture the pre-session Overview and System screens.
3. Tap **START** once. Do not tap repeatedly while the service start request is pending.
4. Confirm the UI transitions from **IDLE / NO SESSION** to the app's active/recording state. Confirm the notification/foreground-service behavior expected by Android for a location recording session.
5. Keep the app in the foreground during the raw-GNSS test. The Android raw GNSS callback has platform lifecycle/permission constraints; a running foreground service is not proof that every callback is currently producing data.
6. Allow approximately 10 minutes for a stationary baseline when the environment permits. Record the actual elapsed duration; do not claim a cold-start fix if no fix was obtained.
7. Inspect **Overview**, **GNSS**, and **System & Capabilities**. Capture screenshots/UI evidence without interrupting the session.
8. Tap **STOP** once. Wait until the session reaches its terminal/completed state; do not immediately force-stop the process.
9. Verify that the session appears in **Sessions** and that the completed session is available for export.
10. Generate and save the session export through the app UI. Preserve the exported archive, manifest and checksum files exactly as produced.

### Run B - short movement/continuity run (optional)

Start a new session and record a safe, repeatable short walk in an open, public area. Do not enter roads, restricted sites or unsafe terrain to collect data. Record approximate movement start/stop times and any observed obstructions. This run tests state freshness and continuity behavior; it does not by itself validate inertial navigation or physical spoofing/jamming detection.

Keep Run B separate from the stationary baseline. Do not deliberately perform RF interference, spoofing, jamming, location injection or other potentially unsafe tests as part of this protocol.

### Optional obstructed-sky run

Only after Run A succeeds, create another short session in a clearly described partially obstructed location. Treat differences from the open-sky baseline as observations that may have multiple causes, not automatic evidence of interference or spoofing.

## 4. What should appear during the run

### Overview

Expected when the app is running:
- session controls remain accessible within the same scrollable Overview surface as the telemetry/sky instrument;
- the observation-bus state and observation count update when evidence is ingested;
- the observed sky view displays only satellites for which the app has valid, finite observed geometry;
- C/N0 color is based on reported C/N0; unavailable C/N0 is neutral gray;
- a white satellite ring means the source reports that satellite was used in the position fix;
- elevation grid rings are labelled separately from the white fix-use ring;
- if no valid azimuth/elevation exists, the plot displays **NO VALID GEOMETRY** and the explicit no-synthetic-position message;
- the heading/orientation label describes the actual mode/reason. Do not infer live orientation from the fact that the phone has been moved.

Before a valid observation session starts, zero points and **NO VALID GEOMETRY** are expected. During an outdoor session with valid correlated satellite geometry, the chart should populate from that evidence. A populated plot is not guaranteed merely because a location permission is granted.

### GNSS tab

Look for actual evidence states, not just a screen title:
- observation/history sample counts;
- whether the domain reports available correlated satellite/navigation evidence;
- AGC history only when AGC samples actually exist;
- explicit **UNAVAILABLE / NO HISTORY DATA** when the session has not recorded the requested type;
- reported satellite identifiers and signal/geometry fields when available.

Raw measurements, satellite status, navigation messages and PVT are separate evidence streams. They need not all have the same number of rows, and Android can expose a capability without returning that event type in every session.

### System & Capabilities tab

Expected:
- device header shows the live runtime identity before a session, or a valid recorded device snapshot when present;
- permissions show their actual runtime state;
- the capability report lists hardware/platform features and their observed support status;
- missing sensors and unsupported antenna information remain explicitly unavailable/unsupported.

The app must not imply that every capability was observed just because it was declared in the manifest.

### Predictions and identity

Predicted pass/ground-track surfaces may remain empty or show a reason when the OMM catalog, valid observer state or verified identity row is missing. In this specific Parallel build, the OrbitCore bridge is not packaged (the Gradle dependency is `t1Implementation` only), so propagation requests can return `ENGINE_UNAVAILABLE` even when catalog data exists. Treat that as a flavor/backend gate, not a GNSS reception failure. The identity table is a separate sourced mapping gate. Receiver SVID is not automatically a NORAD catalog identity. A blank predicted view does not mean raw GNSS acquisition failed.

## 5. Data and integrity acceptance checklist

After stopping and exporting Run A, collect the following evidence:

- [ ] Session ID and final lifecycle state.
- [ ] Start/end wall time, actual elapsed duration and stated environmental condition.
- [ ] Total persisted observation count and counts by type: GNSS raw, GNSS status, GNSS clock, navigation messages, GNSS/PVT fix, cellular info, sensor events, system/error events.
- [ ] Permission/capability report from that session.
- [ ] Raw satellite evidence samples sufficient to show constellation, SVID, C/N0 and available measurement timing/ADR/pseudorange-rate fields, subject to the platform exposing them.
- [ ] Valid observed geometry count and number excluded for missing/invalid azimuth/elevation.
- [ ] Integrity report: sequence gaps/duplicates, ingestion monotonic-time ordering, source timestamp regressions, errors and warnings.
- [ ] Export verification: database/full-session count versus exported JSON count, CSV row count and manifest count where those fields are reported.
- [ ] Archive integrity/checksum result from the app; retain the archive unchanged.
- [ ] UI screenshots of Overview, GNSS, Sessions, System & Capabilities, and relevant Logs/Diagnostics.
- [ ] Cold-launch/process/crash-buffer evidence if a UI crash or ANR is suspected.

### Required ordering properties

- Sequence numbers are session-scoped and should be contiguous for a complete uncorrupted recorded session, unless a recorded audit explicitly explains otherwise.
- Ingestion-monotonic timestamps should be non-decreasing through the persisted ingress path.
- Source timestamps may regress across asynchronous producers. Preserve and report such regressions; they do not on their own imply ingestion-order failure.
- Raw and normalized payloads must remain separate, and missing data must remain null/unavailable rather than being filled with zero.
- Error-event count should ideally be zero; any non-zero error event must be preserved, interpreted and explained rather than removed to make the run appear clean.

## 6. Pass, fail and inconclusive decisions

### PASS for basic device/session behavior

Use this label only if the session starts, records actual device-originated observations, stops normally, persists a completed session, exports successfully, and the sequence/ingestion/order/count checks pass. Attach the exact evidence required above.

### PASS for the UI build gate

Use this label if all primary navigation destinations can be opened, content is not clipped at the target display/font scale, session controls work without overlap, the device identity is correct, screen-reader semantics are usable, and the tested launch/navigation flow is free of crashes. A debug UI cold launch is already verified for this package; the new field run must still re-check current behavior.

### FAIL

Examples: crash/ANR; session cannot start after required permissions are actually granted and platform location is enabled; observations never persist despite positive source callbacks; sequence corruption; ingestion-time monotonic violations; export count/checksum mismatch; fabricated-looking values that contradict raw evidence; or a supposedly observed satellite point appears without valid observed azimuth/elevation.

### INCONCLUSIVE / UNAVAILABLE

Examples: permission denied; location disabled; no clear-sky acquisition; callback says measurements unsupported/not allowed; hardware has no required sensor; cell update rate-limited; catalog or identity data absent; or environmental conditions do not yield valid geometry. Report the exact cause and capture the capability/evidence report; do not classify a platform limitation as a software failure without evidence.

## 7. ADB evidence collection (optional but recommended)

Run on the authorized Windows development machine while the phone is connected. Substitute the actual serial if it changes:

~~~~powershell
$serial = "[device serial redacted]"
$package = "horizon.observatory.parallel.debug"
adb devices
adb -s $serial shell getprop ro.product.model
adb -s $serial shell getprop ro.build.version.release
adb -s $serial shell getprop ro.build.version.sdk
adb -s $serial shell pm path $package
adb -s $serial shell pidof -s $package
adb -s $serial logcat -d -b crash
adb -s $serial shell dumpsys location
adb -s $serial shell dumpsys sensorservice
~~~~

For a reproducible screenshot and accessibility/UI-tree record:

~~~~powershell
adb -s $serial shell am force-stop $package
adb -s $serial logcat -c
adb -s $serial shell monkey -p $package 1
Start-Sleep -Seconds 3
adb -s $serial exec-out screencap -p > .\overview-after-launch.png
adb -s $serial shell uiautomator dump /sdcard/horizon-ui.xml
adb -s $serial shell cat /sdcard/horizon-ui.xml > .\horizon-ui.xml
adb -s $serial logcat -d -b crash > .\crash-buffer.txt
~~~~

Do not run a force-stop during an active recording session. Use this sequence only before a run or after it has been stopped/completed.

## 8. Field report template

Copy and complete the following in a separate file for each session:

~~~~text
Run label:
Date/time and time zone:
Device / Android / API:
App package / APK SHA-256:
Location type and sky obstruction:
Permission states:
Capability report file:
Session ID:
Start / stop UTC:
Observed duration:
Final lifecycle state:
Persisted observation count:
Counts by type:
GNSS status / raw measurement availability:
Valid observed satellite geometry:
Sequence gaps / duplicates:
Ingestion monotonic violations:
Source timestamp regressions:
Error events / relevant log excerpt:
Export filename and SHA-256:
Export count/checksum validation:
Screenshots/UI tree filenames:
PASS / FAIL / INCONCLUSIVE:
Unresolved observations and reason:
~~~~

Preserve the original export and raw evidence unchanged. Analysis copies and redacted screenshots may be created separately, but must not replace the source evidence.

## 9. Current known limitations relevant to interpreting field data

- The latest captured capability report did not find gyroscope, magnetometer or rotation-vector sensors on this device. Heading-up orientation is therefore not assumed.
- Antenna information was reported unsupported in the captured system report; do not expect antenna phase-center modeling or antenna metadata if the platform cannot provide it.
- Carrier phase and AGC capability are unverified as capability claims, regardless of whether individual samples are present.
- Two historical Room migration tests are skipped until the required schema v5/v6 baselines are available; this field session does not close that database migration gate.
- Orbit propagation/identity/prediction scientific gates remain separate from the raw-GNSS session gate.
- The current release verification has not yet captured a fresh outdoor session. That is the main next action after package installation and permission pre-flight.

## 10. Official references

- Android Developers - [Raw GNSS measurements](https://developer.android.com/develop/sensors-and-location/sensors/gnss)
- Android API - [LocationManager](https://developer.android.com/reference/android/location/LocationManager)
- Android API - [GnssMeasurementsEvent.Callback](https://developer.android.com/reference/android/location/GnssMeasurementsEvent.Callback)
- Android Developers - [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics)
- Android Developers - [Compose UI testing](https://developer.android.com/develop/ui/compose/testing)

This protocol is the acceptance rubric for the upcoming real-world recording. It intentionally does not claim a position accuracy, receiver capability, sensor orientation, satellite identity or propagation accuracy before the corresponding raw evidence and independent validation exist.
