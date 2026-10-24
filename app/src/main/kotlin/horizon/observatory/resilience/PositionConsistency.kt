package horizon.observatory.resilience

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One position-relevant estimate from one source. [independenceGroup] names the measurement chain it
 * derives from: evidence sharing a group (for example a GNSS fix and a projection made from it) is NOT
 * independent and can never agree with itself.
 */
data class PositionEvidence(
    val sourceId: String,
    val independenceGroup: String,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val horizontalUncertaintyM: Double,
    /** SystemClock.elapsedRealtimeNanos() domain of the position's acquisition time. */
    val timestampMonotonicNs: Long
)

data class ConsistencyPolicy(
    val maxEvidenceAgeMs: Long = 5_000L,
    val maxTimeSkewMs: Long = 2_000L,
    /** Agreement tolerance = sigmaMultiplier * sqrt(u1^2 + u2^2). Provisional. */
    val sigmaMultiplier: Double = 3.0
)

sealed interface ConsistencyDecision {
    data class Agree(val sourceIds: List<String>, val maxResidualM: Double) : ConsistencyDecision
    data class Disagree(val sourceIds: List<String>, val residualM: Double, val toleranceM: Double) : ConsistencyDecision
    data class Insufficient(val reason: String) : ConsistencyDecision
}

class PositionConsistencyChecker(private val policy: ConsistencyPolicy = ConsistencyPolicy()) {

    fun evaluate(evidence: Collection<PositionEvidence>, nowElapsedNs: Long): ConsistencyDecision {
        val usable = evidence.filter { isUsable(it, nowElapsedNs) }
        // Keep the freshest (then most precise) item per independence group.
        val perGroup = usable.groupBy { it.independenceGroup }.values.map { group ->
            group.sortedWith(compareByDescending<PositionEvidence> { it.timestampMonotonicNs }.thenBy { it.horizontalUncertaintyM }).first()
        }
        if (perGroup.size < 2) return ConsistencyDecision.Insufficient("FEWER_THAN_TWO_INDEPENDENT_SOURCES")

        val skewLimitNs = policy.maxTimeSkewMs * 1_000_000L
        val oldest = perGroup.minOf { it.timestampMonotonicNs }
        val newest = perGroup.maxOf { it.timestampMonotonicNs }
        if (newest - oldest > skewLimitNs) return ConsistencyDecision.Insufficient("TIME_MISALIGNED")

        var worst: ConsistencyDecision.Disagree? = null
        var maxResidual = 0.0
        for (i in perGroup.indices) for (j in i + 1 until perGroup.size) {
            val a = perGroup[i]
            val b = perGroup[j]
            val residual = distanceM(a.latitudeDeg, a.longitudeDeg, b.latitudeDeg, b.longitudeDeg)
            val tolerance = policy.sigmaMultiplier * sqrt(a.horizontalUncertaintyM * a.horizontalUncertaintyM + b.horizontalUncertaintyM * b.horizontalUncertaintyM)
            if (residual > maxResidual) maxResidual = residual
            if (residual > tolerance && (worst == null || residual > worst.residualM)) {
                worst = ConsistencyDecision.Disagree(listOf(a.sourceId, b.sourceId), residual, tolerance)
            }
        }
        return worst ?: ConsistencyDecision.Agree(perGroup.map { it.sourceId }.sorted(), maxResidual)
    }

    private fun isUsable(e: PositionEvidence, nowElapsedNs: Long): Boolean {
        if (e.sourceId.isBlank() || e.independenceGroup.isBlank()) return false
        if (!e.latitudeDeg.isFinite() || e.latitudeDeg < -90.0 || e.latitudeDeg > 90.0) return false
        if (!e.longitudeDeg.isFinite() || e.longitudeDeg < -180.0 || e.longitudeDeg > 180.0) return false
        if (!e.horizontalUncertaintyM.isFinite() || e.horizontalUncertaintyM <= 0.0) return false
        if (e.timestampMonotonicNs <= 0L) return false
        val ageNs = nowElapsedNs - e.timestampMonotonicNs
        return ageNs >= -policy.maxTimeSkewMs * 1_000_000L && ageNs <= policy.maxEvidenceAgeMs * 1_000_000L
    }

    companion object {
        private const val EARTH_RADIUS_M = 6_371_008.8

        fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dp = p2 - p1
            val dl = Math.toRadians(lon2 - lon1)
            val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
            return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
        }
    }
}
