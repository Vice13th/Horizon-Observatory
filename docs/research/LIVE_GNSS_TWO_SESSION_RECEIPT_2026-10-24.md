# Horizon Parallel â€” Live Install & Smoke-Test Receipt

**Date:** 2026-10-24
**Disposition:** PASS â€” one controlled GNSS session completed and exported with per-type accepted-to-persisted counts reconciled; overall release remains BLOCKED.
**Scope:** Authorized ADB installation, launch verification, non-destructive UI navigation, one short GNSS capture/stop, and export reconciliation. No app-data clearing, uninstall, Git write, commit, push, or merge.

## Verified environment

- Worktree: `[local source path omitted]`
- Repository: `Vice13th/Horizonparallell`
- Branch: `test/final-review-handover-2026-10-22`
- Source HEAD at inspection: `0cb51ed64540c4ec12079d1fe6c68c6b6b6769ed`
- Target device: Samsung SM-A075F, serial `[device serial redacted]`, Android 16 / API 36, 720Ã—1600.
- Protected snapshot `[local path omitted]` was not modified.

## Build and artifact identity

- Task invoked: `:app:assembleParallelDebug --no-daemon --console=plain`
- Result: **BUILD SUCCESSFUL in 15s**; 40 actionable tasks, all up-to-date.
- APK: `source/Horizon-Parallel/app/build/outputs/apk/parallel/debug/app-parallel-debug.apk`
- Size: 10,913,306 bytes.
- SHA-256: `81B7079CE4ADA8C5D1EB469C5E72B2E984158EB44BEECCCBA253ED8D880A2992`
- Package/version: `horizon.observatory.parallel.debug`, `1.0.0-observatory`, versionCode 1.
- SDK range: minSdk 26, targetSdk 36.
- Signing: Android debug certificate; certificate SHA-256 `1e14d3b1acb546e0caadb8504e9872fe5242909bfee6c45e4ba4abb4859db5e6`. **Not a production-signed release.**
- Manifest launch activity: `horizon.observatory.ui.MainActivity`.
- The packaged APK and Gradle output were byte-identical by SHA-256. The four sidecars listed in `Horizon-Parallel-20261024-SHA256SUMS.txt` independently matched their recorded checksums. The existing package sidecars were preserved as historical receipts.

## JVM test evidence

- Full task invoked: `:app:testParallelDebugUnitTest --no-daemon --console=plain`
- Result: **BUILD SUCCESSFUL in 15s**; 27 actionable tasks reported up-to-date.
- JUnit report summary read from `app/build/test-results/testParallelDebugUnitTest/TEST-*.xml`: 58 suites, 380 tests, 0 failures, 0 errors, 3 skipped.
- Focused diagnostics suite XML: 6 tests, 0 failures, 0 errors, 0 skipped. It covers per-session reset, snapshot round-trip, incomplete diagnostics, type-level accounting, closed/full classification, and finalized accepted-versus-persisted validation.
- Important qualification: the full test task reported outputs up-to-date in this run; the report summary is measured from the existing JUnit XML, not proof that all 380 test bodies were freshly executed during this exact invocation.
- `git diff --check` produced no whitespace-error output. Git source state was not changed.

## Device install and runtime smoke

- Pre-install package lookup returned no path for `horizon.observatory.parallel.debug`.
- `adb -s [device serial redacted] install -r ...app-parallel-debug.apk` completed with process exit code 0. No uninstall or app-data clear was issued.
- Post-install `pm path` returned `/data/app/â€¦/horizon.observatory.parallel.debugâ€¦/base.apk`.
- Android package manager reports `versionName=1.0.0-observatory`, `versionCode=1`, and `lastUpdateTime=2026-10-24 21:29:00`.
- `am start -W -n horizon.observatory.parallel.debug/horizon.observatory.ui.MainActivity` returned `Status: ok`; `MainActivity` was observed as the top resumed activity and the process was present.
- Non-destructive navigation visited Overview, GNSS, Sessions, System, and More.
- Overview correctly rendered an idle/no-session state and no valid observed satellite geometry; no synthetic positions were shown.
- Before the controlled capture began, GNSS rendered empty-history and unavailable telemetry states. During the fresh capture the observation counter rose to 52; after finalization it returned to the completed-session view.
- Sessions displayed Session History / IDLE; System displayed Samsung SM-A075F / Android 16 / API 36 and Session NONE; More showed Timeline, Diagnostics, Export (disabled until a completed session), Internal Log, and Antenna entries.
- Sampled logcat searches for `FATAL EXCEPTION`, `ANR in`, `NoSuchMethodError`, and the app process fatal signature returned no matches in the captured window. This is a smoke-check only, not endurance evidence.

## Local evidence files

Saved under `[local source path omitted]`:
- `current-device-live-postinstall.png` / `current-device-live-postinstall.xml`
- `current-device-live-gnss.png` / `current-device-live-gnss.xml`
- `current-device-live-sessions.xml`
- `current-device-live-system.xml`
- `current-device-live-more.xml`

These remain local and untracked; no private GNSS export or raw location archive was uploaded to research services.

## Outstanding acceptance gates

