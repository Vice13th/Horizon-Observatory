package horizon.observatory.resilience

class NavigationContinuityStateMachine(
    private val transitionDebounceSamples: Int = 2
) {
    var state: NavigationState = NavigationState.FULL_GNSS
        private set
    private var candidate: NavigationState? = null
    private var candidateCount = 0

    fun update(e: NavigationEvidence): NavigationTransition? {
        val target = nextTarget(state, e) ?: return null
        if (target == state) { candidate = null; candidateCount = 0; return null }
        if (candidate != target) { candidate = target; candidateCount = 1 } else candidateCount++
        if (candidateCount < transitionDebounceSamples) return null
        val from = state
        state = target
        candidate = null
        candidateCount = 0
        return NavigationTransition(from, target, e)
    }

    private fun nextTarget(s: NavigationState, e: NavigationEvidence): NavigationState? = when (s) {
        NavigationState.FULL_GNSS -> when {
            e.recoveryGnss -> NavigationState.RECOVERY
            e.gnssLost -> NavigationState.GNSS_DEGRADED
            e.degradedGnss -> NavigationState.GNSS_DEGRADED
            else -> NavigationState.FULL_GNSS
        }
        NavigationState.GNSS_DEGRADED -> when {
            e.gnssLost -> NavigationState.GNSS_LOST
            e.partialGnss -> NavigationState.PARTIAL_GNSS
            e.healthyGnss -> NavigationState.FULL_GNSS
            else -> NavigationState.GNSS_DEGRADED
        }
        NavigationState.PARTIAL_GNSS -> when {
            e.gnssLost -> NavigationState.GNSS_LOST
            e.healthyGnss -> NavigationState.FULL_GNSS
            e.degradedGnss -> NavigationState.GNSS_DEGRADED
            else -> NavigationState.PARTIAL_GNSS
        }
        NavigationState.GNSS_LOST -> when {
            e.multiSourceAgreement -> NavigationState.MULTI_SOURCE_FUSION
            e.validImu -> NavigationState.INERTIAL_BRIDGING
            e.validCell -> NavigationState.CELL_AIDED
            else -> NavigationState.GNSS_LOST
        }
        NavigationState.INERTIAL_BRIDGING -> when {
            e.multiSourceAgreement -> NavigationState.MULTI_SOURCE_FUSION
            !e.validImu && e.validCell -> NavigationState.CELL_AIDED
            e.recoveryGnss -> NavigationState.RECOVERY
            else -> NavigationState.INERTIAL_BRIDGING
        }
        NavigationState.CELL_AIDED -> when {
            e.multiSourceAgreement -> NavigationState.MULTI_SOURCE_FUSION
            e.validImu && e.validCell -> NavigationState.MULTI_SOURCE_FUSION
            e.recoveryGnss -> NavigationState.RECOVERY
            else -> NavigationState.CELL_AIDED
        }
        NavigationState.MULTI_SOURCE_FUSION -> when {
            e.recoveryGnss -> NavigationState.RECOVERY
            !e.validImu && !e.validCell -> NavigationState.GNSS_LOST
            else -> NavigationState.MULTI_SOURCE_FUSION
        }
        NavigationState.RECOVERY -> when {
            e.healthyGnss -> NavigationState.FULL_GNSS
            e.degradedGnss -> NavigationState.GNSS_DEGRADED
            e.gnssLost -> NavigationState.GNSS_LOST
            else -> NavigationState.RECOVERY
        }
    }
}
