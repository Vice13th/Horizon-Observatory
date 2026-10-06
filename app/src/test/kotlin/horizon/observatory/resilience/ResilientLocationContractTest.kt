package horizon.observatory.resilience

import org.junit.Assert.*
import org.junit.Test

class ResilientLocationContractTest {
    @Test fun snapshot_requires_explicit_provenance_and_navigation_state() {
        val snapshot = ResilientLocationSnapshot(50.0, 8.0, 100.0, 5.0, 8.0, 10L, NavigationState.GNSS_LOST, "DEAD_RECKONED_FROM_LAST_TRUSTED_PVT")
        assertEquals(NavigationState.GNSS_LOST, snapshot.navigationState)
        assertEquals("DEAD_RECKONED_FROM_LAST_TRUSTED_PVT", snapshot.provenance)
    }
}