- **COMPLETED FOR ONE SHORT RUN:** a controlled GNSS capture was stopped/finalized, exported, and reconciled across manifest, session log, JSON, integrity audit, checksum sidecar and SQLite. See the live capture section below. Repeated/high-load/shutdown-race tests remain NOT RUN.
- **OPEN:** historical session discrepancy: 27 raw GNSS measurement rows and 1 GNSS status row were accepted by the channel but not found among persisted rows. Exact loss point remains **UNVERIFIED**. GitHub issue #3 remains open: https://github.com/Vice13th/Horizonparallell/issues/3
- **OPEN:** authentic Room schema v5/v6 history, API 26â€“28 physical-device validation, process-death/endurance, production signing and clean-machine release validation.
- **UNVERIFIED:** scientifically calibrated GNSS interference/AGC detection sensitivity, specificity, latency and false-alarm rate. No RF was transmitted.
- **Disposition:** suitable as an installed debug field-test candidate for continued controlled validation; **not release-ready and not production-certified**.

## State preservation

- No `git clean`, `git reset`, staging, commit, push, merge, or change to `main`.
- Existing untracked screenshots, XML, recovery backup, release manifests/checksums, and historical logs were preserved.
- No uninstall or app-data clear. One new controlled recording was created and finalized; pre-existing app data and prior sessions were not deleted or modified.

## Controlled live GNSS capture and export â€” 2026-10-24

**Result: PASS for this run; historical intermittent loss not reproduced.** No RF was transmitted. The app collected actual Android GNSS/sensor observations, then completed and exported the session.

- **Session:** `1ad83b5d-b7fb-44d8-b753-9b4095689af9`
- **Device:** Samsung SM-A075F, Android 16 / API 36; ADB serial `[device serial redacted]`
- **Observed lifecycle:** UI changed to `RECORDING / ACTIVE` and later `COMPLETED / IDLE`. Service log: `Session COMPLETED ... observations=1909`.
- **Export:** `C:\\Projects\\Horizonparallell-work\\current-device-session-1ad83b5d-export.zip`
- **Export size:** 431,157 bytes
- **Export SHA-256:** `D677B4D67BD176FA8737E968196F5762A3C32844A6EE232AEBF7CE9FF5D44A91`
- **Extracted contents:** `dataset_manifest.json`, `observations.json/.csv`, `session_events.json/.csv`, `session_log.txt`, `capabilities.json`, `permissions.json`, `integrity_audit.json`, `gnss_domain_snapshot.json`, `satellite_evidence.csv`, `gnss_antenna_evidence.json`, `horizon_session.sqlite`, and `checksums.sha256`.
- **Manifest/audit:** schema v5; 1,909 observations and JSON observation rows; 22 system events; 0 error events; sequence 1â€“1909 contiguous; ingestion timestamps nondecreasing; `integrityClean=true`; source timestamp regressions 637 (diagnostic, not itself a failure).
- **Delivery diagnostics:** state `FINALIZED`; countsComplete=true; invariantsHold=true; rejected rows 0 for each active GNSS callback type.
- **Source-to-SQLite and exported-row reconciliation:**
  | Type | Attempted | Accepted | SQLite persisted | Delta |
  |---|---:|---:|---:|---:|
  | GNSS_CLOCK | 38 | 38 | 38 | 0 |
  | GNSS_RAW_MEASUREMENT | 483 | 483 | 483 | 0 |
  | GNSS_STATUS | 38 | 38 | 38 | 0 |
  | GNSS_NAVIGATION_MESSAGE | 54 | 54 | 54 | 0 |
- **Whole-package reconciliation:** `dataset_manifest.json.totalObservations=1909`, JSON records=1909, `integrity_audit.json.observationCount=1909`, SQLite observations=1909, and SQLite audit observationCount=1909.
- **Embedded checksums:** all 13 non-checksum payload files listed in `checksums.sha256` matched their SHA-256 values.
- **Runtime:** app remained foregrounded after export; sampled logcat scan showed no app fatal-exception/ANR matches.
- **Receipt JSON:** `release/Horizon-Parallel-20261024-LIVE-SESSION-1ad83b5d-RESULT.json`

### Interpretation

This is a clean positive end-to-end result on one short capture. It does **not** prove the previously reported intermittent discrepancy has been fixed. Retain GitHub issue #3 as OPEN pending repeated sessions, longer high-load capture, and shutdown/backpressure stress tests. Production release status remains blocked by the other outstanding acceptance gates.


## Supersession note â€” follow-up live sessions (2026-10-24)

The earlier smoke-only status above was accurate at its checkpoint; it is superseded by the later two-session validation in `release/Horizon-Parallel-20261024-LIVE-SESSION-ANALYSIS.md`.

- Session `6c831fdc-f86c-417e-9405-d60731d2e0d4`: 6,273 observations; 13/13 checksums verified; accepted GNSS rows match SQLite. 59 raw-measurement and 4 status rows were explicitly rejected because the channel was full; zero channel-closed rejections.
- Session `8844e39c-af07-409c-b618-6b7ed69305a9`: 6,740 observations; 13/13 checksums verified; accepted GNSS rows match SQLite; no channel-full/closed rejections.
- The historical 27 raw + 1 status accepted-to-persisted gap was not reproduced. Issue #3 remains open; longer backpressure/process-death/shutdown-race tests and historical root-cause analysis are still required.
