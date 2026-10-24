package horizon.observatory.resilience

import horizon.observatory.domain.model.ObservationType
import org.junit.Assert.*
import org.junit.Test

/** G2: last-valid-PVT freshness is independent of GNSS status / raw-measurement callbacks. */
class ResilienceFreshnessTest {
    private fun fix(r: ResilienceRuntime, srcNs: Long, ingestNs: Long = srcNs, json: String = fixJson()) =
        r.accept(rawObs(ObservationType.GNSS_FIX, json, srcNs, ingestNs))
    private fun status(r: ResilienceRuntime, ns: Long, ingestNs: Long = ns) = r.accept(rawObs(ObservationType.GNSS_STATUS, STATUS_JSON, ns, ingestNs))

    @Test fun pvtFreshnessPolicy_rejectsInvalidOrOverflowingThresholds() {
        assertThrows(IllegalArgumentException::class.java) { PvtFreshnessPolicy(staleAfterMs = -1L) }
        assertThrows(IllegalArgumentException::class.java) { PvtFreshnessPolicy(healthyHorizontalUncertaintyM = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { PvtFreshnessPolicy(recoveryMinConsecutiveFixes = 0) }
        assertThrows(IllegalArgumentException::class.java) { PvtFreshnessPolicy(recoveryMinDwellMs = -1L) }
        assertThrows(IllegalArgumentException::class.java) { PvtFreshnessPolicy(maxFutureSkewMs = Long.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { PvtFreshnessPolicy(imuFreshMs = -1L) }
        assertThrows(IllegalArgumentException::class.java) { PvtFreshnessPolicy(estimatePeriodMs = 0L) }
    }

    @Test fun statusCallbacksAlone_neverRefreshAnOldPvt() {
        val r = ResilienceRuntime()
        fix(r, 1 * S)
        for (s in 2..60) status(r, s * S)
        assertEquals(1 * S, r.debugLastValidPvtNs)
        assertEquals(60 * S, r.debugLastGnssStatusNs)
        assertEquals(59_000L, r.debugLastPvtAgeMs)            // age is measured from the PVT, not from the status
        assertEquals(NavigationState.GNSS_LOST, r.navigationState) // baseline defect: stayed FULL_GNSS forever
    }

    @Test fun rawMeasurementCallbacksAlone_neverRefreshAnOldPvt() {
        val r = ResilienceRuntime()
        fix(r, 1 * S)
        for (s in 2..30) r.accept(rawObs(ObservationType.GNSS_RAW_MEASUREMENT, """{"constellationType":1,"svid":7,"cn0DbHz":35.0}""", s * S))
        assertEquals(1 * S, r.debugLastValidPvtNs)
        assertEquals(30 * S, r.debugLastGnssMeasurementNs)
        assertEquals(29_000L, r.debugLastPvtAgeMs)
        assertEquals(NavigationState.GNSS_LOST, r.navigationState)
    }

    @Test fun interleavedStatusMeasurementAndFix_onlyTheFixMovesThePvtClock() {
        val r = ResilienceRuntime()
        fix(r, 1 * S)
        status(r, 2 * S)
        r.accept(rawObs(ObservationType.GNSS_RAW_MEASUREMENT, """{"constellationType":1,"svid":7,"cn0DbHz":35.0}""", 3 * S))
        assertEquals(1 * S, r.debugLastValidPvtNs)
        fix(r, 4 * S)
        assertEquals(4 * S, r.debugLastValidPvtNs)
        status(r, 5 * S)
        assertEquals(1_000L, r.debugLastPvtAgeMs)
        assertEquals(NavigationState.FULL_GNSS, r.navigationState)
    }

    @Test fun staleCoordinatesAreNotCurrent_stateLeavesFullGnss_afterThePvtGoesStale() {
        val r = ResilienceRuntime()
        fix(r, 1 * S)
        val seen = mutableListOf<NavigationState>()
        for (s in 2..12) { status(r, s * S); seen += r.navigationState }
        assertEquals(NavigationState.FULL_GNSS, seen[3])        // t=5 s: PVT age 4 s, still current
        assertTrue(NavigationState.GNSS_DEGRADED in seen)        // PVT age > 5 s -> loss is detected from the PVT clock
        assertEquals(NavigationState.GNSS_LOST, seen.last())
    }

    @Test fun duplicateAndOutOfOrderPvt_areIgnored_andNeverMoveTheClockBackwards() {
        val r = ResilienceRuntime()
        fix(r, 10 * S)
        fix(r, 5 * S, ingestNs = 11 * S)                          // older source timestamp, arrives later
        fix(r, 10 * S, ingestNs = 12 * S)                         // duplicate
        assertEquals(10 * S, r.debugLastValidPvtNs)
        assertEquals(2, r.debugOutOfOrderPvtCount)
        fix(r, 12 * S)
        assertEquals(12 * S, r.debugLastValidPvtNs)
    }

    @Test fun oldPvtDeliveredLate_isStaleOnArrival_notCurrent() {
        val r = ResilienceRuntime()
        fix(r, srcNs = 1 * S, ingestNs = 20 * S)
        assertNull(r.debugLastValidPvtNs)
        assertEquals(1, r.debugStaleOnArrivalPvtCount)
    }

    @Test fun futureDatedAndInvalidFixes_areRejected_andDoNotTouchTheClock() {
        val r = ResilienceRuntime()
        fix(r, srcNs = 100 * S, ingestNs = 10 * S)
        fix(r, 11 * S, json = fixJson(lat = 91.0))
        fix(r, 12 * S, json = fixJson(lon = -181.0))
        fix(r, 13 * S, json = fixJson(acc = null))
        fix(r, 14 * S, json = fixJson(acc = -3.0))
        fix(r, 15 * S, json = "{")
        r.accept(rawObs(ObservationType.GNSS_FIX, fixJson(), null, 16 * S))
        assertNull(r.debugLastValidPvtNs)
        assertEquals(7, r.debugRejectedPvtCount)
    }

    @Test fun ingestionTimeIsMonotonic_anOutOfOrderEventCannotRejuvenateAnything() {
        val r = ResilienceRuntime()
        fix(r, 1 * S)
        status(r, 10 * S)
        assertEquals(9_000L, r.debugLastPvtAgeMs)
        status(r, 2 * S)                                          // older event arrives afterwards
        assertEquals(9_000L, r.debugLastPvtAgeMs)
    }

    @Test fun newValidPvtAfterLoss_resetsTheAge_butOneFixDoesNotRestoreFullGnss() {
        val r = ResilienceRuntime()
        fix(r, 1 * S, json = fixJson(speed = 2.0, bearing = 0.0))
        for (s in 2..20) r.accept(rawObs(ObservationType.SENSOR_ACCEL, "{}", s * S))
        assertEquals(NavigationState.INERTIAL_BRIDGING, r.navigationState)
        fix(r, 21 * S)
        assertEquals(0L, r.debugLastPvtAgeMs)
        for (k in 1..40) r.accept(rawObs(ObservationType.SENSOR_ACCEL, "{}", 21 * S + k * 20_000_000L)) // 50 Hz for 0.8 s
        assertNotEquals("one transient fix must not restore FULL_GNSS", NavigationState.FULL_GNSS, r.navigationState)
    }

    @Test fun recoveryRequiresConsecutiveFixesAndDwell_thenReturnsToFullGnss() {
        val r = ResilienceRuntime()
        fix(r, 1 * S, json = fixJson(speed = 2.0, bearing = 0.0))
        for (s in 2..20) r.accept(rawObs(ObservationType.SENSOR_ACCEL, "{}", s * S))
        fix(r, 21 * S); fix(r, 22 * S)
        for (k in 0..3) r.accept(rawObs(ObservationType.SENSOR_ACCEL, "{}", 22 * S + k * 100_000_000L))
        assertNotEquals("two fixes spanning 1 s are not enough", NavigationState.FULL_GNSS, r.navigationState)
        fix(r, 23 * S)
        val seen = mutableListOf<NavigationState>()
        for (k in 1..6) { r.accept(rawObs(ObservationType.SENSOR_ACCEL, "{}", 23 * S + k * 100_000_000L)); seen += r.navigationState }
        assertTrue("RECOVERY must be visited: $seen", NavigationState.RECOVERY in seen)
        assertEquals(NavigationState.FULL_GNSS, r.navigationState)
    }

    @Test fun lostGnssRecovers_evenWithoutAnyImuOrCellularEvidence() {
        val r = ResilienceRuntime()
        fix(r, 1 * S)
        for (s in 2..12) status(r, s * S)
        assertEquals(NavigationState.GNSS_LOST, r.navigationState)   // baseline defect: trapped here forever
        for (s in 13..16) { fix(r, s * S); status(r, s * S + 1) }
        for (k in 1..6) status(r, 16 * S + k * 100_000_000L)
        assertEquals(NavigationState.FULL_GNSS, r.navigationState)
    }

    @Test fun missingTimestamps_yieldNoEvaluation_andNoFreshness() {
        val r = ResilienceRuntime()
        assertNull(r.accept(rawObs(ObservationType.GNSS_STATUS, STATUS_JSON, null, null)))
        assertNull(r.debugLastValidPvtNs)
    }
}
