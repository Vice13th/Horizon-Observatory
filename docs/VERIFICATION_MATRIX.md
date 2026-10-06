# HORIZON — Verification Matrix

| Gate | Status | Evidence |
|---|---|---|
| Source checkpoint assembled | VERIFIED | This archive |
| Next Phase Kotlin compile | VERIFIED | User-reported successful `compileDebugKotlin` |
| Physical device launch | VERIFIED | Runtime log evidence |
| Location permissions | VERIFIED | Session manifests |
| Foreground observation service | VERIFIED | Runtime log evidence |
| Session RECORDING | VERIFIED | Runtime `HorizonHealth` evidence |
| GNSS raw measurement engine | VERIFIED | Runtime log + exported observations |
| Multi-satellite data | VERIFIED | `GNSS_STATUS` + raw records |
| Elevation | OBSERVED | Exported `GNSS_STATUS` satellite arrays |
| Azimuth | OBSERVED | Exported `GNSS_STATUS` satellite arrays |
| C/N0 | MEASURED | Exported GNSS raw records |
| AGC | MEASURED | 6.0 dB samples in both latest sessions |
| AGC variation | MEASURED | Exactly constant in both latest sessions |
| GNSS navigation message capture | VERIFIED for session `35f8...` | 14 persisted navigation-message records |
| GNSS navigation message capture | NOT_OBSERVED for session `f860...` | 0 persisted records |
| Cellular callback path | VERIFIED | 9 callback records in `35f8...` |
| Cellular fresh request path | VERIFIED | 18 request records in `35f8...` |
| Cellular freshness metadata | MEASURED | 7–5014 ms in `35f8...` |
| Session sequence integrity | VERIFIED | contiguous 1..N in both sessions |
| Ingestion monotonic integrity | VERIFIED | non-decreasing in both sessions |
| Source timestamp regressions | OBSERVED | retained and reported, not treated as ingestion-order violations |
| Export | VERIFIED | real exported session ZIPs |
| Export read-back | VERIFIED in prior validated pipeline | existing export/integrity evidence |
| Antenna model | NOT_IMPLEMENTED | intentionally deferred |
| GNSS correlation layer | NEXT | not yet implemented |
| Final release | NOT_CLAIMED | checkpoint only |

| B0 panel recovery firewall | VERIFIED | Real-device active recording capped UI projection at 512; completed session retained 9,522 persisted observations; 11 instrumentation tests passed |
| B0 raw evidence preservation | VERIFIED | Completed session total 9,522 > live projection cap 512; full snapshot path remained active after completion |
| B4 orientation source on SM-A075F | BLOCKED | Sensor inventory exposes only coarse `TYPE_DEVICE_ORIENTATION (27)` among orientation-related sensors; no rotation-vector, geomagnetic rotation-vector, magnetic-field, or heading sensor was exposed |
| B5 empirical panel/skyplot rotation | BLOCKED | Requires a device exposing a valid orientation source; not inferred on sensorless target |
| B0 performance observation | MEASURED / FOLLOW-UP | Debug gfxinfo aggregate: 1,139 frames, 94 janky (8.25%); no root-cause attribution |

