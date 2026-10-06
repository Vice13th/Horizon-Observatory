# HORIZON — Architecture State at Checkpoint

## Data path

```text
Android / Vendor Runtime
        |
        +-- GNSS Status --------------------+
        |                                    |
        +-- GNSS Raw Measurements -----------+--> Observation Domain
        |                                    |
        +-- GNSS Navigation Messages -------+
        |
        +-- Cellular callbacks / fresh requests
        |
        +-- Sensors
        |
        v
Observation ingestion
        |
        +-- source timestamp
        +-- HORIZON ingestion monotonic timestamp
        +-- session-scoped sequence number
        +-- provenance / capability state
        v
Reliable persistence queue
        |
        v
Room / observations
        |
        +--> Integrity audit
        +--> Session analysis
        +--> Export engine
        +--> UI Observatory
```

## Integrity model

The source measurement timestamp is evidence supplied by the platform/provider. It is not used as the sole ordering clock because asynchronous producers can deliver events out of order.

The HORIZON ingestion monotonic timestamp is the integrity ordering clock.

Sequence numbers are session-scoped and begin at 1 for each session.

## GNSS model

The checkpoint intentionally keeps these streams separate:

- `GNSS_STATUS`
- `GNSS_RAW_MEASUREMENT`
- `GNSS_NAVIGATION_MESSAGE`
- `GNSS_FIX`

The next analysis layer will correlate these records rather than flattening fields prematurely.

Recommended satellite correlation key:

`(constellationType, svid, carrierFrequencyHz when available)`

## Cellular model

Cellular observations carry acquisition provenance where available:

- `REQUEST_CELL_INFO_UPDATE`
- `TELEPHONY_CALLBACK`
- source age metadata
- serving/registered state

Serving-cell measurements are preferred in derived RSRP analysis; neighbor observations remain raw evidence.

## Capability states

Capability availability, observation availability and measured values are distinct concepts.

For example:

`AGC capability state = UNVERIFIED`

can coexist with:

`AGC observation = 6.0 dB, 207 samples, spread 0`

The UI must not convert capability uncertainty into fabricated measurement values.

## Orbit prediction foundation (PREDICTED/DERIVED domain)
Added under `horizon.observatory.astronomy.{time,catalog,identity,propagation,frames,prediction,orientation}` and wired
in `HorizonContainer`. Observed evidence (`SatelliteEvidence`, `CorrelatedSkyPlot`) is unchanged. Details and status:
`docs/ORBIT_PREDICTION_ARCHITECTURE.md`. Room schema version is now 7.
