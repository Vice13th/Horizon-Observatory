package horizon.observatory.resilience

import org.junit.Assert.*
import org.junit.Test

class ResilienceEngineTest {
    @Test fun capabilityContract_preservesAvailableUnavailableUnknown() {
        val c = CapabilityContract.from(listOf(
            CapabilityEvidence(CapabilityKey.RAW_GNSS, CapabilityAvailability.AVAILABLE, "GNSS_SOURCE", 1L),
            CapabilityEvidence(CapabilityKey.AGC, CapabilityAvailability.UNAVAILABLE, "DEVICE", 1L),
            CapabilityEvidence(CapabilityKey.ORIENTATION, CapabilityAvailability.UNKNOWN, "DEVICE", 1L)
        ))
        assertEquals(CapabilityAvailability.AVAILABLE, c[CapabilityKey.RAW_GNSS].availability)
        assertEquals(CapabilityAvailability.UNAVAILABLE, c[CapabilityKey.AGC].availability)
        assertEquals(CapabilityAvailability.UNKNOWN, c[CapabilityKey.ORIENTATION].availability)
    }

    @Test fun interferenceEngine_doesNotLabelPhysicalCause_withoutEvidence() {
        val a = InterferenceEvidenceEngine().assess(InterferenceSnapshot())
        assertEquals(InterferenceState.UNKNOWN, a.state)
    }

    @Test fun interferenceEngine_detectsJamLikely_fromStrongMultiSignalCollapse() {
        val a = InterferenceEvidenceEngine().assess(InterferenceSnapshot(
            cn0MedianDbHz = 20.0, baselineCn0MedianDbHz = 35.0,
            trackedSatelliteCount = 5, baselineTrackedSatelliteCount = 20,
            pvtDegraded = true, measurementContinuityLost = true
        ))
        assertEquals(InterferenceState.JAM_LIKELY, a.state)
        assertTrue(a.score >= 0.60)
        assertTrue(a.evidence.isNotEmpty())
    }

    @Test fun trustRanking_isDeterministic_andRecordsRejectReason() {
        val result = MeasurementTrustEngine().rank(listOf(
            MeasurementCandidate("B", signalStability = 0.1, freshness = 0.1),
            MeasurementCandidate("A", signalStability = 1.0, freshness = 1.0),
            MeasurementCandidate("C")
        ))
        assertEquals(listOf("A", "B", "C"), result.map { it.id })
        assertEquals(TrustDecision.REJECT, result[1].decision)
        assertTrue(result[1].reasons.any { it.contains("LOW_SIGNALSTABILITY") })
        assertEquals(TrustDecision.DOWN_WEIGHT, result[2].decision)
    }

    @Test fun navigationStateMachine_debouncesLoss_andUsesExplicitContinuityStates() {
        val m = NavigationContinuityStateMachine(transitionDebounceSamples = 2)
        assertNull(m.update(NavigationEvidence(degradedGnss = true)))
        val degraded = m.update(NavigationEvidence(degradedGnss = true))
        assertEquals(NavigationState.GNSS_DEGRADED, degraded!!.to)
        assertNull(m.update(NavigationEvidence(partialGnss = true)))
        val partial = m.update(NavigationEvidence(partialGnss = true))
        assertEquals(NavigationState.PARTIAL_GNSS, partial!!.to)
        assertNull(m.update(NavigationEvidence(gnssLost = true)))
        val lost = m.update(NavigationEvidence(gnssLost = true))
        assertEquals(NavigationState.GNSS_LOST, lost!!.to)
        assertNull(m.update(NavigationEvidence(validImu = true)))
        val inertial = m.update(NavigationEvidence(validImu = true))
        assertEquals(NavigationState.INERTIAL_BRIDGING, inertial!!.to)
    }

    @Test fun deadReckoning_growsUncertainty_andNeverClaimsFreshGnss() {
        val last = TrustedPvt(50.0, 8.0, 100.0, 10.0, null, 1_000_000_000L, 5.0, 8.0)
        val a = DeadReckoningBridge().estimate(BridgeInput(last, 10.0, headingDeg = null, imuAvailable = true))
        assertEquals("DEAD_RECKONED_FROM_LAST_TRUSTED_PVT", a.provenance)
        assertEquals(NavigationState.INERTIAL_BRIDGING, a.navigationState)
        assertTrue(a.horizontalUncertaintyM > last.horizontalUncertaintyM)
        assertTrue(a.verticalUncertaintyM > last.verticalUncertaintyM)
        assertEquals(last.latitudeDeg, a.latitudeDeg, 0.000001)
    }
}
