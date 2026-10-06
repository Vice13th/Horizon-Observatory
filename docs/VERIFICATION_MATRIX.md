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
| K5 Emergency Navigation | UNIMPLEMENTED | No production emergency-mode state/lifecycle implementation yet |
| K6 Reception Optimization | UNIMPLEMENTED | No production optimization profile yet |
| K7 Resilient Location | UNIMPLEMENTED | No explicit Horizon location API/mock bridge yet |
| K8 hardware validation | BLOCKED / PENDING | Requires batched real-device degradation/loss/recovery and long-run evidence |
