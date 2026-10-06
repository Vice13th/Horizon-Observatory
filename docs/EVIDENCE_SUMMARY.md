# HORIZON — Latest Session Evidence Summary

Two session ZIPs are preserved in `evidence/sessions/`.

## Session 35f8...

Observation mix:

- SYSTEM_EVENT: 2
- SENSOR_LIGHT: 188
- SENSOR_ACCEL: 1897
- CELLULAR_INFO: 27
- SENSOR_PROXIMITY: 1
- GNSS_STATUS: 38
- GNSS_RAW_MEASUREMENT: 207
- GNSS_NAVIGATION_MESSAGE: 14
- GNSS_FIX: 21

GNSS:

- AGC: 207 samples, min=max=6.0 dB
- C/N0: min 13.731, max 35.0, mean 20.819 dB-Hz
- satellite elevation entries: 2057
- satellite azimuth entries: 2057

Cellular:

- 18 `REQUEST_CELL_INFO_UPDATE`
- 9 `TELEPHONY_CALLBACK`
- source age: 7–5014 ms

Integrity:

- sequence 1..2395 contiguous
- ingestion timestamps non-decreasing
- integrity clean

## Session f860...

Observation mix:

- SYSTEM_EVENT: 2
- SENSOR_LIGHT: 299
- CELLULAR_INFO: 3
- SENSOR_ACCEL: 3028
- SENSOR_PROXIMITY: 1
- GNSS_STATUS: 60
- GNSS_RAW_MEASUREMENT: 129
- GNSS_FIX: 1

GNSS:

- AGC: 129 samples, min=max=6.0 dB
- C/N0: min 14.4, max 26.6, mean 22.675 dB-Hz
- satellite elevation entries: 2277
- satellite azimuth entries: 2277
- navigation-message records: 0

Integrity:

- sequence 1..3523 contiguous
- ingestion timestamps non-decreasing
- integrity clean
