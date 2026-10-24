package horizon.observatory.export

import horizon.observatory.domain.gnss.ConstellationSummary
import horizon.observatory.domain.gnss.GnssAntennaEvidence
import horizon.observatory.domain.gnss.GnssDomainSnapshot
import horizon.observatory.domain.gnss.SatelliteEvidence
import horizon.observatory.domain.gnss.SatelliteHistoryPoint
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File

/**
 * Writes a potentially long derived GNSS snapshot incrementally.
 *
 * The previous exporter assembled every result list into nested JSONArrays and then
 * serialized the entire JSONObject to one String. That duplicated the largest report
 * payloads in memory. This writer retains the input snapshot (owned by correlation) and
 * emits one JSON element at a time.
 */
internal object GnssDomainSnapshotJsonWriter {
    suspend fun write(file: File, snapshot: GnssDomainSnapshot) {
        val stats = snapshot.signalStatistics
        file.bufferedWriter().use { writer ->
            writer.append("{\n")
            appendField(writer, "sampleCount", stats.sampleCount, true)
            appendField(writer, "averageCn0DbHz", stats.averageCn0DbHz, true)
            appendField(writer, "minCn0DbHz", stats.minCn0DbHz, true)
            appendField(writer, "maxCn0DbHz", stats.maxCn0DbHz, true)
            appendField(writer, "averageElevationDegrees", stats.averageElevationDegrees, true)
            appendField(writer, "minElevationDegrees", stats.minElevationDegrees, true)
            appendField(writer, "maxElevationDegrees", stats.maxElevationDegrees, true)
            appendField(writer, "averageAgcDb", stats.averageAgcDb, true)
            appendField(writer, "minAgcDb", stats.minAgcDb, true)
            appendField(writer, "maxAgcDb", stats.maxAgcDb, true)
            appendArrayField(writer, "distinctFrequenciesHz", stats.distinctFrequenciesHz, { encodeJsonScalar(it) }, true)
            appendField(writer, "navigationMessageCount", snapshot.navigationMessageCount, true)
            appendField(writer, "correlatedMeasurementCount", snapshot.correlatedMeasurementCount, true)
            appendField(writer, "statusOnlySatelliteCount", snapshot.statusOnlySatelliteCount, true)
            appendField(writer, "rawOnlySatelliteCount", snapshot.rawOnlySatelliteCount, true)
            appendArrayField(writer, "satelliteEvidence", snapshot.satelliteEvidence, { it.toSnapshotJson().toString() }, true)
            appendArrayField(writer, "latestSatelliteEvidence", snapshot.latestSatelliteEvidence, { it.toSnapshotJson().toString() }, true)
            appendArrayField(writer, "historyPoints", snapshot.historyPoints, { it.toSnapshotJson().toString() }, true)
            appendArrayField(writer, "constellationSummaries", snapshot.constellationSummaries, { it.toSnapshotJson().toString() }, true)
            appendArrayField(writer, "antennaEvidence", snapshot.antennaEvidence, { it.toSnapshotJson().toString() }, true)
            appendField(writer, "provenance", "DERIVED_FROM_PERSISTED_GNSS_OBSERVATIONS", false)
            writer.append("}\n")
        }
    }

    suspend fun writeAntennaEvidence(file: File, evidence: List<GnssAntennaEvidence>): Int {
        val sink = StreamingJsonArrayWriter.open(file)
        sink.use { writer ->
            evidence.forEachIndexed { index, item ->
                if (index % PAGE_SIZE == 0) currentCoroutineContext().ensureActive()
                writer.append(item.toSnapshotJson())
            }
            writer.finish()
            return writer.count
        }
    }

    private suspend fun <T> appendArrayField(
        writer: BufferedWriter,
        name: String,
        values: Iterable<T>,
        serialize: (T) -> String,
        commaAfter: Boolean
    ) {
        writer.append("  ").append(JSONObject.quote(name)).append(": [\n")
        val iterator = values.iterator()
        var index = 0
        while (iterator.hasNext()) {
            if (index % PAGE_SIZE == 0) currentCoroutineContext().ensureActive()
            writer.append("    ").append(serialize(iterator.next()))
            if (iterator.hasNext()) writer.append(',')
            writer.append('\n')
            index += 1
        }
        writer.append("  ]")
        if (commaAfter) writer.append(',')
        writer.append('\n')
    }

