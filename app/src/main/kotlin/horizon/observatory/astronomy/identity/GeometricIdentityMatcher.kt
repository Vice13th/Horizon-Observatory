package horizon.observatory.astronomy.identity

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class ObservedDirection(val id: GnssSatelliteId, val azDeg: Double, val elDeg: Double)
data class CandidateDirection(val noradCatId: Long, val azDeg: Double, val elDeg: Double, val displayName: String?)

enum class HintStatus { CONSISTENT, AMBIGUOUS, NO_CANDIDATE }

data class IdentityHint(
    val id: GnssSatelliteId,
    val status: HintStatus,
    /** Closest candidate (informational unless CONSISTENT). */
    val noradCatId: Long?,
    val displayName: String?,
    val separationDeg: Double?,
    val runnerUpSeparationDeg: Double?,
    val reason: String
)

/**
 * HYPOTHESIS (testable, thresholds provisional): the receiver's az/el of a satellite and the SGP4/SDP4
 * prediction of the right catalog object should agree to a few degrees, while other objects are far
 * away. A hint is CONSISTENT only if (1) the best candidate is within [maxSeparationDeg], (2) the next
 * candidate is at least [minMarginDeg] farther, and (3) the match is mutual — this candidate's closest
 * observed satellite is this one, with the same margin. Anything else is AMBIGUOUS / NO_CANDIDATE.
 * Output is a hint for the operator; it is never an identification and never feeds prediction.
 * Pure geometry: single epoch, no refraction, receiver az/el accuracy is device dependent.
 */
object GeometricIdentityMatcher {
    const val DEFAULT_MAX_SEPARATION_DEG = 3.0
    const val DEFAULT_MIN_MARGIN_DEG = 4.0

    /** Angle between two sky directions: atan2(|a×b|, a·b) on unit vectors (stable for tiny and large angles). */
    fun separationDeg(az1Deg: Double, el1Deg: Double, az2Deg: Double, el2Deg: Double): Double {
        fun v(az: Double, el: Double): DoubleArray {
            val a = Math.toRadians(az); val e = Math.toRadians(el)
            return doubleArrayOf(cos(e) * sin(a), cos(e) * cos(a), sin(e))
        }
        val a = v(az1Deg, el1Deg); val b = v(az2Deg, el2Deg)
        val cx = a[1] * b[2] - a[2] * b[1]
        val cy = a[2] * b[0] - a[0] * b[2]
        val cz = a[0] * b[1] - a[1] * b[0]
        val dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        return Math.toDegrees(atan2(sqrt(cx * cx + cy * cy + cz * cz), dot))
    }

    fun match(
        observed: List<ObservedDirection>,
        candidates: List<CandidateDirection>,
        maxSeparationDeg: Double = DEFAULT_MAX_SEPARATION_DEG,
        minMarginDeg: Double = DEFAULT_MIN_MARGIN_DEG
    ): List<IdentityHint> {
        val obs = observed.distinctBy { it.id }
        // sep[i][j] = separation between observed i and candidate j
        val sep = Array(obs.size) { i -> DoubleArray(candidates.size) { j ->
            separationDeg(obs[i].azDeg, obs[i].elDeg, candidates[j].azDeg, candidates[j].elDeg)
        } }
        return obs.mapIndexed { i, o ->
            if (candidates.isEmpty()) return@mapIndexed IdentityHint(o.id, HintStatus.NO_CANDIDATE, null, null, null, null, "no predicted candidates")
            val order = candidates.indices.sortedBy { sep[i][it] }
            val best = order[0]
            val bestSep = sep[i][best]
            val second = order.getOrNull(1)?.let { sep[i][it] }
            val c = candidates[best]
            fun hint(status: HintStatus, reason: String) = IdentityHint(o.id, status, c.noradCatId, c.displayName, bestSep, second, reason)
            when {
                bestSep > maxSeparationDeg -> hint(HintStatus.NO_CANDIDATE, "closest candidate is ${"%.1f".format(java.util.Locale.US, bestSep)}° away (> ${maxSeparationDeg}°)")
                second != null && second - bestSep < minMarginDeg -> hint(HintStatus.AMBIGUOUS, "another candidate is within ${minMarginDeg}° of the best separation")
                else -> {
                    // mutual-best check from the candidate's side
                    val col = obs.indices.sortedBy { sep[it][best] }
                    val rival = col.firstOrNull { it != i }?.let { sep[it][best] }
                    when {
                        col[0] != i -> hint(HintStatus.AMBIGUOUS, "another observed satellite is closer to this candidate")
                        rival != null && rival - bestSep < minMarginDeg -> hint(HintStatus.AMBIGUOUS, "another observed satellite is almost as close to this candidate")
                        else -> hint(HintStatus.CONSISTENT, "unique mutual best match")
                    }
                }
            }
        }
    }
}
