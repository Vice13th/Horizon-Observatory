package horizon.observatory.analysis.integrity

import horizon.observatory.storage.entity.ObservationEntity
import org.json.JSONObject

/** Parsed integrity inputs. Fields that are absent or JSON null stay null — they are never turned into zero. */
data class IntegrityInputs(
    val measurements: List<MeasurementSample>,
    val clocks: List<ClockSample>,
    val agc: List<AgcEventSample>,
    val agcDiagnostics: AgcParseDiagnostics = AgcParseDiagnostics()
) {
    companion object {
        const val TYPE_RAW = "GNSS_RAW_MEASUREMENT"
        const val TYPE_CLOCK = "GNSS_CLOCK"

        private fun JSONObject.dbl(key: String): Double? =
            if (has(key) && !isNull(key)) optDouble(key, Double.NaN).takeIf { it.isFinite() } else null

        private fun JSONObject.lng(key: String): Long? =
            if (has(key) && !isNull(key)) runCatching { getLong(key) }.getOrNull() else null

        private fun JSONObject.int(key: String): Int? = lng(key)?.toInt()

        fun fromObservations(rows: List<ObservationEntity>): IntegrityInputs {
            val measurements = ArrayList<MeasurementSample>()
            val clocks = ArrayList<ClockSample>()
            val agc = ArrayList<AgcEventSample>()
            var eventsSeen = 0
            var unsupportedOrMissingArrayEvents = 0
            var emptyArrayEvents = 0
            var malformedEntryCount = 0
            var invalidLevelCount = 0
            for (row in rows) {
                if (row.type != TYPE_RAW && row.type != TYPE_CLOCK) continue
                val obj = runCatching { JSONObject(row.rawPayloadJson) }.getOrNull() ?: continue
                val ns: Long = obj.lng("eventElapsedRealtimeNanos") ?: row.monotonicTimestampNs ?: continue
                if (row.type == TYPE_RAW) {
                    val constellation = obj.int("constellationType") ?: continue
                    val svid = obj.int("svid") ?: continue
                    measurements.add(
                        MeasurementSample(
                            ns, constellation, svid,
                            obj.dbl("carrierFrequencyHz"), obj.dbl("cn0DbHz"), obj.dbl("automaticGainControlLevelDb"),
                            obj.dbl("pseudorangeRateMetersPerSecond"), obj.dbl("pseudorangeRateUncertaintyMetersPerSecond"),
                            obj.dbl("accumulatedDeltaRangeMeters"), obj.int("accumulatedDeltaRangeState"), obj.dbl("accumulatedDeltaRangeUncertaintyMeters")
                        )
                    )
                } else {
                    val extras = obj.optJSONObject("eventExtras")
                    val full = extras?.let { if (it.has("fullTracking") && !it.isNull("fullTracking")) it.optBoolean("fullTracking") else null }
                    clocks.add(
                        ClockSample(
                            ns, obj.lng("timeNanos"), obj.dbl("biasNanos"), obj.dbl("biasUncertaintyNanos"),
                            obj.dbl("driftNanosPerSecond"), obj.dbl("driftUncertaintyNanosPerSecond"),
                            obj.int("hardwareClockDiscontinuityCount"), full
                        )
                    )
                    eventsSeen++
                    val arr = extras?.optJSONArray("agcEvents")
                    if (arr == null) {
                        unsupportedOrMissingArrayEvents++
                    } else if (arr.length() == 0) {
                        emptyArrayEvents++
                    } else {
                        for (i in 0 until arr.length()) {
                            val a = arr.optJSONObject(i)
                            if (a == null) {
                                malformedEntryCount++
                                continue
                            }
                            val level = a.dbl("levelDb")
                            if (level == null) {
                                invalidLevelCount++
                                continue
                            }
                            agc.add(AgcEventSample(ns, level, a.int("constellationType"), a.dbl("carrierFrequencyHz")))
                        }
                    }
                }
            }
            return IntegrityInputs(
                measurements, clocks, agc,
                AgcParseDiagnostics(
                    eventsSeen = eventsSeen,
                    unsupportedOrMissingArrayEvents = unsupportedOrMissingArrayEvents,
                    emptyArrayEvents = emptyArrayEvents,
                    malformedEntryCount = malformedEntryCount,
                    invalidLevelCount = invalidLevelCount,
                    validControlCount = agc.size
                )
            )
        }
    }
}
