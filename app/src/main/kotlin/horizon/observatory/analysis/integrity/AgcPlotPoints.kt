package horizon.observatory.analysis.integrity

/** AGC values paired with monotonic event time. Plot time is relative to the first retained event. */
data class AgcPlotPoint(val eventNs: Long, val levelDb: Double)

/** Sorted elapsed-second/value pairs; rejects non-finite levels and negative/unknown timestamps. */
fun agcPlotPoints(samples: List<AgcPlotPoint>): List<Pair<Double, Double>> {
    val ordered = samples.filter { it.levelDb.isFinite() && it.eventNs >= 0L }.sortedBy { it.eventNs }
    val originNs = ordered.firstOrNull()?.eventNs ?: return emptyList()
    return ordered.map { point -> ((point.eventNs - originNs).coerceAtLeast(0L) / 1.0e9) to point.levelDb }
}
