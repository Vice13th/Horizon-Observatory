package horizon.observatory.domain.model

enum class SessionLifecycleState {
    IDLE,
    READY,
    RECORDING,
    STOPPING,
    COMPLETED,
    CRASH_CLOSED
}

class IllegalSessionTransitionException(
    from: SessionLifecycleState,
    to: SessionLifecycleState
) : IllegalStateException("Illegal session transition: $from -> $to")

/** Single source of truth for legal session lifecycle transitions. */
object SessionStateMachine {
    fun canTransition(from: SessionLifecycleState, to: SessionLifecycleState): Boolean = when (from) {
        SessionLifecycleState.IDLE -> to == SessionLifecycleState.READY
        SessionLifecycleState.READY -> to == SessionLifecycleState.RECORDING
        SessionLifecycleState.RECORDING ->
            to == SessionLifecycleState.STOPPING || to == SessionLifecycleState.CRASH_CLOSED
        SessionLifecycleState.STOPPING -> to == SessionLifecycleState.COMPLETED
        SessionLifecycleState.COMPLETED -> false
        SessionLifecycleState.CRASH_CLOSED -> false
    }

    fun requireTransition(from: SessionLifecycleState, to: SessionLifecycleState) {
        if (!canTransition(from, to)) {
            throw IllegalSessionTransitionException(from, to)
        }
    }
}
