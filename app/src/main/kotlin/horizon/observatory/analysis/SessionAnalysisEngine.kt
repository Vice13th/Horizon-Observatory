package horizon.observatory.analysis

import horizon.observatory.analysis.gnss.GnssCorrelationEngine
import horizon.observatory.domain.gnss.GnssDomainSnapshot
import horizon.observatory.storage.entity.ObservationEntity
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.sqrt

class SessionAnalysisEngine {
    private val gnssCorrelationEngine = GnssCorrelationEngine()

    fun analyze(observations: List<ObservationEntity>): AnalysisSnapshot {
        val gnssRaw = observations.filter { it.type == "GNSS_RAW_MEASUREMENT" }
        val gnssStatus = observations.filter { it.type == "GNSS_STATUS" }
        val gnssFix = observations.filter { it.type == "GNSS_FIX" }
        val gnssNav = observations.filter { it.type == "GNSS_NAVIGATION_MESSAGE" }
        val cellular = observations.filter { it.type == "CELLULAR_INFO" }
        val sensors = observations.filter { it.source == "SENSOR" }
        val gnssDomain = gnssCorrelationEngine.buildSnapshot(observations)

        val cno = gnssRaw.mapNotNull { it.rawPayloadJson.doubleOrNull("cn0DbHz") }
        val agc = gnssRaw.mapNotNull { it.rawPayloadJson.doubleOrNull("automaticGainControlLevelDb") }
        val rsrpAll = cellular.mapNotNull {
            it.rawPayloadJson.doubleOrNull("rsrp") ?: it.rawPayloadJson.doubleOrNull("ssRsrp")
        }
        val servingCellular = cellular.filter { it.isServingCell() }
        val rsrpServing = servingCellular.mapNotNull {
            it.rawPayloadJson.doubleOrNull("rsrp") ?: it.rawPayloadJson.doubleOrNull("ssRsrp")
        }
        val rsrp = if (rsrpServing.isNotEmpty()) rsrpServing else rsrpAll

        val satelliteCounts = gnssStatus.mapNotNull { it.rawPayloadJson.doubleOrNull("satelliteCount") }
        val latestStatus = gnssStatus.maxByOrNull { it.sequenceNumber }
        val latestSatelliteCount = latestStatus?.rawPayloadJson?.doubleOrNull("satelliteCount")?.toInt()
        val distinctSatelliteKeys: Set<String> = latestStatus?.rawPayloadJson?.satelliteKeys() ?: emptySet()
        val multiSatelliteEpochs = gnssStatus.count { (it.rawPayloadJson.doubleOrNull("satelliteCount") ?: 0.0) > 1.0 }

        val latestServing = servingCellular.maxByOrNull { it.sequenceNumber }
            ?: cellular.maxByOrNull { it.sequenceNumber }
        val latestRsrp = latestServing?.rawPayloadJson?.doubleOrNull("rsrp")
            ?: latestServing?.rawPayloadJson?.doubleOrNull("ssRsrp")
        val latestTechnology = latestServing?.technology
        val latestAgeMs = latestServing?.rawPayloadJson?.longOrNull("sourceAgeMs")
        val freshRequestSamples = cellular.count { it.rawPayloadJson.stringOrNull("acquisitionTrigger") == "REQUEST_CELL_INFO_UPDATE" }
        val callbackSamples = cellular.count {
            it.rawPayloadJson.stringOrNull("acquisitionTrigger") == "TELEPHONY_CALLBACK" ||
                it.rawPayloadJson.stringOrNull("acquisitionTrigger") == "PHONE_STATE_CALLBACK"
        }

        val gaps = sequenceGaps(observations)
        val monotonicViolations = monotonicViolations(observations)
        val agcMin = agc.minOrNull()
        val agcMax = agc.maxOrNull()
        val agcSpread = if (agcMin != null && agcMax != null) agcMax - agcMin else null
        val agcDistinct = agc.map { String.format("%.3f", it) }.distinct().size
        val agcState = when {
            agc.isEmpty() -> "UNAVAILABLE"
            agc.all { abs(it - agc.first()) < 1e-9 } -> "EXACTLY_CONSTANT"
            else -> "VARYING"
        }

        return AnalysisSnapshot(
            observationCount = observations.size,
            gnssRawCount = gnssRaw.size,
            gnssStatusCount = gnssStatus.size,
            gnssFixCount = gnssFix.size,
            cellularCount = cellular.size,
            sensorCount = sensors.size,
            averageCn0DbHz = cno.averageOrNull(),
            averageRsrpDbm = rsrp.averageOrNull(),
            averageVisibleSatellites = satelliteCounts.averageOrNull(),
            sequenceGapCount = gaps,
            timestampMonotonicViolations = monotonicViolations,
            estimatedSensorMagnitude = sensorMagnitude(observations),
            navigationMessageCount = gnssNav.size,
            agcSampleCount = agc.size,
            agcMinDb = agcMin,
            agcMaxDb = agcMax,
            agcSpreadDb = agcSpread,
            agcDistinctLevels = agcDistinct,
            agcState = agcState,
            latestSatelliteCount = latestSatelliteCount,
            distinctSatelliteCount = distinctSatelliteKeys.size,
            multiSatelliteEpochs = multiSatelliteEpochs,
            latestServingRsrpDbm = latestRsrp,
            latestServingTechnology = latestTechnology,
            latestCellAgeMs = latestAgeMs,
            freshCellRequestSamples = freshRequestSamples,
            cellularCallbackSamples = callbackSamples,
            gnssDomain = gnssDomain
        )
    }

