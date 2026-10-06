package horizon.observatory.domain.gnss

/**
 * Stable key for correlating GNSS observations across Android callbacks.
 * SVID is not globally unique, so constellation is mandatory. Frequency is optional
 * because some Android status/measurement records do not expose it.
 */
data class SatelliteCorrelationKey(
    val constellationType: Int,
    val svid: Int,
    val carrierFrequencyHz: Long? = null
)

enum class SatelliteEvidenceMatch {
    STATUS_AND_RAW_MATCH,
    STATUS_ONLY,
    RAW_ONLY
}

enum class NavigationAssociation {
    ASSOCIATED,
    AMBIGUOUS,
    NOT_ASSOCIATED
}

data class SatelliteEvidence(
    val key: SatelliteCorrelationKey,
    val constellationType: Int,
    val svid: Int,
    val carrierFrequencyHz: Double?,
    val cn0DbHz: Double?,
    val basebandCn0DbHz: Double?,
    val elevationDegrees: Double?,
    val azimuthDegrees: Double?,
    val usedInFix: Boolean?,
    val measurementState: Int?,
    val multipathIndicator: Int?,
    val accumulatedDeltaRangeMeters: Double?,
    val accumulatedDeltaRangeState: Int?,
    val automaticGainControlLevelDb: Double?,
    val navigationMessageCount: Int,
    val navigationAssociation: NavigationAssociation,
    val lastNavigationMessageMonotonicTimestampNs: Long?,
    val observationMonotonicTimestampNs: Long?,
    val statusMonotonicTimestampNs: Long?,
    val rawSequenceNumber: Long?,
    val statusSequenceNumber: Long?,
    val match: SatelliteEvidenceMatch,
    val provenance: String = "GNSS_CORRELATION_DERIVED"
)

data class GnssAntennaEvidence(
    val carrierFrequencyMHz: Double?,
    val phaseCenterOffset: String?,
    val phaseCenterVariationCorrections: String?,
    val signalGainCorrections: String?,
    val monotonicTimestampNs: Long?,
    val capabilityState: String,
    val provenance: String = "GNSS_ANTENNA_RAW"
)

/** One correlated observation point for GNSS history/plotting. */
data class SatelliteHistoryPoint(
    val sequenceNumber: Long?,
    val constellationType: Int,
    val svid: Int,
    val carrierFrequencyHz: Double?,
    val cn0DbHz: Double?,
    val basebandCn0DbHz: Double?,
    val elevationDegrees: Double?,
    val azimuthDegrees: Double?,
    val usedInFix: Boolean?,
    val agcDb: Double?,
    val navigationMessageCount: Int,
    val match: SatelliteEvidenceMatch,
    val monotonicTimestampNs: Long?
)

data class ConstellationSummary(
    val constellationType: Int,
    val evidenceCount: Int,
    val distinctSatelliteCount: Int,
    val matchedCount: Int,
    val statusOnlyCount: Int,
    val rawOnlyCount: Int,
    val navigationMessageCount: Int,
    val averageCn0DbHz: Double?,
    val averageElevationDegrees: Double?,
    val averageAgcDb: Double?
)

data class GnssSignalStatistics(
    val sampleCount: Int,
    val averageCn0DbHz: Double?,
    val minCn0DbHz: Double?,
    val maxCn0DbHz: Double?,
    val averageElevationDegrees: Double?,
    val minElevationDegrees: Double?,
    val maxElevationDegrees: Double?,
    val averageAgcDb: Double?,
    val minAgcDb: Double?,
    val maxAgcDb: Double?,
    val distinctFrequenciesHz: List<Double>
)

data class GnssDomainSnapshot(
    val signalStatistics: GnssSignalStatistics,
    val satelliteEvidence: List<SatelliteEvidence>,
    val latestSatelliteEvidence: List<SatelliteEvidence>,
    val historyPoints: List<SatelliteHistoryPoint>,
    val constellationSummaries: List<ConstellationSummary>,
    val antennaEvidence: List<GnssAntennaEvidence>,
    val navigationMessageCount: Int,
    val correlatedMeasurementCount: Int,
    val statusOnlySatelliteCount: Int,
    val rawOnlySatelliteCount: Int
)
