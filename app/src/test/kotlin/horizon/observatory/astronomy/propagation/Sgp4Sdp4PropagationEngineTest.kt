package horizon.observatory.astronomy.propagation

import horizon.observatory.astronomy.OmmParser
import horizon.observatory.astronomy.OmmRecord
import horizon.observatory.astronomy.time.PropagationTime
import horizon.observatory.astronomy.time.TimeBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Engine CONTRACT tests only. The fake backends below are TEST DOUBLES: the numbers they return are
 * arbitrary and carry no orbital meaning. Nothing here validates SGP4/SDP4 numerics; that requires
 * published reference vectors (see Sgp4ReferenceVectorTest, PENDING).
 */
class Sgp4Sdp4PropagationEngineTest {
    private val record: OmmRecord = OmmParser.parse(
        """[{"NORAD_CAT_ID":1,"EPOCH":"2026-01-01T00:00:00.500000","MEAN_MOTION":2.0,"ECCENTRICITY":0.01,
        "INCLINATION":90.0,"RA_OF_ASC_NODE":180.0,"ARG_OF_PERICENTER":270.0,"MEAN_ANOMALY":45.0,
        "BSTAR":0.0002,"MEAN_MOTION_DOT":0.0000001,"EPHEMERIS_TYPE":0}]"""
    ).records.single()

    private val at = PropagationTime(record.epochUtc.toEpochMilli() + 60_000L, TimeBasis.DEVICE_CLOCK_UTC)

    private class RecordingBackend(private val reply: BackendResult) : Sgp4Sdp4Backend {
        var input: Sgp4Sdp4Input? = null
        var minutes: Double? = null
        override val name = "TEST_DOUBLE"
        override val version: String? = null
        override val supportsDeepSpace = true
        override fun propagate(input: Sgp4Sdp4Input, minutesSinceEpoch: Double): BackendResult {
            this.input = input; this.minutes = minutesSinceEpoch; return reply
        }
    }

    private val okState = BackendState(1.0, 2.0, 3.0, 0.1, 0.2, 0.3)

    @Test
    fun `unavailable backend yields ENGINE_UNAVAILABLE and never a state`() {
        val engine = Sgp4Sdp4PropagationEngine(UnavailableSgp4Sdp4Backend)
        val r = engine.propagate(record, at)
        r as PropagationResult.Failure
        assertEquals(PropagationFailureReason.ENGINE_UNAVAILABLE, r.reason)
        assertEquals(EngineVerificationStatus.UNVERIFIED, r.engine.verification)
    }

    @Test
    fun `OMM units are converted to backend units`() {
        val backend = RecordingBackend(BackendResult.Ok(okState, PropagationModelFamily.SGP4_NEAR_EARTH))
        Sgp4Sdp4PropagationEngine(backend).propagate(record, at)
        val i = backend.input!!
        assertEquals(2.0 * 2.0 * Math.PI / 1440.0, i.meanMotionRadPerMin, 1e-12)
        assertEquals(Math.PI / 2.0, i.inclinationRad, 1e-12)
        assertEquals(Math.PI, i.raanRad, 1e-12)
        assertEquals(1.5 * Math.PI, i.argOfPericenterRad, 1e-12)
        assertEquals(Math.toRadians(45.0), i.meanAnomalyRad, 1e-12)
        assertEquals(0.0002, i.bstar, 0.0)
        assertEquals(0.0000001, i.meanMotionDotRevPerDay2!!, 0.0)
        assertNull(i.meanMotionDdotRevPerDay3)
        assertEquals("2026-01-01T00:00:00.500000", i.epochRaw)
    }