| K0 capability contract | IMPLEMENTED / MEASURED | Typed AVAILABLE / UNAVAILABLE / UNKNOWN contract with provenance/time; existing session capability report retained |
| K1 interference evidence engine | IMPLEMENTED / TESTED | Deterministic evidence scoring with NORMAL/DEGRADED/JAM-LIKELY/SPOOF-LIKELY/UNKNOWN/RECOVERY hypothesis states; 6/6 resilience tests green |
| K2 measurement trust | IMPLEMENTED / TESTED | Deterministic ranking, KEEP/DOWN_WEIGHT/REJECT decisions and reasons; raw evidence untouched |
| K3 navigation continuity | IMPLEMENTED / TESTED | Debounced explicit state machine; transition tests green |
| K4 dead-reckoning bridge | IMPLEMENTED / TESTED | Last-trusted PVT bridge, explicit dead-reckoned provenance, uncertainty growth; no fabricated heading |
| K0-K4 service integration | UNVERIFIED | Domain engines not yet wired into live ObservatoryService/Room derived-event path |
| K5 Emergency Navigation | IMPLEMENTED / DEVICE-PARTIAL | Persistent mode controller + foreground-service integration; ADB shell cannot invoke internal exported=false service; UI/background/screen-off recovery remains unverified |
| K6 Reception Optimization | IMPLEMENTED / TESTED | Explicit ON/OFF software policy + deterministic trust-policy reuse; no physical RF gain claimed |
| K7 Resilient Location | IMPLEMENTED / DEVICE-VERIFIED CONTRACT | Read-only ContentProvider with dedicated permission; instrumentation verified explicit snapshot/provenance contract; mock/test-location bridge remains unimplemented |
| K8 hardware validation | BLOCKED / PENDING | Requires batched real-device degradation/loss/recovery and long-run evidence |

| K0-K4 live service integration | VERIFIED / DEVICE-OBSERVED | `ResilienceRuntime` wired at serialized observation ingress; raw evidence unchanged; DERIVED resilience events persisted; controlled-loss device receipt |
| K5 Emergency Navigation lifecycle | DEVICE-PARTIAL / VERIFIED RECOVERY | Service recovery closed interrupted session; clean stop completed; background/screen-off long-run remains unverified |
| K6 Reception Optimization | IMPLEMENTED / TESTED | Software-only policy; no RF/antenna gain claim |
| K7 Resilient Location | DEVICE-VERIFIED CONTRACT / INTEGRATED FEED | Explicit provider contract instrumented; runtime publishes observed GNSS and dead-reckoned snapshots; shell consumer denied by required read permission as designed |
| K8 controlled GNSS-loss resilience | VERIFIED CONTROLLED LOSS | Location mode 3→0→3; observed transitions through GNSS degraded/lost/inertial/multi-source and recovery; not physical interference validation |
| K8 physical interference / long-run validation | PENDING | Requires controlled physical interference/degradation, truth comparison, long-run background/screen-off and resource/battery receipts |

## 2026-10-06 K0-K7 RUNTIME HARDENING RECEIPT

- K2/K6 freshness policy: VERIFIED in JVM regression; measurement age now feeds derived freshness and stale derived candidates are explicitly rejected.
- K1 recovery classification: VERIFIED in JVM regression; explicit recovery evidence is evaluated before NORMAL.
- K0-K7 derived-event volume control: VERIFIED in JVM regression; unchanged GNSS interference state does not emit duplicate resilience events while raw GNSS_STATUS evidence remains persisted.
- Full regression: 162 JVM tests, 0 failures, 0 errors, 3 existing reference-vector tests skipped.
- Device regression: 14 instrumentation tests finished on SM-A075F / Android 16, 2 existing migration tests skipped.
- APK: `2F3C43B74CBFF7D9D79C3415B19E1517E2F8D3ABE59092A9203B89DB797B0AB2`.
- K8 physical/long-run validation remains PENDING.
## FINAL K RUNTIME HARDENING RECEIPT — 2026-10-06

Final source correction after review: interference classification ordering now gives strong JAM/SPOOF/DEGRADED evidence precedence over recovery flags, with explicit recovery remaining reachable before NORMAL. Regression coverage was extended for this precedence boundary.

Final receipt: `:app:testPrimaryDebugUnitTest :app:assemblePrimaryDebug` BUILD SUCCESSFUL; 163 unit tests, 0 failures, 0 errors, 3 existing reference-vector skips. APK SHA-256: `4641C4F855C8EB92E769E404171237CFBC11D2F345F611CBBB6DE92E590DBE0C`. `:app:connectedPrimaryDebugAndroidTest` BUILD SUCCESSFUL; 14 tests finished on SM-A075F / Android 16, 2 existing migration tests skipped. No new K8 physical-interference claim is made.