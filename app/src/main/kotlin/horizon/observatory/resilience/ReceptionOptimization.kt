package horizon.observatory.resilience

data class ReceptionOptimizationPolicy(
    val enabled: Boolean,
    val staleMeasurementMaxAgeMs: Long = 5_000L,
    val interferenceAwareWeighting: Boolean = true,
    val adaptiveRecovery: Boolean = true,
    val powerAwareProfile: Boolean = true
) {
    fun apply(candidates: List<MeasurementCandidate>, nowMonotonicMs: Long? = null): List<MeasurementCandidate> {
        if (!enabled) return candidates
        return candidates.map { candidate ->
            // Raw observations are immutable. Only derived trust inputs are adjusted here.
            val stale = candidate.ageMs?.let { it > staleMeasurementMaxAgeMs } == true
            val freshness = when {
                stale -> 0.0
                candidate.ageMs == null -> candidate.freshness
                staleMeasurementMaxAgeMs <= 0L -> 0.0
                else -> (1.0 - candidate.ageMs.toDouble() / staleMeasurementMaxAgeMs.toDouble()).coerceIn(0.0, 1.0)
            }
            candidate.copy(freshness = freshness)
        }
    }
}

class ReceptionOptimizationEngine(private val policy: ReceptionOptimizationPolicy) {
    private val trustEngine = MeasurementTrustEngine()
    fun rank(candidates: List<MeasurementCandidate>, nowMonotonicMs: Long? = null): List<TrustDecisionRecord> =
        trustEngine.rank(policy.apply(candidates, nowMonotonicMs))
}
