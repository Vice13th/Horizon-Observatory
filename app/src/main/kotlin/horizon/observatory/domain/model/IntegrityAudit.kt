package horizon.observatory.domain.model

data class IntegrityAuditResult(
    val observationCount: Int,
    val firstSequence: Long?,
    val lastSequence: Long?,
    val sequenceContiguous: Boolean,
    val ingestionTimestampsNonDecreasing: Boolean,
    val ingestionTimestampedObservationCount: Int,
    val sourceTimestampRegressions: Int,
    val sourceTimestampedObservationCount: Int
) {
    /** Integrity requires a complete serialized ingress clock plus contiguous sequence order. */
    val isClean: Boolean =
        sequenceContiguous &&
            ingestionTimestampsNonDecreasing &&
            ingestionTimestampedObservationCount == observationCount

    @Deprecated("Use ingestionTimestampsNonDecreasing; source timestamps may legitimately arrive out of order.")
    val monotonicTimestampsNonDecreasing: Boolean
        get() = ingestionTimestampsNonDecreasing

    @Deprecated("Use ingestionTimestampedObservationCount")
    val timestampedObservationCount: Int
        get() = ingestionTimestampedObservationCount
}

object IntegrityAudit {
    fun audit(observations: List<RawObservationView>): IntegrityAuditResult {
        val builder = IncrementalIntegrityAudit()
        observations.sortedBy { it.sequenceNumber }.forEach(builder::append)
        return builder.finish()
    }
}

/**
 * One-pass integrity accumulator for ordered database pages.
 *
 * Rows must be provided in strictly increasing sequence order, as emitted by
 * ObservationDao.getObservationPage(). It retains counters and prior clock values only,
 * rather than a second list of all observations.
 */
class IncrementalIntegrityAudit {
    private var count = 0
    private var first: Long? = null
    private var last: Long? = null
    private var contiguous = true
    private var previousIngestion: Long? = null
    private var ingestionNonDecreasing = true
    private var ingestionCount = 0
    private var previousSource: Long? = null
    private var sourceRegressions = 0
    private var sourceCount = 0

    fun append(row: RawObservationView) {
        val priorSequence = last
        require(priorSequence == null || row.sequenceNumber > priorSequence) {
            "Incremental integrity audit requires strictly increasing sequence numbers: prior=" + priorSequence + " next=" + row.sequenceNumber
        }

        count += 1
        if (first == null) first = row.sequenceNumber
        last = row.sequenceNumber
        if (row.sequenceNumber != count.toLong()) contiguous = false

        row.ingestionMonotonicTimestampNs?.let { timestamp ->
            val previous = previousIngestion
            if (previous != null && timestamp < previous) ingestionNonDecreasing = false
            previousIngestion = timestamp
            ingestionCount += 1
        }

        row.sourceMonotonicTimestampNs?.let { timestamp ->
            val previous = previousSource
            if (previous != null && timestamp < previous) sourceRegressions += 1
            previousSource = timestamp
            sourceCount += 1
        }
    }

    fun finish(): IntegrityAuditResult = IntegrityAuditResult(
        observationCount = count,
        firstSequence = first,
        lastSequence = last,
        sequenceContiguous = contiguous,
        ingestionTimestampsNonDecreasing = ingestionNonDecreasing,
        ingestionTimestampedObservationCount = ingestionCount,
        sourceTimestampRegressions = sourceRegressions,
        sourceTimestampedObservationCount = sourceCount
    )
}

data class RawObservationView(
    val sequenceNumber: Long,
    val sourceMonotonicTimestampNs: Long?,
    val ingestionMonotonicTimestampNs: Long? = sourceMonotonicTimestampNs,
    val provider: String = "UNKNOWN"
)
