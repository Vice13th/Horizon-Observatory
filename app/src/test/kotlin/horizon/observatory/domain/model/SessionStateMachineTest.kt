package horizon.observatory.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateMachineTest {
    @Test
    fun legalLifecycleTransitionsAreAccepted() {
        assertTrue(SessionStateMachine.canTransition(SessionLifecycleState.IDLE, SessionLifecycleState.READY))
        assertTrue(SessionStateMachine.canTransition(SessionLifecycleState.READY, SessionLifecycleState.RECORDING))
        assertTrue(SessionStateMachine.canTransition(SessionLifecycleState.RECORDING, SessionLifecycleState.STOPPING))
        assertTrue(SessionStateMachine.canTransition(SessionLifecycleState.STOPPING, SessionLifecycleState.COMPLETED))
        assertTrue(SessionStateMachine.canTransition(SessionLifecycleState.RECORDING, SessionLifecycleState.CRASH_CLOSED))
    }

    @Test
    fun illegalLifecycleTransitionsAreRejected() {
        assertFalse(SessionStateMachine.canTransition(SessionLifecycleState.COMPLETED, SessionLifecycleState.RECORDING))
        assertFalse(SessionStateMachine.canTransition(SessionLifecycleState.STOPPING, SessionLifecycleState.RECORDING))
        assertFalse(SessionStateMachine.canTransition(SessionLifecycleState.CRASH_CLOSED, SessionLifecycleState.COMPLETED))
        assertThrows(IllegalSessionTransitionException::class.java) {
            SessionStateMachine.requireTransition(SessionLifecycleState.COMPLETED, SessionLifecycleState.RECORDING)
        }
    }
}
