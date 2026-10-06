package horizon.observatory.astronomy.time

/**
 * Explicit time-domain types for the orbit-prediction pipeline.
 *
 * Nothing in the pipeline accepts an anonymous `Long` timestamp where more than one time domain is
 * possible. Every instant is either one of the value classes below or a [PropagationTime].
 *
 * TIME CONTRACT (INPUT TIME / TIME SCALE / TIME REFERENCE / CONVERSION):
 *  - Unit: milliseconds since the Unix epoch (1970-01-01T00:00:00Z), i.e. "Unix time". Unix time
 *    does not count leap seconds; every UTC value in this pipeline (OMM EPOCH parsed through
 *    java.time.Instant, device wall clock) shares that convention, so differences between them are
 *    internally consistent. A leap second inserted between a catalog epoch and a propagation time
 *    would not be represented. None has been inserted since 2016-12-31.
 *  - Time scale: UTC. Sidereal time later in the chain treats UTC as UT1 (|UT1-UTC| < 0.9 s), an
 *    accepted and documented approximation (see PredictionFramePipeline).
 *  - Time reference: see [TimeBasis]. The device clock is NOT verified against GNSS time.
 *  - No conversion between GPS time, TAI and UTC is performed anywhere in this pipeline.
 *  - MonotonicElapsedNanos is a different clock domain (SystemClock.elapsedRealtimeNanos) and must
 *    never be compared with, or converted into, a UTC value except through
 *    horizon.observatory.core.time.TimestampEngine, which anchors it to the device wall clock.
 */
enum class TimeBasis {
    /** UTC exactly as stated by an external catalog (for OMM: the EPOCH field, UTC by definition). */
    CATALOG_UTC,

    /**
     * UTC derived from the device wall clock via TimestampEngine (one wall-clock reading at process
     * start plus elapsed real time). NOT verified against GNSS time and not corrected for later
     * wall-clock steps.
     */
    DEVICE_CLOCK_UTC
}

/** Instant at which an orbital element set is defined (OMM EPOCH), UTC ms. */
@JvmInline
value class CatalogEpochUtcMs(val value: Long)

/** Instant at which this device retrieved a catalog payload, device-clock UTC ms. */
@JvmInline
value class RetrievalTimeUtcMs(val value: Long)

/** Source/event time of a receiver observation converted to UTC ms via the device clock. */
@JvmInline
value class ObservationTimeUtcMs(val value: Long)

/** SystemClock.elapsedRealtimeNanos domain. Never mixed with UTC values. */
@JvmInline
value class MonotonicElapsedNanos(val value: Long)

/**
 * Time at which a satellite state is requested. The only time value accepted by the propagation
 * layer. [utcMillis] is Unix-time milliseconds; [basis] states where the value came from.
 */
data class PropagationTime(val utcMillis: Long, val basis: TimeBasis) {
    companion object {
        fun fromDeviceClock(clock: DeviceClockUtc): PropagationTime =
            PropagationTime(clock.nowUtcMillis(), TimeBasis.DEVICE_CLOCK_UTC)

        /** Propagate to the time of an observation. The observation time is device-clock derived. */
        fun forObservation(observationTime: ObservationTimeUtcMs, basis: TimeBasis): PropagationTime =
            PropagationTime(observationTime.value, basis)
    }
}

/** Injectable device wall-clock reading (UTC ms). Injectable so JVM tests need no Android runtime. */
fun interface DeviceClockUtc {
    fun nowUtcMillis(): Long
}