    fun timeline(observations: List<ObservationEntity>): List<TimelinePoint> =
        observations.sortedWith(compareBy<ObservationEntity>({ it.monotonicTimestampNs ?: Long.MAX_VALUE }, { it.timestampUtcMs }, { it.sequenceNumber }))
            .map { TimelinePoint(it.sequenceNumber, it.timestampUtcMs, it.monotonicTimestampNs, it.type, it.technology) }

    private fun sequenceGaps(observations: List<ObservationEntity>): Int {
        val seq = observations.map { it.sequenceNumber }.sorted()
        if (seq.isEmpty()) return 0
        var gaps = 0
        var expected = seq.first()
        seq.forEach {
            if (it != expected) gaps++
            expected = it + 1
        }
        return gaps
    }

    private fun monotonicViolations(observations: List<ObservationEntity>): Int {
        var previous: Long? = null
        var violations = 0
        observations.sortedBy { it.sequenceNumber }.forEach { item ->
            val current = item.monotonicTimestampNs ?: return@forEach
            if (previous != null && current < previous!!) violations++
            previous = current
        }
        return violations
    }

    private fun sensorMagnitude(observations: List<ObservationEntity>): Double? {
        val rows = observations.filter { it.source == "SENSOR" }.mapNotNull {
            val json = JSONObject(it.rawPayloadJson)
            val values = json.optJSONArray("values") ?: return@mapNotNull null
            if (values.length() < 3) return@mapNotNull null
            val x = values.optDouble(0, Double.NaN)
            val y = values.optDouble(1, Double.NaN)
            val z = values.optDouble(2, Double.NaN)
            if (listOf(x, y, z).any { it.isNaN() }) null else sqrt(x * x + y * y + z * z)
        }
        return rows.averageOrNull()
    }

    private fun ObservationEntity.isServingCell(): Boolean = runCatching {
        val json = JSONObject(rawPayloadJson)
        val registered = json.optBoolean("registered", false)
        val connectionStatus = json.optInt("connectionStatus", 0)
        registered || connectionStatus == 1 || connectionStatus == 2
    }.getOrDefault(false)

    private fun String.doubleOrNull(key: String): Double? = runCatching {
        val v = JSONObject(this).opt(key)
        when (v) {
            is Number -> v.toDouble()
            else -> null
        }
    }.getOrNull()

    private fun String.longOrNull(key: String): Long? = runCatching {
        val v = JSONObject(this).opt(key)
        when (v) {
            is Number -> v.toLong()
            else -> null
        }
    }.getOrNull()

    private fun String.stringOrNull(key: String): String? = runCatching {
        JSONObject(this).optString(key, null)
    }.getOrNull()

    private fun String.satelliteKeys(): Set<String> = runCatching {
        val array = JSONObject(this).optJSONArray("satellites") ?: return emptySet()
        buildSet {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add("${item.optInt("constellationType", -1)}:${item.optInt("svid", -1)}")
            }
        }
    }.getOrDefault(emptySet())

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}

data class AnalysisSnapshot(
    val observationCount: Int,
    val gnssRawCount: Int,
    val gnssStatusCount: Int,
    val gnssFixCount: Int,
    val cellularCount: Int,
    val sensorCount: Int,
    val averageCn0DbHz: Double?,
    val averageRsrpDbm: Double?,
    val averageVisibleSatellites: Double?,
    val sequenceGapCount: Int,
    val timestampMonotonicViolations: Int,
    val estimatedSensorMagnitude: Double?,
    val navigationMessageCount: Int = 0,
    val agcSampleCount: Int = 0,
    val agcMinDb: Double? = null,
    val agcMaxDb: Double? = null,
    val agcSpreadDb: Double? = null,
    val agcDistinctLevels: Int = 0,
    val agcState: String = "UNAVAILABLE",
    val latestSatelliteCount: Int? = null,
    val distinctSatelliteCount: Int = 0,
    val multiSatelliteEpochs: Int = 0,
    val latestServingRsrpDbm: Double? = null,
    val latestServingTechnology: String? = null,
    val latestCellAgeMs: Long? = null,
    val freshCellRequestSamples: Int = 0,
    val cellularCallbackSamples: Int = 0,
    val gnssDomain: GnssDomainSnapshot = GnssDomainSnapshot(
        signalStatistics = horizon.observatory.domain.gnss.GnssSignalStatistics(
            sampleCount = 0, averageCn0DbHz = null, minCn0DbHz = null, maxCn0DbHz = null,
            averageElevationDegrees = null, minElevationDegrees = null, maxElevationDegrees = null,
            averageAgcDb = null, minAgcDb = null, maxAgcDb = null, distinctFrequenciesHz = emptyList()
        ),
        satelliteEvidence = emptyList(),
        latestSatelliteEvidence = emptyList(),
        historyPoints = emptyList(),
        constellationSummaries = emptyList(),
        antennaEvidence = emptyList(),
        navigationMessageCount = 0,
        correlatedMeasurementCount = 0,
        statusOnlySatelliteCount = 0,
        rawOnlySatelliteCount = 0
    )
)

data class TimelinePoint(
    val sequence: Long,
    val timestampUtcMs: Long,
    val monotonicTimestampNs: Long?,
    val type: String,
    val technology: String
)
