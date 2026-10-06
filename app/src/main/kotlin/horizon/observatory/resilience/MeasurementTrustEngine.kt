package horizon.observatory.resilience

class MeasurementTrustEngine {
    fun rank(candidates: List<MeasurementCandidate>): List<TrustDecisionRecord> {
        return candidates.map { c ->
            val fields = listOf(
                "signalStability" to c.signalStability,
                "temporalContinuity" to c.temporalContinuity,
                "freshness" to c.freshness,
                "dopplerConsistency" to c.dopplerConsistency,
                "adrConsistency" to c.adrConsistency,
                "geometry" to c.geometry,
                "residualConsistency" to c.residualConsistency,
                "sourceAgreement" to c.sourceAgreement
            )
            val available = fields.mapNotNull { (n,v) -> v?.coerceIn(0.0,1.0)?.let { n to it } }
            val score = if (available.isEmpty()) 0.0 else available.sumOf { it.second } / available.size
            val reasons = buildList {
                available.filter { it.second < 0.25 }.forEach { add("LOW_${it.first.uppercase()}") }
                if (available.isEmpty()) add("NO_TRUST_EVIDENCE")
            }
            val stale = c.ageMs?.let { it > 5_000L } == true || c.freshness?.let { it <= 0.0 } == true
            val decision = when {
                stale -> TrustDecision.REJECT
                available.isEmpty() -> TrustDecision.DOWN_WEIGHT
                score < 0.25 -> TrustDecision.REJECT
                score < 0.55 -> TrustDecision.DOWN_WEIGHT
                else -> TrustDecision.KEEP
            }
            Triple(c.id, score, reasons to decision)
        }.sortedWith(compareByDescending<Triple<String,Double,Pair<List<String>,TrustDecision>>> { it.second }.thenBy { it.first })
            .mapIndexed { index, (id,score,pair) -> TrustDecisionRecord(id, score, pair.second, pair.first, index + 1) }
    }
}
