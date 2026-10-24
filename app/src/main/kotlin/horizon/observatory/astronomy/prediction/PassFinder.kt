package horizon.observatory.astronomy.prediction

data class AzEl(val azDeg: Double, val elDeg: Double)

/**
 * A predicted visibility pass above an elevation mask. riseMs/setMs are null when the pass was
 * already in progress at the window start / still in progress at the window end (never guessed).
 */
data class PredictedPass(
    val riseMs: Long?,
    val setMs: Long?,
    val culminationMs: Long,
    val maxElDeg: Double,
    val riseAzDeg: Double?,
    val setAzDeg: Double?
)

/**
 * Pass search over an arbitrary elevation oracle [at] (UTC ms → az/el, null = no valid prediction):
 * 1. coarse scan every [coarseStepMs]; 2. each mask crossing refined by bisection to [toleranceMs];
 * 3. culmination refined by ternary search (elevation is unimodal inside a pass).
 * A null sample ends an open pass with an unknown edge and never starts one. Geometry only:
 * no refraction, no illumination, no obstruction model.
 */
object PassFinder {
    suspend fun find(
        startMs: Long,
        endMs: Long,
        maskDeg: Double,
        coarseStepMs: Long = 300_000L,
        toleranceMs: Long = 1_000L,
        at: suspend (Long) -> AzEl?
    ): List<PredictedPass> {
        require(coarseStepMs > 0 && endMs > startMs)
        val out = ArrayList<PredictedPass>()
        var inPass = false
        var riseMs: Long? = null
        var riseAz: Double? = null
        var passStart = startMs
        var prevT = startMs
        var prevE: Double? = at(startMs)?.elDeg?.minus(maskDeg)
        if (prevE != null && prevE >= 0) { inPass = true; passStart = startMs }

        suspend fun cross(lo: Long, hi: Long, loAbove: Boolean): Long {
            var a = lo; var b = hi
            while (b - a > toleranceMs) {
                val m = a + (b - a) / 2
                val e = at(m)?.elDeg?.minus(maskDeg) ?: return m
                if ((e >= 0) == loAbove) a = m else b = m
            }
            return b
        }
        suspend fun finish(from: Long, to: Long, rise: Long?, riseAzDeg: Double?, set: Long?) {
            var a = from; var b = to
            repeat(40) {
                if (b - a <= toleranceMs) return@repeat
                val m1 = a + (b - a) / 3; val m2 = b - (b - a) / 3
                val e1 = at(m1)?.elDeg ?: Double.NEGATIVE_INFINITY
                val e2 = at(m2)?.elDeg ?: Double.NEGATIVE_INFINITY
                if (e1 < e2) a = m1 else b = m2
            }
            val c = a + (b - a) / 2
            val ce = at(c) ?: return
            out.add(PredictedPass(rise, set, c, ce.elDeg, riseAzDeg, set?.let { at(it)?.azDeg }))
        }

        var t = startMs + coarseStepMs
        while (true) {
            val tt = if (t >= endMs) endMs else t
            val e = at(tt)?.elDeg?.minus(maskDeg)
            val pe = prevE
            if (!inPass) {
                if (e != null && e >= 0 && pe != null && pe < 0) {
                    riseMs = cross(prevT, tt, loAbove = false)
                    riseAz = at(riseMs)?.azDeg
                    passStart = riseMs; inPass = true
                }
            } else {
                if (e == null) {
                    finish(passStart, prevT, riseMs, riseAz, null); inPass = false; riseMs = null; riseAz = null
                } else if (e < 0) {
                    val set = cross(prevT, tt, loAbove = true)
                    finish(passStart, set, riseMs, riseAz, set); inPass = false; riseMs = null; riseAz = null
                }
            }
            prevT = tt; prevE = e
            if (tt >= endMs) break
            t += coarseStepMs
        }
        if (inPass) finish(passStart, endMs, riseMs, riseAz, null)
        return out
    }
}
