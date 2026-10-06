package horizon.observatory.analysis.assistance

import horizon.observatory.storage.entity.ObservationEntity

enum class AssistanceTemporalRelationship {
    BEFORE_ACQUISITION,
    OVERLAPS_ACQUISITION,
    BEFORE_FIRST_MEASUREMENT,
    BEFORE_FIRST_PVT,
    NO_TEMPORAL_OVERLAP,
    UNKNOWN
}

data class AssistanceCorrelation(
    val assistanceObservationId: Long,
    val sessionId: String,
    val relationship: AssistanceTemporalRelationship,
    val temporalDeltaNs: Long?,
    val correlationWindowNs: Long,
    val provenance: String = "ASSISTANCE_CORRELATION_DERIVED"
)

/**
 * Separate from GnssCorrelationEngine.kt (unmodified). Monotonic-time-only; never uses
 * timestampUtcMs. Temporal correlation only -- never asserts causality.
 */
class AssistanceCorrelationEngine(private val correlationWindowNs: Long = 5_000_000_000L) {

    fun correlate(
        assistanceEvents: List<ObservationEntity>,
        gnssObservations: List<ObservationEntity>
    ): List<AssistanceCorrelation> {
        val gnss = gnssObservations.filter { it.monotonicTimestampNs != null }
        val acquisitionStart = gnss.minOfOrNull { it.monotonicTimestampNs!! }
        val acquisitionEnd = gnss.maxOfOrNull { it.monotonicTimestampNs!! }
        val firstMeasurement = gnss.filter { it.type == "GNSS_RAW_MEASUREMENT" }
            .minOfOrNull { it.monotonicTimestampNs!! }
        val firstPvt = gnss.filter { it.type == "GNSS_FIX" }
            .minOfOrNull { it.monotonicTimestampNs!! }

        return assistanceEvents.map { event ->
            val sessionId = event.sessionId
            val ts = event.monotonicTimestampNs
            if (ts == null || acquisitionStart == null || acquisitionEnd == null) {
                return@map AssistanceCorrelation(event.observationId, sessionId, AssistanceTemporalRelationship.UNKNOWN, null, correlationWindowNs)
            }
            val relationship = when {
                ts in acquisitionStart..acquisitionEnd -> AssistanceTemporalRelationship.OVERLAPS_ACQUISITION
                firstPvt != null && ts < firstPvt && (firstPvt - ts) <= correlationWindowNs -> AssistanceTemporalRelationship.BEFORE_FIRST_PVT
                firstMeasurement != null && ts < firstMeasurement && (firstMeasurement - ts) <= correlationWindowNs -> AssistanceTemporalRelationship.BEFORE_FIRST_MEASUREMENT
                ts < acquisitionStart && (acquisitionStart - ts) <= correlationWindowNs -> AssistanceTemporalRelationship.BEFORE_ACQUISITION
                else -> AssistanceTemporalRelationship.NO_TEMPORAL_OVERLAP
            }
            val delta = when (relationship) {
                AssistanceTemporalRelationship.BEFORE_FIRST_PVT -> firstPvt!! - ts
                AssistanceTemporalRelationship.BEFORE_FIRST_MEASUREMENT -> firstMeasurement!! - ts
                AssistanceTemporalRelationship.BEFORE_ACQUISITION -> acquisitionStart - ts
                AssistanceTemporalRelationship.OVERLAPS_ACQUISITION -> 0L
                else -> null
            }
            AssistanceCorrelation(event.observationId, sessionId, relationship, delta, correlationWindowNs)
        }
    }
}
