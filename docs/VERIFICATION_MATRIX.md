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
