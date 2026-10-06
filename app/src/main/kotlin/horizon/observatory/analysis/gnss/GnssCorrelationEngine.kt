package horizon.observatory.analysis.gnss

import horizon.observatory.domain.gnss.GnssAntennaEvidence
import horizon.observatory.domain.gnss.GnssDomainSnapshot
import horizon.observatory.domain.gnss.NavigationAssociation
import horizon.observatory.domain.gnss.SatelliteCorrelationKey
import horizon.observatory.domain.gnss.SatelliteEvidence
import horizon.observatory.domain.gnss.SatelliteEvidenceMatch
import horizon.observatory.domain.gnss.SatelliteHistoryPoint
import horizon.observatory.domain.gnss.ConstellationSummary
import horizon.observatory.domain.gnss.GnssSignalStatistics
import horizon.observatory.storage.entity.ObservationEntity
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.round

/**
 * Correlates independent Android GNSS callback streams into a derived, time-aware satellite
 * evidence series. Raw observations remain untouched; every SatelliteEvidence item is derived.
 */
class GnssCorrelationEngine(
    private val statusMatchWindowNs: Long = 2_000_000_000L,
    private val navigationMatchWindowNs: Long = 5_000_000_000L
) {
    fun buildSnapshot(observations: List<ObservationEntity>): GnssDomainSnapshot {
        val statusRows = observations
            .asSequence()
            .filter { it.type == "GNSS_STATUS" }
            .flatMap { parseStatus(it).orEmpty().asSequence() }
            .toList()

        val rawRows = observations
            .asSequence()
            .filter { it.type == "GNSS_RAW_MEASUREMENT" }
            .mapNotNull(::parseRaw)
            .toList()

        val navRows = observations
            .asSequence()
            .filter { it.type == "GNSS_NAVIGATION_MESSAGE" }
            .mapNotNull(::parseNav)
            .toList()

        val antenna = observations
            .asSequence()
            .filter { it.type == "GNSS_ANTENNA_INFO" }
            .map(::parseAntenna)
            .toList()

        val statusByBaseKey = statusRows.groupBy { BaseKey(it.constellationType, it.svid) }
        val latestStatusByBaseKey = latestStatus(statusRows)
        val matchedRawSequences = mutableSetOf<Long>()
        val evidence = mutableListOf<SatelliteEvidence>()

        rawRows.sortedBy { it.sequenceNumber }.forEach { raw ->
            val candidate = selectBestStatus(statusByBaseKey[BaseKey(raw.constellationType, raw.svid)].orEmpty(), raw)
            val nav = associateNavigation(raw, candidate, statusRows, navRows)
            if (candidate != null) matchedRawSequences += raw.sequenceNumber

            evidence += SatelliteEvidence(
                key = SatelliteCorrelationKey(raw.constellationType, raw.svid, raw.carrierFrequencyHz?.roundToLongSafe()),
                constellationType = raw.constellationType,
                svid = raw.svid,
                carrierFrequencyHz = raw.carrierFrequencyHz ?: candidate?.carrierFrequencyHz,
                cn0DbHz = raw.cn0DbHz ?: candidate?.cn0DbHz,
                basebandCn0DbHz = raw.basebandCn0DbHz ?: candidate?.basebandCn0DbHz,
                elevationDegrees = candidate?.elevationDegrees,
                azimuthDegrees = candidate?.azimuthDegrees,
                usedInFix = candidate?.usedInFix,
                measurementState = raw.state,
                multipathIndicator = raw.multipathIndicator,
                accumulatedDeltaRangeMeters = raw.accumulatedDeltaRangeMeters,
                accumulatedDeltaRangeState = raw.accumulatedDeltaRangeState,
                automaticGainControlLevelDb = raw.agcDb,
                navigationMessageCount = nav.count,
                navigationAssociation = nav.association,
                lastNavigationMessageMonotonicTimestampNs = nav.lastMonotonicTimestampNs,
                observationMonotonicTimestampNs = raw.monotonicTimestampNs,
                statusMonotonicTimestampNs = candidate?.monotonicTimestampNs,
                rawSequenceNumber = raw.sequenceNumber,
                statusSequenceNumber = candidate?.sequenceNumber,
                match = if (candidate == null) SatelliteEvidenceMatch.RAW_ONLY else SatelliteEvidenceMatch.STATUS_AND_RAW_MATCH
            )
        }

        // Preserve a current status view for satellites that have no raw measurement in the same
        // bounded window. This is still observed status data, not a synthesized measurement.
        latestStatusByBaseKey.values.forEach { status ->
            val hasRecentRaw = evidence.any {
                it.constellationType == status.constellationType &&
                    it.svid == status.svid &&
                    status.monotonicTimestampNs != null &&
                    it.observationMonotonicTimestampNs != null &&
                    timeDistance(status.monotonicTimestampNs, it.observationMonotonicTimestampNs) <= statusMatchWindowNs
            }
            if (!hasRecentRaw) {
                evidence += SatelliteEvidence(
                    key = SatelliteCorrelationKey(status.constellationType, status.svid, status.carrierFrequencyHz?.roundToLongSafe()),
                    constellationType = status.constellationType,
                    svid = status.svid,
                    carrierFrequencyHz = status.carrierFrequencyHz,
                    cn0DbHz = status.cn0DbHz,
                    basebandCn0DbHz = status.basebandCn0DbHz,
                    elevationDegrees = status.elevationDegrees,
                    azimuthDegrees = status.azimuthDegrees,
                    usedInFix = status.usedInFix,
                    measurementState = null,
                    multipathIndicator = null,
                    accumulatedDeltaRangeMeters = null,
                    accumulatedDeltaRangeState = null,
                    automaticGainControlLevelDb = null,
                    navigationMessageCount = 0,
                    navigationAssociation = NavigationAssociation.NOT_ASSOCIATED,
                    lastNavigationMessageMonotonicTimestampNs = null,
                    observationMonotonicTimestampNs = null,
                    statusMonotonicTimestampNs = status.monotonicTimestampNs,
                    rawSequenceNumber = null,
                    statusSequenceNumber = status.sequenceNumber,
                    match = SatelliteEvidenceMatch.STATUS_ONLY
                )
            }
        }

        val ordered = evidence.sortedWith(
            compareBy<SatelliteEvidence>(
                { it.observationMonotonicTimestampNs ?: Long.MAX_VALUE },
                { it.constellationType },
                { it.svid },
                { it.rawSequenceNumber ?: Long.MAX_VALUE }
            )
        )

        val latestBySatellite = ordered
            .groupBy { BaseKey(it.constellationType, it.svid) }
            .values
            .mapNotNull { values ->
                values.maxWithOrNull(compareBy<SatelliteEvidence>(
                    { it.observationMonotonicTimestampNs ?: it.statusMonotonicTimestampNs ?: Long.MIN_VALUE },
                    { it.rawSequenceNumber ?: it.statusSequenceNumber ?: Long.MIN_VALUE }
                ))
            }
            .sortedWith(compareBy<SatelliteEvidence>({ it.constellationType }, { it.svid }))

        val history = ordered.map { item ->
            SatelliteHistoryPoint(
                sequenceNumber = item.rawSequenceNumber ?: item.statusSequenceNumber,
                constellationType = item.constellationType,
                svid = item.svid,
                carrierFrequencyHz = item.carrierFrequencyHz,
                cn0DbHz = item.cn0DbHz,
                basebandCn0DbHz = item.basebandCn0DbHz,
                elevationDegrees = item.elevationDegrees,
                azimuthDegrees = item.azimuthDegrees,
                usedInFix = item.usedInFix,
                agcDb = item.automaticGainControlLevelDb,
                navigationMessageCount = item.navigationMessageCount,
                match = item.match,
                monotonicTimestampNs = item.observationMonotonicTimestampNs ?: item.statusMonotonicTimestampNs
            )
        }

        val cn0Values = ordered.mapNotNull { it.cn0DbHz }
        val elevationValues = ordered.mapNotNull { it.elevationDegrees }
        val agcValues = ordered.mapNotNull { it.automaticGainControlLevelDb }
        val signalStatistics = GnssSignalStatistics(
            sampleCount = cn0Values.size,
            averageCn0DbHz = cn0Values.averageOrNull(),
            minCn0DbHz = cn0Values.minOrNull(),
            maxCn0DbHz = cn0Values.maxOrNull(),
            averageElevationDegrees = elevationValues.averageOrNull(),
            minElevationDegrees = elevationValues.minOrNull(),
            maxElevationDegrees = elevationValues.maxOrNull(),
            averageAgcDb = agcValues.averageOrNull(),
            minAgcDb = agcValues.minOrNull(),
            maxAgcDb = agcValues.maxOrNull(),
            distinctFrequenciesHz = ordered.mapNotNull { it.carrierFrequencyHz }
                .distinct()
                .sorted()
        )

        val summaries = ordered.groupBy { it.constellationType }
            .map { (constellationType, values) ->
                ConstellationSummary(
                    constellationType = constellationType,
                    evidenceCount = values.size,
                    distinctSatelliteCount = values.map { it.svid }.toSet().size,
                    matchedCount = values.count { it.match == SatelliteEvidenceMatch.STATUS_AND_RAW_MATCH },
                    statusOnlyCount = values.count { it.match == SatelliteEvidenceMatch.STATUS_ONLY },
                    rawOnlyCount = values.count { it.match == SatelliteEvidenceMatch.RAW_ONLY },
                    navigationMessageCount = values.sumOf { it.navigationMessageCount },
                    averageCn0DbHz = values.mapNotNull { it.cn0DbHz }.averageOrNull(),
                    averageElevationDegrees = values.mapNotNull { it.elevationDegrees }.averageOrNull(),
                    averageAgcDb = values.mapNotNull { it.automaticGainControlLevelDb }.averageOrNull()
                )
            }
            .sortedBy { it.constellationType }

        return GnssDomainSnapshot(
            signalStatistics = signalStatistics,
            satelliteEvidence = ordered,
            latestSatelliteEvidence = latestBySatellite,
            historyPoints = history,
            constellationSummaries = summaries,
            antennaEvidence = antenna,
            navigationMessageCount = navRows.size,
            correlatedMeasurementCount = matchedRawSequences.size,
            statusOnlySatelliteCount = ordered.count { it.match == SatelliteEvidenceMatch.STATUS_ONLY },
            rawOnlySatelliteCount = ordered.count { it.match == SatelliteEvidenceMatch.RAW_ONLY }
        )
    }

    private fun selectBestStatus(rows: List<StatusRecord>, raw: RawRecord): StatusRecord? {
        if (rows.isEmpty()) return null
        return rows
            .filter { status -> timeDistance(status.monotonicTimestampNs, raw.monotonicTimestampNs) <= statusMatchWindowNs }
            .minWithOrNull(compareBy<StatusRecord>(
                { frequencyPenalty(it.carrierFrequencyHz, raw.carrierFrequencyHz) },
                { timeDistance(it.monotonicTimestampNs, raw.monotonicTimestampNs) }
            ))
    }

    private fun frequencyPenalty(statusHz: Double?, rawHz: Double?): Int {
        if (statusHz == null || rawHz == null) return 0
        return if (abs(statusHz - rawHz) < 1.0) 0 else 1
    }

    private fun associateNavigation(
        raw: RawRecord,
        selectedStatus: StatusRecord?,
        allStatusRows: List<StatusRecord>,
        navRows: List<NavRecord>
    ): NavigationMatch {
        if (navRows.isEmpty()) return NavigationMatch(0, NavigationAssociation.NOT_ASSOCIATED, null)
        val referenceTime = raw.monotonicTimestampNs ?: selectedStatus?.monotonicTimestampNs
            ?: return NavigationMatch(0, NavigationAssociation.NOT_ASSOCIATED, null)
        val candidates = navRows.filter {
            it.svid == raw.svid && timeDistance(it.monotonicTimestampNs, referenceTime) <= navigationMatchWindowNs
        }
        if (candidates.isEmpty()) return NavigationMatch(0, NavigationAssociation.NOT_ASSOCIATED, null)

        // GnssNavigationMessage does not expose constellation identity in our persisted payload.
        // Resolve only when the surrounding status evidence gives a single constellation for this SVID.
        val constellationCandidates = allStatusRows
            .asSequence()
            .filter { it.svid == raw.svid && timeDistance(it.monotonicTimestampNs, referenceTime) <= navigationMatchWindowNs }
            .map { it.constellationType }
            .distinct()
            .toList()

        val association = when {
            selectedStatus == null -> NavigationAssociation.AMBIGUOUS
            constellationCandidates.size == 1 && constellationCandidates.first() == selectedStatus.constellationType -> NavigationAssociation.ASSOCIATED
            else -> NavigationAssociation.AMBIGUOUS
        }

        val unique = candidates.distinctBy { it.sequenceNumber }
        return NavigationMatch(
            count = unique.size,
            association = association,
            lastMonotonicTimestampNs = unique.maxByOrNull { it.monotonicTimestampNs ?: Long.MIN_VALUE }?.monotonicTimestampNs
        )
    }

    private fun latestStatus(rows: List<StatusRecord>): Map<BaseKey, StatusRecord> =
        rows.sortedBy { it.sequenceNumber }.associateBy { BaseKey(it.constellationType, it.svid) }

    private fun parseStatus(row: ObservationEntity): List<StatusRecord>? {
        return runCatching {
            val root = JSONObject(row.rawPayloadJson)
            val array = root.optJSONArray("satellites") ?: return@runCatching null
            val result = buildList<StatusRecord> {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        StatusRecord(
                            sequenceNumber = row.sequenceNumber,
                            constellationType = item.optInt("constellationType", -1),
                            svid = item.optInt("svid", -1),
                            carrierFrequencyHz = item.doubleOrNull("carrierFrequencyHz"),
                            cn0DbHz = item.doubleOrNull("cn0DbHz"),
                            basebandCn0DbHz = item.doubleOrNull("basebandCn0DbHz"),
                            elevationDegrees = item.doubleOrNull("elevationDegrees"),
                            azimuthDegrees = item.doubleOrNull("azimuthDegrees"),
                            usedInFix = item.optBoolean("usedInFix", false),
                            monotonicTimestampNs = row.monotonicTimestampNs
                        )
                    )
                }
            }
            result
        }.getOrNull()
    }

    private fun parseRaw(row: ObservationEntity): RawRecord? = runCatching {
        val root = JSONObject(row.rawPayloadJson)
        RawRecord(
            sequenceNumber = row.sequenceNumber,
            constellationType = root.optInt("constellationType", -1),
            svid = root.optInt("svid", -1),
            carrierFrequencyHz = root.doubleOrNull("carrierFrequencyHz"),
            cn0DbHz = root.doubleOrNull("cn0DbHz"),
            basebandCn0DbHz = root.doubleOrNull("basebandCn0DbHz"),
            state = root.intOrNull("state"),
            multipathIndicator = root.intOrNull("multipathIndicator"),
            accumulatedDeltaRangeMeters = root.doubleOrNull("accumulatedDeltaRangeMeters"),
            accumulatedDeltaRangeState = root.intOrNull("accumulatedDeltaRangeState"),
            agcDb = root.doubleOrNull("automaticGainControlLevelDb"),
            monotonicTimestampNs = row.monotonicTimestampNs
        )
    }.getOrNull()

    private fun parseNav(row: ObservationEntity): NavRecord? = runCatching {
        val root = JSONObject(row.rawPayloadJson)
        NavRecord(
            sequenceNumber = row.sequenceNumber,
            svid = root.optInt("svid", -1),
            monotonicTimestampNs = row.monotonicTimestampNs
        )
    }.getOrNull()

    private fun parseAntenna(row: ObservationEntity): GnssAntennaEvidence = runCatching {
        val root = JSONObject(row.rawPayloadJson)
        GnssAntennaEvidence(
            carrierFrequencyMHz = root.doubleOrNull("carrierFrequencyMHz"),
            phaseCenterOffset = root.stringOrNull("phaseCenterOffset"),
            phaseCenterVariationCorrections = root.stringOrNull("phaseCenterVariationCorrections"),
            signalGainCorrections = root.stringOrNull("signalGainCorrections"),
            monotonicTimestampNs = row.monotonicTimestampNs,
            capabilityState = row.capabilityState
        )
    }.getOrElse {
        GnssAntennaEvidence(null, null, null, null, row.monotonicTimestampNs, row.capabilityState)
    }

    private data class BaseKey(val constellationType: Int, val svid: Int)
    private data class StatusRecord(
        val sequenceNumber: Long,
        val constellationType: Int,
        val svid: Int,
        val carrierFrequencyHz: Double?,
        val cn0DbHz: Double?,
        val basebandCn0DbHz: Double?,
        val elevationDegrees: Double?,
        val azimuthDegrees: Double?,
        val usedInFix: Boolean,
        val monotonicTimestampNs: Long?
    )
    private data class RawRecord(
        val sequenceNumber: Long,
        val constellationType: Int,
        val svid: Int,
        val carrierFrequencyHz: Double?,
        val cn0DbHz: Double?,
        val basebandCn0DbHz: Double?,
        val state: Int?,
        val multipathIndicator: Int?,
        val accumulatedDeltaRangeMeters: Double?,
        val accumulatedDeltaRangeState: Int?,
        val agcDb: Double?,
        val monotonicTimestampNs: Long?
    )
    private data class NavRecord(
        val sequenceNumber: Long,
        val svid: Int,
        val monotonicTimestampNs: Long?
    )
    private data class NavigationMatch(
        val count: Int,
        val association: NavigationAssociation,
        val lastMonotonicTimestampNs: Long?
    )

    private fun JSONObject.doubleOrNull(key: String): Double? = when (val value = opt(key)) {
        is Number -> value.toDouble()
        else -> null
    }

    private fun JSONObject.intOrNull(key: String): Int? = when (val value = opt(key)) {
        is Number -> value.toInt()
        else -> null
    }

    private fun JSONObject.stringOrNull(key: String): String? = when (val value = opt(key)) {
        is String -> value
        else -> null
    }

    private fun Double.roundToLongSafe(): Long = round(this).toLong()

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

    private fun timeDistance(a: Long?, b: Long?): Long {
        if (a == null || b == null) return Long.MAX_VALUE
        return if (a >= b) a - b else b - a
    }
}
