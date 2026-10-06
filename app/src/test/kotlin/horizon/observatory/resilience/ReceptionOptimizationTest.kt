package horizon.observatory.resilience

import org.junit.Assert.*
import org.junit.Test

class ReceptionOptimizationTest {
    @Test fun disabledPolicy_isIdentityOnDerivedCandidates() {
        val candidates = listOf(MeasurementCandidate("A", signalStability = 0.8))
        assertEquals(candidates, ReceptionOptimizationPolicy(false).apply(candidates))
    }

    @Test fun enabledPolicy_keepsRawCandidateValuesUnmodified() {
        val candidates = listOf(MeasurementCandidate("A", signalStability = 0.8, freshness = 0.9))
        val result = ReceptionOptimizationPolicy(true).apply(candidates)
        assertEquals(candidates, result)
    }

    @Test fun engine_reusesDeterministicTrustRanking() {
        val result = ReceptionOptimizationEngine(ReceptionOptimizationPolicy(true)).rank(
            listOf(MeasurementCandidate("B", signalStability = 0.4), MeasurementCandidate("A", signalStability = 0.9))
        )
        assertEquals("A", result.first().id)
    }
}
