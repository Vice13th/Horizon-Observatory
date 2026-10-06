package horizon.observatory.orbitcore

import horizon.observatory.astronomy.propagation.BackendResult
import horizon.observatory.astronomy.propagation.PropagationModelFamily
import horizon.observatory.astronomy.propagation.Sgp4Sdp4Input
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitCoreSgp4Sdp4BackendTest {
    @Test
    fun `real OrbitCore runtime is reachable and returns finite state`() {
        val result = OrbitCoreSgp4Sdp4Backend().propagate(
            Sgp4Sdp4Input(
                noradCatId = 25544L,
                objectName = "ISS (ZARYA)",
                objectId = "1998-067A",
                classification = "U",
                epochUtc = Instant.parse("2026-01-01T00:00:00Z"),
                epochRaw = "2026-01-01T00:00:00.000000",
                meanMotionRadPerMin = 15.5 * 2.0 * Math.PI / 1440.0,
                eccentricity = 0.0005,
                inclinationRad = Math.toRadians(51.64),
                raanRad = Math.toRadians(10.0),
                argOfPericenterRad = Math.toRadians(20.0),
                meanAnomalyRad = Math.toRadians(30.0),
                bstar = 0.0001,
                meanMotionDotRevPerDay2 = 0.0,
                meanMotionDdotRevPerDay3 = 0.0,
                ephemerisType = 0,
                elementSetNo = 999,
                revAtEpoch = 12345L,
            ),
            minutesSinceEpoch = 0.0,
        )

        assertTrue("OrbitCore must return Ok, got $result", result is BackendResult.Ok)
        result as BackendResult.Ok
        assertEquals(PropagationModelFamily.SGP4_NEAR_EARTH, result.modelUsed)
        assertTrue(result.state.xKm.isFinite())
        assertTrue(result.state.yKm.isFinite())
        assertTrue(result.state.zKm.isFinite())
        assertTrue(result.state.vxKmS.isFinite())
        assertTrue(result.state.vyKmS.isFinite())
        assertTrue(result.state.vzKmS.isFinite())
    }

    @Test
    fun `real OrbitCore runtime selects deep space model for GNSS period and returns finite state`() {
        val result = OrbitCoreSgp4Sdp4Backend().propagate(
            Sgp4Sdp4Input(
                noradCatId = 99999L,
                objectName = "GNSS-TEST",
                objectId = null,
                classification = "U",
                epochUtc = Instant.parse("2026-01-01T00:00:00Z"),
                epochRaw = "2026-01-01T00:00:00Z",
                meanMotionRadPerMin = 2.0 * Math.PI / 720.0,
                eccentricity = 0.01,
                inclinationRad = Math.toRadians(55.0),
                raanRad = Math.toRadians(40.0),
                argOfPericenterRad = Math.toRadians(10.0),
                meanAnomalyRad = Math.toRadians(15.0),
                bstar = 0.0,
                meanMotionDotRevPerDay2 = 0.0,
                meanMotionDdotRevPerDay3 = 0.0,
                ephemerisType = 0,
                elementSetNo = 1,
                revAtEpoch = 1L,
            ),
            minutesSinceEpoch = 0.0,
        )
        assertTrue("OrbitCore must return Ok, got $result", result is BackendResult.Ok)
        result as BackendResult.Ok
        assertEquals(PropagationModelFamily.SDP4_DEEP_SPACE, result.modelUsed)
        assertTrue(result.state.xKm.isFinite())
        assertTrue(result.state.yKm.isFinite())
        assertTrue(result.state.zKm.isFinite())
        assertTrue(result.state.vxKmS.isFinite())
        assertTrue(result.state.vyKmS.isFinite())
        assertTrue(result.state.vzKmS.isFinite())
    }
}