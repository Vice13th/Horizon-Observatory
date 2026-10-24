package horizon.observatory.resilience

sealed interface PublishDecision {
    object Publish : PublishDecision
    data class Skip(val reason: String) : PublishDecision
}

/**
 * The single decision point before a snapshot may replace the published one. Pure; one instance per
 * session, used under the service's ingress lock.
 *
 * Guarantees: published timestamps never go backwards (an out-of-order or duplicate fix cannot overwrite
 * a newer position); an estimate never replaces an observed fix of the same instant; an invalid snapshot
 * is never published. It records a Publish decision optimistically; the store independently refuses
 * invalid payloads.
 */
class SnapshotPublishGate {
    private var lastNs: Long? = null
    private var lastWasEstimate = false

    fun decide(candidate: ResilientLocationSnapshot): PublishDecision {
        ResilientLocationValidator.problem(candidate)?.let { return PublishDecision.Skip("INVALID_$it") }
        val isEstimate = candidate.fixKind == ResilientFixKind.SHORT_HORIZON_ESTIMATE
        val last = lastNs
        if (last != null) {
            val ts = candidate.timestampMonotonicNs
            if (ts < last) return PublishDecision.Skip("OLDER_THAN_PUBLISHED")
            if (ts == last) {
                if (isEstimate == lastWasEstimate) return PublishDecision.Skip("DUPLICATE_TIMESTAMP")
                if (isEstimate) return PublishDecision.Skip("ESTIMATE_DOES_NOT_REPLACE_OBSERVED")
            }
        }
        lastNs = candidate.timestampMonotonicNs
        lastWasEstimate = isEstimate
        return PublishDecision.Publish
    }
}
