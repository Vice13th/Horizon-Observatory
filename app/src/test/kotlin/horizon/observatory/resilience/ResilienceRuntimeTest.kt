package horizon.observatory.resilience

import horizon.observatory.domain.model.CapabilityState
import horizon.observatory.domain.model.EvidenceStatus
import horizon.observatory.domain.model.ObservationProvenance
import horizon.observatory.domain.model.ObservationType
import horizon.observatory.domain.model.RawObservation
import horizon.observatory.domain.model.TimestampDomain
import org.junit.Assert.*
import org.junit.Test

class ResilienceRuntimeTest {
    private fun obs(type: ObservationType, json: String, ns: Long) =
        RawObservation(type, ns / 1_000_000L, "test", json, ns, ns, technology = "TEST",
            capabilityState = CapabilityState.AVAILABLE_NOW, evidenceStatus = EvidenceStatus.MEASURED,
            provenance = ObservationProvenance.RAW_ACQUISITION,
            timestampDomain = TimestampDomain.ANDROID_ELAPSED_REALTIME_NANOS)

    @Test fun rawObservation_remainsUntouched() {
        val runtime = ResilienceRuntime()
        val raw = obs(ObservationType.GNSS_STATUS, """{"satelliteCount":8,"satellites":[{"cn0DbHz":35,"usedInFix":true}]}""", 1_000_000_000L)
        val before = raw
        runtime.accept(raw)
        assertEquals(before, raw)
    }

    @Test fun replay_is_deterministic_for_same_raw_sequence() {
        val observations = listOf(
            obs(ObservationType.GNSS_STATUS, """{"satelliteCount":8,"satellites":[{"cn0DbHz":35,"usedInFix":true}]}""", 1_000_000_000L),
            obs(ObservationType.GNSS_FIX, """{"latitude":50.0,"longitude":8.0,"accuracyMeters":5.0}""", 2_000_000_000L),
            obs(ObservationType.SENSOR_ACCEL, "{}", 9_000_000_000L)
        )
        val a = ResilienceRuntime.replay(observations)
        val b = ResilienceRuntime.replay(observations)
        assertEquals(a, b)
    }

    @Test fun poorRawMeasurement_produces_a_derived_trust_decision_without_mutating_raw() {
        val runtime = ResilienceRuntime()
        val raw = obs(ObservationType.GNSS_RAW_MEASUREMENT, """{"constellationType":1,"svid":7,"cn0DbHz":5.0}""", 1_000_000_000L)
        val before = raw
        val update = runtime.accept(raw)
        assertNotNull(update)
        assertEquals(TrustDecision.REJECT, update!!.trustDecisions.single().decision)
        assertEquals(before, raw)
    }

    @Test fun inertialBridge_uses_elapsed_time_and_grows_uncertainty() {
        val runtime = ResilienceRuntime()
        runtime.accept(obs(ObservationType.GNSS_FIX, """{"latitude":50.0,"longitude":8.0,"altitude":100.0,"accuracyMeters":5.0,"speedMetersPerSecond":2.0,"bearingDegrees":0.0}""", 1_000_000_000L))
        runtime.accept(obs(ObservationType.GNSS_STATUS, """{"satelliteCount":6,"satellites":[{"cn0DbHz":35,"usedInFix":true}]}""", 2_000_000_000L))
        runtime.accept(obs(ObservationType.SENSOR_ACCEL, "{}", 8_000_000_000L))
        runtime.accept(obs(ObservationType.SENSOR_ACCEL, "{}", 9_000_000_000L))
        runtime.accept(obs(ObservationType.SENSOR_ACCEL, "{}", 10_000_000_000L))
        runtime.accept(obs(ObservationType.SENSOR_ACCEL, "{}", 11_000_000_000L))
        runtime.accept(obs(ObservationType.SENSOR_ACCEL, "{}", 12_000_000_000L))
        val update = runtime.accept(obs(ObservationType.SENSOR_ACCEL, "{}", 13_000_000_000L))
        assertNotNull(update)
        assertEquals(50.0 + (12.0 * 2.0) / 111_320.0, update!!.estimate!!.latitudeDeg, 0.000001)
        assertTrue(update.estimate.horizontalUncertaintyM > 5.0)
        assertEquals(13_000_000_000L, update.estimate.timestampMonotonicNs)
    }

    @Test fun trustedFix_outageFollowsExplicitStates_andNeverInventsHeading() {
        val runtime = ResilienceRuntime()
        runtime.accept(obs(ObservationType.GNSS_FIX, """{"latitude":50.0,"longitude":8.0,"accuracyMeters":5.0,"speedMetersPerSecond":2.0}""", 1_000_000_000L))
        runtime.accept(obs(ObservationType.GNSS_STATUS, """{"satelliteCount":6,"satellites":[{"cn0DbHz":35,"usedInFix":true}]}""", 2_000_000_000L))
        assertEquals(2_000_000_000L, runtime.debugLastGnssEvidenceNs)
        runtime.accept(obs(ObservationType.SENSOR_ACCEL, "{}", 8_000_000_000L))
        assertEquals(2_000_000_000L, runtime.debugLastGnssEvidenceNs)
        runtime.accept(obs(ObservationType.SENSOR_ACCEL, "{}", 9_000_000_000L))
        assertEquals(NavigationState.GNSS_DEGRADED, runtime.navigationState)
        runtime.accept(obs(ObservationType.SENSOR_ACCEL, "{}", 10_000_000_000L))
        assertEquals(8000L, runtime.debugLastComputedGnssAgeMs)
        runtime.accept(obs(ObservationType.SENSOR_ACCEL, "{}", 11_000_000_000L))
        assertEquals(9000L, runtime.debugLastComputedGnssAgeMs)
        assertEquals(NavigationState.GNSS_LOST, runtime.navigationState)
        runtime.accept(obs(ObservationType.SENSOR_ACCEL, "{}", 12_000_000_000L))
        val update = runtime.accept(obs(ObservationType.SENSOR_ACCEL, "{}", 13_000_000_000L))
        assertNotNull(update)
        assertEquals(NavigationState.INERTIAL_BRIDGING, runtime.navigationState)
        assertEquals("DEAD_RECKONED_FROM_LAST_TRUSTED_PVT", update!!.estimate!!.provenance)
        assertEquals(50.0, update.estimate.latitudeDeg, 0.000001)
        assertEquals(8.0, update.estimate.longitudeDeg, 0.000001)
    }
}
