package horizon.observatory.acquisition.gnss

import android.location.GnssMeasurementsEvent
import android.os.Build
import horizon.observatory.core.time.TimestampEngine
import horizon.observatory.domain.model.CapabilityState
import horizon.observatory.domain.model.EvidenceStatus
import horizon.observatory.domain.model.ObservationProvenance
import horizon.observatory.domain.model.ObservationType
import horizon.observatory.domain.model.RawObservation
import horizon.observatory.domain.model.TimestampDomain
import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.putIf(key: String, present: Boolean, value: () -> Any): JSONObject =
    put(key, if (present) value() else JSONObject.NULL)

/**
 * One GNSS_CLOCK row per measurements event: the raw receiver clock fields that offline replay and clock-continuity
 * diagnostics need (they were not persisted before). Fields the receiver does not report are JSON null — never zero.
 * `eventElapsedRealtimeNanos` is the same key the measurement rows use, so rows of one event can be joined.
 */
internal fun GnssMeasurementsEvent.toClockObservation(eventElapsedNs: Long, eventTimestampSource: String): RawObservation {
    val c = clock
    val payload = JSONObject().apply {
        put("eventElapsedRealtimeNanos", eventElapsedNs)
        put("eventTimestampSource", eventTimestampSource)
        put("timeNanos", c.timeNanos)
        putIf("timeUncertaintyNanos", c.hasTimeUncertaintyNanos()) { c.timeUncertaintyNanos }
        putIf("fullBiasNanos", c.hasFullBiasNanos()) { c.fullBiasNanos }
        putIf("biasNanos", c.hasBiasNanos()) { c.biasNanos }
        putIf("biasUncertaintyNanos", c.hasBiasUncertaintyNanos()) { c.biasUncertaintyNanos }
        putIf("driftNanosPerSecond", c.hasDriftNanosPerSecond()) { c.driftNanosPerSecond }
        putIf("driftUncertaintyNanosPerSecond", c.hasDriftUncertaintyNanosPerSecond()) { c.driftUncertaintyNanosPerSecond }
        put("hardwareClockDiscontinuityCount", c.hardwareClockDiscontinuityCount)
        putIf("leapSecond", c.hasLeapSecond()) { c.leapSecond }
        putIf("elapsedRealtimeUncertaintyNanos", Build.VERSION.SDK_INT >= 29 && c.hasElapsedRealtimeUncertaintyNanos()) { c.elapsedRealtimeUncertaintyNanos }
        put("measurementCount", measurements.size)
        put("eventExtras", eventExtras())
    }
    return RawObservation(
        type = ObservationType.GNSS_CLOCK,
        utcTimestampMs = TimestampEngine.elapsedNanosToUtcMillis(eventElapsedNs),
        monotonicTimestampNs = eventElapsedNs,
        provider = "GnssClock",
        payloadJson = payload.toString(),
        technology = "GNSS",
        capabilityState = CapabilityState.AVAILABLE_NOW,
        evidenceStatus = EvidenceStatus.MEASURED,
        provenance = ObservationProvenance.RAW_ACQUISITION,
        timestampDomain = TimestampDomain.ANDROID_ELAPSED_REALTIME_NANOS
    )
}

/**
 * Event-level extras read through reflection so that an API-signature difference degrades to "not exposed" instead of
 * breaking acquisition: `isFullTracking` (duty-cycling) and the event-level AGC collection (newer Android API; the
 * measurement-level AGC path is deprecated). UNVERIFIED on a device.
 */
private fun GnssMeasurementsEvent.eventExtras(): JSONObject =
    extractGnssEventExtras(this, GnssMeasurementsEvent::class.java)

/**
 * Reflection boundary kept injectable so framework API drift and malformed control objects can be
 * exercised in local JVM tests without constructing Android framework objects or emitting RF.
 */
internal fun extractGnssEventExtras(target: Any, apiClass: Class<*>): JSONObject {
    val extras = JSONObject()
    val full = runCatching { apiClass.getMethod("isFullTracking").invoke(target) as? Boolean }.getOrNull()
    extras.put("fullTracking", full ?: JSONObject.NULL)

    var api = "NOT_EXPOSED"
    val arr = JSONArray()
    runCatching {
        val coll = apiClass.getMethod("getGnssAutomaticGainControls").invoke(target) as? Collection<*>
        if (coll != null) {
            api = "EVENT_LEVEL"
            coll.forEach { agc ->
                if (agc != null) {
                    // A malformed item must not discard otherwise valid controls from this event.
                    runCatching {
                        val k = agc.javaClass
                        JSONObject().apply {
                            put("levelDb", (k.getMethod("getLevelDb").invoke(agc) as Number).toDouble())
                            put("constellationType", (k.getMethod("getConstellationType").invoke(agc) as Number).toInt())
                            put("carrierFrequencyHz", (k.getMethod("getCarrierFrequencyHz").invoke(agc) as Number).toDouble())
                        }
                    }.getOrNull()?.let(arr::put)
                }
            }
        }
    }
    extras.put("agcApi", api)
    extras.put("agcEvents", arr)
    return extras
}
