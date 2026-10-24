package horizon.observatory.resilience

import org.junit.Assert.*
import org.junit.Test

/** G5 (pure part): the UI state machine behind the Location Continuity control. */
class LocationContinuityStatusTest {
    private fun svc(s: ContinuityServiceStatus, n: NavigationState? = null) = ContinuityServiceSnapshot(s, null, n)
    private fun reduce(requested: Boolean, s: ContinuityServiceSnapshot, perm: Boolean = true, loc: Boolean = true, since: Long? = null) =
        ContinuityUiReducer.reduce(ContinuityInputs(requested, s, perm, loc, since))

    @Test fun offWhenNotRequestedAndStopped() = assertEquals(ContinuityUiState.OFF, reduce(false, svc(ContinuityServiceStatus.STOPPED), perm = false))
    @Test fun stoppingWhileServiceStillRunningAfterUserTurnedOff() = assertEquals(ContinuityUiState.STOPPING, reduce(false, svc(ContinuityServiceStatus.ACTIVE)))
    @Test fun permissionBeatsEverythingWhileRequested() = assertEquals(ContinuityUiState.PERMISSION_REQUIRED, reduce(true, svc(ContinuityServiceStatus.ACTIVE), perm = false, loc = false))
    @Test fun locationServicesOff() = assertEquals(ContinuityUiState.LOCATION_OFF, reduce(true, svc(ContinuityServiceStatus.ACTIVE), loc = false))
    @Test fun activeOnlyWhenFullGnssOrNoStateYet() {
        assertEquals(ContinuityUiState.ACTIVE, reduce(true, svc(ContinuityServiceStatus.ACTIVE)))
        assertEquals(ContinuityUiState.ACTIVE, reduce(true, svc(ContinuityServiceStatus.ACTIVE, NavigationState.FULL_GNSS)))
        for (n in NavigationState.values().filter { it != NavigationState.FULL_GNSS })
            assertEquals("$n", ContinuityUiState.DEGRADED, reduce(true, svc(ContinuityServiceStatus.ACTIVE, n)))
    }
    @Test fun requestedButNotRunning_isStartingOnlyWithinGrace_thenFailed() {
        assertEquals(ContinuityUiState.STARTING, reduce(true, svc(ContinuityServiceStatus.STOPPED), since = 500))
        assertEquals(ContinuityUiState.FAILED, reduce(true, svc(ContinuityServiceStatus.STOPPED), since = 3_000))
        assertEquals(ContinuityUiState.FAILED, reduce(true, svc(ContinuityServiceStatus.STOPPED), since = null))
    }
    @Test fun serviceFailureAndStarting() {
        assertEquals(ContinuityUiState.FAILED, reduce(true, ContinuityServiceSnapshot(ContinuityServiceStatus.FAILED, ContinuityFailure.FOREGROUND_START_REJECTED)))
        assertEquals(ContinuityUiState.STARTING, reduce(true, svc(ContinuityServiceStatus.STARTING)))
    }
    @Test fun debouncer_dampsOnlyActiveDegradedFlapping() {
        val d = ContinuityUiDebouncer(2_000)
        assertEquals(ContinuityUiState.ACTIVE, d.update(ContinuityUiState.ACTIVE, 0))
        assertEquals(ContinuityUiState.ACTIVE, d.update(ContinuityUiState.DEGRADED, 100))
        assertEquals(ContinuityUiState.ACTIVE, d.update(ContinuityUiState.ACTIVE, 900))   // blip gone
        assertEquals(ContinuityUiState.ACTIVE, d.update(ContinuityUiState.DEGRADED, 1_000))
        assertEquals(ContinuityUiState.DEGRADED, d.update(ContinuityUiState.DEGRADED, 3_000))
        assertEquals(ContinuityUiState.FAILED, d.update(ContinuityUiState.FAILED, 3_001))  // errors are immediate
        assertEquals(ContinuityUiState.PERMISSION_REQUIRED, d.update(ContinuityUiState.PERMISSION_REQUIRED, 3_002))
    }
    @Test fun copyIsHonestAboutLimits_andCoversEveryState() {
        assertTrue(ContinuityCopy.LIMITS.contains("cannot defeat radio interference"))
        assertTrue(ContinuityCopy.LIMITS.contains("provisional"))
        ContinuityUiState.values().forEach { assertTrue(ContinuityCopy.headline(it).isNotBlank()) }
    }
}
