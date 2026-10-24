# Research Evidence and Design Traceability â€” 2026-10-18

## References
1. Android GnssClock API: https://developer.android.com/reference/android/location/GnssClock â€” hardware-clock continuity and discontinuity semantics.
2. Android Raw GNSS Measurements: https://developer.android.com/develop/sensors-and-location/sensors/gnss â€” platform raw-measurement API context.
3. Android raw measurement analysis: https://developer.android.com/develop/sensors-and-location/sensors/gnss-analyze-raw â€” state flags and accumulated-delta-range guidance.
4. Gogoi, Minetto, Linty and Dovis, A Controlled-Environment Quality Assessment of Android GNSS Raw Measurements, Electronics (2019): https://doi.org/10.3390/electronics8010005.
5. Vallado et al., Revisiting Space-Track Report #3: https://celestrak.org/publications/AIAA/2006-6753/AIAA-2006-6753-Rev2.pdf.
6. Validation of SGP4 and IS-GPS-200D against GPS precision ephemerides: https://celestrak.org/publications/AAS/07-127/AAS-07-127.pdf.

## Traceability
- Preserve raw timestamps, clock-domain and ingestion-time fields because source chronology must be audited rather than silently repaired.
- Treat hardware-clock discontinuity as a boundary for continuity assumptions.
- Preserve measurement state and ADR validity/reset/cycle-slip flags; unavailable capability is not synthesized.
- Separate raw observations, derived results, model predictions and unavailable values in UI/export.
- Require independent reference vectors and frame/time-scale checks before claiming orbit accuracy.
- Use controlled field comparisons and independent ground truth before attributing weak signals to interference or multipath.

## Current evidence boundary
The tested persisted export contains 93,754 observations; sequences are contiguous, ingestion timestamps are non-decreasing, SQLite integrity is ok, and 13/13 embedded hashes pass. Source timestamps regress in 44,938 records (~47.93%) and the cause remains unresolved. Three SGP4 vectors remain skipped, Room migration baselines are missing, connected instrumentation was not rerun, and no outdoor ground-truth/interference campaign is claimed. These references guide validation design; they do not certify this implementation or dataset.


## Supplemental research and implementation trace - 2026-10-22

### API constraints directly supported by Android primary references

- `GnssClock` elapsed-realtime is API 29+ and only valid when its availability flag is true: https://developer.android.com/reference/android/location/GnssClock
- Event-level AGC is API 33+, may be reported before other measurement/clock fields are available, and an empty collection can indicate that the event-level field is unsupported: https://developer.android.com/reference/android/location/GnssMeasurementsEvent
- Android's AGC/CN0 jamming/spoofing guidance explicitly describes limitations and does not promise universal detection: https://developer.android.com/develop/sensors-and-location/sensors/gnss-spoof-jam

### Research records surfaced in scientific search

- Spens, Lee, Nedelkov and Akos, "Detecting GNSS Jamming and Spoofing on Android Devices", DOI 10.33012/navi.537: https://doi.org/10.33012/navi.537
- "Assessment of RFI Geolocation Using Modern Android Smartphones", DOI 10.33012/2022.18571: https://doi.org/10.33012/2022.18571
- "Identifying Low Cost GNSS Monitor Metrics for Robust RFI Detection", DOI 10.33012/2024.19542
- Kazim, Darwich and Marais, 2026 COTS receiver preprint, arXiv:2602.12688: https://arxiv.org/abs/2602.12688. The indexed abstract limits the evaluated interference case to chirp on GPS L1.

SciSpace search returned paper metadata/abstracts; the results were used for discovery and scope limits, not represented as exhaustive full-text or a systematic review. AGC is device/RF-chain dependent and must be validated alongside independent calibrated RF ground truth, per-device baselines and held-out sessions. Missing/constant AGC is not a negative or positive interference verdict.

The complete current-source, code-change, tool-access, historical-evidence and verification ledger is `docs/research/DEVELOPMENT_HISTORY_AND_RESEARCH_LEDGER_2026-10-22.md`.
