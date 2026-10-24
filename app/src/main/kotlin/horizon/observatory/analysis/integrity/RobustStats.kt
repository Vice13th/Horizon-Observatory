package horizon.observatory.analysis.integrity

import kotlin.math.abs
import kotlin.math.floor

internal object RobustStats {
    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    /** Median absolute deviation scaled to ≈ σ for Gaussian data. */
    fun madSigma(values: List<Double>, center: Double? = median(values)): Double? {
        if (values.isEmpty() || center == null) return null
        val m = median(values.map { abs(it - center) }) ?: return null
        return 1.4826 * m
    }

    fun quantile(values: List<Double>, q: Double): Double? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val pos = q.coerceIn(0.0, 1.0) * (s.size - 1)
        val lo = floor(pos).toInt()
        val hi = minOf(lo + 1, s.size - 1)
        return s[lo] + (pos - lo) * (s[hi] - s[lo])
    }
}

/** Result of scanning a series with a sliding "recent vs preceding baseline" robust shift test. */
internal data class ShiftScan(
    val windows: Int,
    val maxAbsZ: Double,
    val maxZ: Double,
    val atSeconds: Double?,
    val exceeding: Int,
    /** End time of the first window that exceeded the gate (onset estimate); null if none did. */
    val firstExceedSeconds: Double? = null
) {
    companion object {
        val EMPTY = ShiftScan(0, 0.0, 0.0, null, 0, null)
    }

    fun merge(o: ShiftScan): ShiftScan {
        val best = if (o.maxAbsZ > maxAbsZ) o else this
        val first = listOfNotNull(firstExceedSeconds, o.firstExceedSeconds).minOrNull()
        return ShiftScan(windows + o.windows, best.maxAbsZ, best.maxZ, best.atSeconds, exceeding + o.exceeding, first)
    }
}

/**
 * z = (median(recent window) − median(preceding baseline)) / max(MAD-σ(baseline), floor).
 * Relative to the session's own baseline: an anomaly present from the start is invisible to this test.
 */
internal fun scanRobustShift(
    values: DoubleArray,
    seconds: DoubleArray,
    recent: Int,
    minBaseline: Int,
    maxBaseline: Int,
    floorSigma: Double,
    zGate: Double,
    stride: Int
): ShiftScan {
    var windows = 0
    var maxAbs = 0.0
    var maxZ = 0.0
    var at: Double? = null
    var exceed = 0
    var firstExceed: Double? = null
    var end = minBaseline + recent
    val step = maxOf(1, stride)
    while (end <= values.size) {
        val rs = end - recent
        val bs = maxOf(0, rs - maxBaseline)
        if (rs - bs >= minBaseline) {
            val base = values.slice(bs until rs)
            val rec = values.slice(rs until end)
            val mb = RobustStats.median(base)!!
            val sigma = maxOf(RobustStats.madSigma(base, mb)!!, floorSigma)
            val z = (RobustStats.median(rec)!! - mb) / sigma
            windows++
            if (abs(z) > maxAbs) {
                maxAbs = abs(z); maxZ = z; at = seconds[end - 1]
            }
            if (abs(z) >= zGate) {
                exceed++
                if (firstExceed == null) firstExceed = seconds[end - 1]
            }
        }
        end += step
    }
    return ShiftScan(windows, maxAbs, maxZ, at, exceed, firstExceed)
}
