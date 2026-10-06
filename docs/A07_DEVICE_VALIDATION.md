# Samsung Galaxy A07 / SM-A075F — Physical Device Validation Protocol

## Purpose

This protocol turns the remaining `UNVERIFIED` statements into measured evidence without inferring hardware capability from documentation alone.

## Preconditions

- Samsung Galaxy A07 / SM-A075F physically connected.
- Developer options enabled.
- USB debugging enabled.
- Android reports API 36.
- ADB is available on PATH.
- A debug APK has been built from this repository.

## Identity check

Capture:

```text
ro.product.manufacturer
ro.product.model
ro.build.version.release
ro.build.version.sdk
```

Expected model: `SM-A075F`.

## Capability session

1. Launch Horizon.
2. Open **Diagnostics**.
3. Save the full capability report.
4. Record every field as `AVAILABLE_NOW`, `UNAVAILABLE`, `UNVERIFIED`, or `UNKNOWN` exactly as reported.

## GNSS validation

Perform an outdoor open-sky session.

Verify independently:

- GNSS status callbacks arrive.
- raw measurement callbacks arrive, if exposed.
- C/N0 values are numeric and not synthetic.
- SVID/constellation fields are present where exposed.
- navigation messages are present or explicitly unavailable.
- antenna info is present or explicitly unavailable.
- carrier phase/ADR/AGC are reported only when exposed.
- monotonic timestamps are non-null for the corresponding event stream.

Do not upgrade an `UNVERIFIED` hardware capability to `VERIFIED` from an API existence check.

## Cellular validation

Record:

- serving/registered status;
- RAT;
- MCC/MNC;
- cell identity;
- PCI/TAC where exposed;
- EARFCN where exposed;
- RSRP/RSRQ/RSSNR where exposed;
- timestamp data;
- whether the source reports a refreshed or cached result when determinable.

## Sensor validation

Compare the runtime sensor inventory with actual device behavior. Each sensor must be classified independently.

## Session lifecycle validation

Run:

```text
START
  -> ACTIVE
STOP
  -> STOPPING
  -> CLOSED
```

Then repeat with an interrupted process where practical and verify recovery to `CRASH_CLOSED` after restart.

## Integrity validation

For a closed session, export and verify:

```text
Room observation count
= JSON record count
= CSV data-row count
= manifest.totalObservations
```

Verify:

```text
sequenceNumber = 1..N
unique(sessionId, sequenceNumber)
monotonic timestamps nondecreasing per provider
```

## Evidence package

Preserve:

- capability JSON;
- exported ZIP;
- logcat capture;
- session ID;
- Android/API identity;
- validation date/time;
- any observed unavailable capabilities.

The output of this protocol is the evidence required to change individual fields from `UNVERIFIED` to `MEASURED`/`OBSERVED`/`VERIFIED`.
