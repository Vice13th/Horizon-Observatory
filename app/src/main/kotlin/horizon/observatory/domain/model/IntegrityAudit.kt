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
        val ordered = observations.sortedBy { it.sequenceNumber }
        val first = ordered.firstOrNull()?.sequenceNumber
        val last = ordered.lastOrNull()?.sequenceNumber

        var contiguous = true
        ordered.forEachIndexed { index, row ->
            val expected = (index + 1).toLong()
            if (row.sequenceNumber != expected) contiguous = false
        }

        val ingestionTimestamps = ordered.mapNotNull { it.ingestionMonotonicTimestampNs }
        val ingestionMonotonic = ingestionTimestamps.zipWithNext().all { (a, b) -> b >= a }

        var sourceRegressions = 0
        var previousSource: Long? = null
        ordered.forEach { row ->
            val current = row.sourceMonotonicTimestampNs ?: return@forEach
            if (previousSource != null && current < previousSource!!) sourceRegressions++
            previousSource = current
        }

        return IntegrityAuditResult(
            observationCount = ordered.size,
            firstSequence = first,
            lastSequence = last,
            sequenceContiguous = contiguous,
            ingestionTimestampsNonDecreasing = ingestionMonotonic,
            ingestionTimestampedObservationCount = ingestionTimestamps.size,
            sourceTimestampRegressions = sourceRegressions,
            sourceTimestampedObservationCount = ordered.count { it.sourceMonotonicTimestampNs != null }
        )
    }
}

data class RawObservationView(
    val sequenceNumber: Long,
    val sourceMonotonicTimestampNs: Long?,
    val ingestionMonotonicTimestampNs: Long? = sourceMonotonicTimestampNs,
    val provider: String = "UNKNOWN"
)
