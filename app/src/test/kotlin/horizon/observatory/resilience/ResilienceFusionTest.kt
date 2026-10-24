package horizon.observatory.resilience

import horizon.observatory.domain.model.ObservationType
import org.junit.Assert.*
import org.junit.Test

/** G3: state-machine and fusion semantics. */
class ResilienceFusionTest {
    private fun fix(r: ResilienceRuntime, ns: Long, json: String = fixJson()) = r.accept(rawObs(ObservationType.GNSS_FIX, json, ns))
    private fun accel(r: ResilienceRuntime, ns: Long) = r.accept(rawObs(ObservationType.SENSOR_ACCEL, "{}", ns))
    private fun cell(r: ResilienceRuntime, ns: Long) = r.accept(rawObs(ObservationType.CELLULAR_INFO, "{}", ns))
    private fun status(r: ResilienceRuntime, ns: Long) = r.accept(rawObs(ObservationType.GNSS_STATUS, STATUS_JSON, ns))
    private fun ev(id: String, group: String, lat: Double, lon: Double, u: Double, ts: Long) = PositionEvidence(id, group, lat, lon, u, ts)

    @Test fun imuCallbacksFresh_butNoPositionOutput_neverFusion_andMovingWithoutHeadingStaysLost() {
        val r = ResilienceRuntime()
        fix(r, 1 * S, fixJson(speed = 10.0))                       // moving, no course
        val updates = (2..40).mapNotNull { accel(r, it * S) }
        assertEquals(NavigationState.GNSS_LOST, r.navigationState)
        assertTrue(updates.none { it.estimate != null })           // never invents a heading
        // Reported once per distinct reason: heading first, then the age ceiling once past maxAge.
        assertEquals(listOf(CoastingRejection.HEADING_UNAVAILABLE_WHILE_MOVING, CoastingRejection.MAX_AGE_EXCEEDED), updates.mapNotNull { it.coastingRejection })
    }

    @Test fun cellularInfoFresh_butNoCellularCoordinates_isNotAPosition_andNeverFusion() {
        val r = ResilienceRuntime()
        fix(r, 1 * S, fixJson(speed = 2.0, bearing = 0.0))
        status(r, 2 * S)
        val visited = mutableSetOf<NavigationState>()
        for (s in 8..40) {
            if (s >= 15) status(r, s * S)                           // GNSS status resumes without a PVT
            accel(r, s * S + 1); cell(r, s * S + 2)
            visited += r.navigationState
        }
        assertFalse("MULTI_SOURCE_FUSION from IMU+cell+status is the baseline defect: $visited", NavigationState.MULTI_SOURCE_FUSION in visited)
        assertFalse(NavigationState.CELL_AIDED in visited)
        assertTrue(r.debugLastConsistency is ConsistencyDecision.Insufficient)
        assertNotNull(r.debugLastCellularInfoNs)
    }

    private fun lossWithExternal(a: (Long) -> PositionEvidence?, b: (Long) -> PositionEvidence?, untilS: Int = 30): Pair<ResilienceRuntime, Set<NavigationState>> {
        val r = ResilienceRuntime()
        fix(r, 1 * S)
        val visited = mutableSetOf<NavigationState>()
        for (s in 2..untilS) {
            val t = s * S
            a(t)?.let { r.acceptPositionEvidence(it) }
            b(t)?.let { r.acceptPositionEvidence(it) }
            status(r, t)
            visited += r.navigationState
        }
        return r to visited
    }

    @Test fun twoAlignedIndependentPositions_agreeWithinTolerance_enableFusion() {
        val (r, visited) = lossWithExternal(
            { ev("NET", "NETWORK", 50.0, 8.0, 20.0, it) },
            { ev("RX2", "EXTERNAL_RECEIVER", 50.00005, 8.0, 15.0, it) })
        assertTrue("visited=$visited", NavigationState.MULTI_SOURCE_FUSION in visited)
        assertEquals(NavigationState.MULTI_SOURCE_FUSION, r.navigationState)
        assertTrue(r.debugLastConsistency is ConsistencyDecision.Agree)
    }

    @Test fun sourcesDisagreeBeyondThreshold_noFusion() {
        val (r, visited) = lossWithExternal(
            { ev("NET", "NETWORK", 50.0, 8.0, 20.0, it) },
            { ev("RX2", "EXTERNAL_RECEIVER", 50.01, 8.0, 15.0, it) })   // ~1.1 km apart
        assertFalse(NavigationState.MULTI_SOURCE_FUSION in visited)
        assertTrue(r.debugLastConsistency is ConsistencyDecision.Disagree)
        assertEquals(NavigationState.GNSS_LOST, r.navigationState)
    }

    @Test fun oneSourceBecomesStale_fusionIsDropped() {
        val (r, visited) = lossWithExternal(
            { ev("NET", "NETWORK", 50.0, 8.0, 20.0, it) },
            { if (it <= 15 * S) ev("RX2", "EXTERNAL_RECEIVER", 50.00005, 8.0, 15.0, it) else null }, untilS = 40)
        assertTrue(NavigationState.MULTI_SOURCE_FUSION in visited)
        assertEquals(NavigationState.GNSS_LOST, r.navigationState)
        assertTrue(r.debugLastConsistency is ConsistencyDecision.Insufficient)
    }