    @Test
    fun `minutes since epoch is signed and exact for whole seconds`() {
        val backend = RecordingBackend(BackendResult.Ok(okState, PropagationModelFamily.SGP4_NEAR_EARTH))
        val engine = Sgp4Sdp4PropagationEngine(backend)
        engine.propagate(record, at)
        assertEquals(1.0, backend.minutes!!, 1e-12)
        engine.propagate(record, PropagationTime(record.epochUtc.toEpochMilli(), TimeBasis.DEVICE_CLOCK_UTC))
        assertEquals(0.0, backend.minutes!!, 1e-12)
        engine.propagate(record, PropagationTime(record.epochUtc.toEpochMilli() - 120_000L, TimeBasis.DEVICE_CLOCK_UTC))
        assertEquals(-2.0, backend.minutes!!, 1e-12)
    }

    @Test
    fun `success carries the backend's model, minutes from epoch and descriptor`() {
        val backend = RecordingBackend(BackendResult.Ok(okState, PropagationModelFamily.SDP4_DEEP_SPACE))
        val r = Sgp4Sdp4PropagationEngine(backend).propagate(record, at)
        r as PropagationResult.Success
        assertEquals(PropagationModelFamily.SDP4_DEEP_SPACE, r.modelUsed)
        assertEquals(1.0, r.minutesFromEpoch, 1e-12)
        assertEquals(1.0, r.state.xKm, 0.0)
        assertEquals(at, r.state.atTime)
        assertEquals("TEST_DOUBLE", r.engine.backendName)
        assertEquals(EngineVerificationStatus.UNVERIFIED, r.engine.verification)
    }

    @Test
    fun `backend error maps to PROPAGATION_ERROR with code`() {
        val r = Sgp4Sdp4PropagationEngine(RecordingBackend(BackendResult.Error(6, "decayed"))).propagate(record, at)
        r as PropagationResult.Failure
        assertEquals(PropagationFailureReason.PROPAGATION_ERROR, r.reason)
        assertTrue(r.detail.contains("6") && r.detail.contains("decayed"))
    }

    @Test
    fun `non-finite backend output is rejected`() {
        val bad = BackendState(Double.NaN, 0.0, 0.0, 0.0, 0.0, 0.0)
        val r = Sgp4Sdp4PropagationEngine(RecordingBackend(BackendResult.Ok(bad, PropagationModelFamily.SGP4_NEAR_EARTH))).propagate(record, at)
        r as PropagationResult.Failure
        assertEquals(PropagationFailureReason.NON_FINITE_OUTPUT, r.reason)
        val inf = BackendState(0.0, Double.POSITIVE_INFINITY, 0.0, 0.0, 0.0, 0.0)
        val r2 = Sgp4Sdp4PropagationEngine(RecordingBackend(BackendResult.Ok(inf, PropagationModelFamily.SGP4_NEAR_EARTH))).propagate(record, at)
        assertEquals(PropagationFailureReason.NON_FINITE_OUTPUT, (r2 as PropagationResult.Failure).reason)
    }

    @Test
    fun `invalid orbital input is rejected before reaching the backend`() {
        val backend = RecordingBackend(BackendResult.Ok(okState, PropagationModelFamily.SGP4_NEAR_EARTH))
        val engine = Sgp4Sdp4PropagationEngine(backend)
        listOf(
            record.copy(eccentricity = 1.0),
            record.copy(eccentricity = -0.1),
            record.copy(meanMotionRevPerDay = 0.0),
            record.copy(meanMotionRevPerDay = Double.NaN),
            record.copy(inclinationDeg = 181.0),
            record.copy(bstar = Double.POSITIVE_INFINITY)
        ).forEach {
            val r = engine.propagate(it, at)
            assertEquals(PropagationFailureReason.INVALID_INPUT, (r as PropagationResult.Failure).reason)
        }
        assertNull(backend.input)
    }

    @Test
    fun `engine never self-declares verified`() {
        assertEquals(
            EngineVerificationStatus.UNVERIFIED,
            Sgp4Sdp4PropagationEngine(RecordingBackend(BackendResult.Unavailable("x"))).descriptor.verification
        )
    }
}