    private fun appendField(writer: BufferedWriter, name: String, value: Any?, commaAfter: Boolean) {
        val encoded = JSONObject().put(name, value ?: JSONObject.NULL).toString()
        writer.append("  ").append(encoded, 1, encoded.length - 1)
        if (commaAfter) writer.append(',')
        writer.append('\n')
    }

    private fun encodeJsonScalar(value: Double): String =
        JSONObject().put("value", value).toString().substringAfter(':').dropLast(1)

    private fun SatelliteEvidence.toSnapshotJson(): JSONObject = JSONObject().apply {
        put("constellationType", constellationType)
        put("svid", svid)
        put("carrierFrequencyHz", carrierFrequencyHz ?: JSONObject.NULL)
        put("cn0DbHz", cn0DbHz ?: JSONObject.NULL)
        put("basebandCn0DbHz", basebandCn0DbHz ?: JSONObject.NULL)
        put("elevationDegrees", elevationDegrees ?: JSONObject.NULL)
        put("azimuthDegrees", azimuthDegrees ?: JSONObject.NULL)
        put("usedInFix", usedInFix ?: JSONObject.NULL)
        put("measurementState", measurementState ?: JSONObject.NULL)
        put("multipathIndicator", multipathIndicator ?: JSONObject.NULL)
        put("accumulatedDeltaRangeMeters", accumulatedDeltaRangeMeters ?: JSONObject.NULL)
        put("accumulatedDeltaRangeState", accumulatedDeltaRangeState ?: JSONObject.NULL)
        put("automaticGainControlLevelDb", automaticGainControlLevelDb ?: JSONObject.NULL)
        put("navigationMessageCount", navigationMessageCount)
        put("navigationAssociation", navigationAssociation.name)
        put("observationMonotonicTimestampNs", observationMonotonicTimestampNs ?: JSONObject.NULL)
        put("statusMonotonicTimestampNs", statusMonotonicTimestampNs ?: JSONObject.NULL)
        put("rawSequenceNumber", rawSequenceNumber ?: JSONObject.NULL)
        put("statusSequenceNumber", statusSequenceNumber ?: JSONObject.NULL)
        put("match", match.name)
        put("provenance", provenance)
    }

    private fun SatelliteHistoryPoint.toSnapshotJson(): JSONObject = JSONObject().apply {
        put("sequenceNumber", sequenceNumber ?: JSONObject.NULL)
        put("constellationType", constellationType)
        put("svid", svid)
        put("carrierFrequencyHz", carrierFrequencyHz ?: JSONObject.NULL)
        put("cn0DbHz", cn0DbHz ?: JSONObject.NULL)
        put("basebandCn0DbHz", basebandCn0DbHz ?: JSONObject.NULL)
        put("elevationDegrees", elevationDegrees ?: JSONObject.NULL)
        put("azimuthDegrees", azimuthDegrees ?: JSONObject.NULL)
        put("usedInFix", usedInFix ?: JSONObject.NULL)
        put("agcDb", agcDb ?: JSONObject.NULL)
        put("navigationMessageCount", navigationMessageCount)
        put("match", match.name)
        put("monotonicTimestampNs", monotonicTimestampNs ?: JSONObject.NULL)
    }

    private fun ConstellationSummary.toSnapshotJson(): JSONObject = JSONObject().apply {
        put("constellationType", constellationType)
        put("evidenceCount", evidenceCount)
        put("distinctSatelliteCount", distinctSatelliteCount)
        put("matchedCount", matchedCount)
        put("statusOnlyCount", statusOnlyCount)
        put("rawOnlyCount", rawOnlyCount)
        put("navigationMessageCount", navigationMessageCount)
        put("averageCn0DbHz", averageCn0DbHz ?: JSONObject.NULL)
        put("averageElevationDegrees", averageElevationDegrees ?: JSONObject.NULL)
        put("averageAgcDb", averageAgcDb ?: JSONObject.NULL)
    }

    private fun GnssAntennaEvidence.toSnapshotJson(): JSONObject = JSONObject().apply {
        put("carrierFrequencyMHz", carrierFrequencyMHz ?: JSONObject.NULL)
        put("phaseCenterOffset", phaseCenterOffset ?: JSONObject.NULL)
        put("phaseCenterVariationCorrections", phaseCenterVariationCorrections ?: JSONObject.NULL)
        put("signalGainCorrections", signalGainCorrections ?: JSONObject.NULL)
        put("monotonicTimestampNs", monotonicTimestampNs ?: JSONObject.NULL)
        put("capabilityState", capabilityState)
        put("provenance", provenance)
    }

    private const val PAGE_SIZE = 256
}