    @Test fun dependentEvidenceFromTheSameChain_isNotIndependent_noFusion() {
        val (r, visited) = lossWithExternal(
            { ev("NET_A", "NETWORK", 50.0, 8.0, 20.0, it) },
            { ev("NET_B", "NETWORK", 50.0, 8.0, 20.0, it) })
        assertFalse(NavigationState.MULTI_SOURCE_FUSION in visited)
        assertEquals("FEWER_THAN_TWO_INDEPENDENT_SOURCES", (r.debugLastConsistency as ConsistencyDecision.Insufficient).reason)
    }

    @Test fun staleOrMisalignedEvidence_isInsufficient() {
        val checker = PositionConsistencyChecker()
        val now = 100 * S
        val a = ev("A", "G1", 50.0, 8.0, 10.0, now)
        assertEquals("TIME_MISALIGNED", (checker.evaluate(listOf(a, ev("B", "G2", 50.0, 8.0, 10.0, now - 3 * S)), now) as ConsistencyDecision.Insufficient).reason)
        assertTrue(checker.evaluate(listOf(a, ev("B", "G2", 50.0, 8.0, 10.0, now - 6 * S)), now) is ConsistencyDecision.Insufficient)
        assertTrue(checker.evaluate(listOf(a, ev("B", "G2", 50.0, 8.0, 0.0, now)), now) is ConsistencyDecision.Insufficient)   // zero uncertainty is not evidence
        assertTrue(checker.evaluate(listOf(a, ev("B", "G2", Double.NaN, 8.0, 5.0, now)), now) is ConsistencyDecision.Insufficient)
        assertTrue(checker.evaluate(listOf(a, ev("B", "G2", 50.0, 8.0, 10.0, now)), now) is ConsistencyDecision.Agree)
    }

    @Test fun runtimeRejectsInvalidExternalEvidence_andOlderItemsForTheSameSource() {
        val r = ResilienceRuntime()
        assertFalse(r.acceptPositionEvidence(ev("", "G", 50.0, 8.0, 5.0, 5 * S)))
        assertFalse(r.acceptPositionEvidence(ev("A", "", 50.0, 8.0, 5.0, 5 * S)))
        assertFalse(r.acceptPositionEvidence(ev("A", "G", 95.0, 8.0, 5.0, 5 * S)))
        assertFalse(r.acceptPositionEvidence(ev("A", "G", 50.0, 8.0, -1.0, 5 * S)))
        assertTrue(r.acceptPositionEvidence(ev("A", "G", 50.0, 8.0, 5.0, 5 * S)))
        assertFalse(r.acceptPositionEvidence(ev("A", "G", 50.0, 8.0, 5.0, 4 * S)))
    }

    @Test fun glost_recoversWhenGnssIsBack_evenWithoutImuOrCell() {
        val m = NavigationContinuityStateMachine(2)
        repeat(4) { m.update(NavigationEvidence(gnssLost = true)) }
        assertEquals(NavigationState.GNSS_LOST, m.state)
        repeat(2) { m.update(NavigationEvidence(healthyGnss = true, recoveryGnss = true)) }
        assertEquals(NavigationState.RECOVERY, m.state)
        repeat(2) { m.update(NavigationEvidence(healthyGnss = true)) }
        assertEquals(NavigationState.FULL_GNSS, m.state)
    }

    @Test fun stateMachine_fusionIsUnreachableFromSensorFlagsAlone() {
        val m = NavigationContinuityStateMachine(2)
        repeat(4) { m.update(NavigationEvidence(gnssLost = true)) }
        repeat(20) { m.update(NavigationEvidence(gnssLost = true, validImu = true, validCell = true)) }
        assertNotEquals(NavigationState.MULTI_SOURCE_FUSION, m.state)
        repeat(3) { m.update(NavigationEvidence(gnssLost = true, validImu = true, validCell = true, multiSourceAgreement = true)) }
        assertEquals(NavigationState.MULTI_SOURCE_FUSION, m.state)
        repeat(3) { m.update(NavigationEvidence(gnssLost = true, validImu = true)) }
        assertEquals("fusion must drop when agreement vanishes", NavigationState.INERTIAL_BRIDGING, m.state)
        repeat(3) { m.update(NavigationEvidence(gnssLost = true)) }
        assertEquals("a stale IMU must not keep the bridging label", NavigationState.GNSS_LOST, m.state)
    }

    @Test fun unknownEvidenceStaysUnknown_neverNormalByDefault() {
        assertEquals(InterferenceState.UNKNOWN, InterferenceEvidenceEngine().assess(InterferenceSnapshot()).state)
        val r = ResilienceRuntime()
        val update = r.accept(rawObs(ObservationType.GNSS_RAW_MEASUREMENT, """{"constellationType":1,"svid":7,"cn0DbHz":5.0}""", 1 * S))
        assertEquals(InterferenceState.UNKNOWN, update!!.interferenceState)
    }

    @Test fun duplicateEvents_doNotCauseStateOscillation() {
        val r = ResilienceRuntime()
        fix(r, 1 * S)
        val states = mutableListOf<NavigationState>()
        repeat(50) { status(r, 2 * S); states += r.navigationState }   // identical event delivered repeatedly
        assertEquals(setOf(NavigationState.FULL_GNSS), states.toSet())
    }
}
