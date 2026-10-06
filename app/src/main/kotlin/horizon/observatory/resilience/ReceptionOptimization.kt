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
            // Raw measurements are never changed. Policy only changes derived trust inputs.
            candidate
        }
    }
}

class ReceptionOptimizationEngine(private val policy: ReceptionOptimizationPolicy) {
    private val trustEngine = MeasurementTrustEngine()
    fun rank(candidates: List<MeasurementCandidate>): List<TrustDecisionRecord> =
        trustEngine.rank(policy.apply(candidates))
}
