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

    @Test
    fun `Vallado near-earth reference case 00005 matches independent SGP4 vectors`() {
        val input = Sgp4Sdp4Input(
            noradCatId = 5L,
            objectName = "VANGUARD 1",
            objectId = "1958-002B",
            classification = "U",
            epochUtc = Instant.parse("2000-06-27T18:50:19.733571Z"),
            epochRaw = "2000-06-27T18:50:19.733571Z",
            meanMotionRadPerMin = 0.04722944544077856,
            eccentricity = 0.1859667,
            inclinationRad = 0.5980929187319208,
            raanRad = 6.08638547138321,
            argOfPericenterRad = 5.790416027488515,
            meanAnomalyRad = 0.3373093125574321,
            bstar = 2.8098e-05,
            meanMotionDotRevPerDay2 = 2.3e-07,
            meanMotionDdotRevPerDay3 = 0.0,
            ephemerisType = 0,
            elementSetNo = 475,
            revAtEpoch = 41366L,
        )
        val backend = OrbitCoreSgp4Sdp4Backend()

        val atEpoch = backend.propagate(input, 0.0)
        val at360 = backend.propagate(input, 360.0)
        assertTrue("epoch result must be Ok: $atEpoch", atEpoch is BackendResult.Ok)
        assertTrue("360min result must be Ok: $at360", at360 is BackendResult.Ok)
        atEpoch as BackendResult.Ok
        at360 as BackendResult.Ok
        assertEquals(PropagationModelFamily.SGP4_NEAR_EARTH, atEpoch.modelUsed)
        assertEquals(PropagationModelFamily.SGP4_NEAR_EARTH, at360.modelUsed)

        assertStateClose(atEpoch.state, 7022.465292664064, -1400.0829675535551, 0.03995155416521326, 1.8938410145129514, 6.405893759209842, 4.534807250354738)
        assertStateClose(at360.state, -7154.031202015707, -3783.176825036568, -3536.1941229422155, 4.741887408996156, -4.151817765373694, -2.0939354249073663)
    }

    private fun assertStateClose(
        state: horizon.observatory.astronomy.propagation.BackendState,
        x: Double, y: Double, z: Double,
        vx: Double, vy: Double, vz: Double,
    ) {
        assertEquals(x, state.xKm, 1e-6)
        assertEquals(y, state.yKm, 1e-6)
        assertEquals(z, state.zKm, 1e-6)
        assertEquals(vx, state.vxKmS, 1e-9)
        assertEquals(vy, state.vyKmS, 1e-9)
        assertEquals(vz, state.vzKmS, 1e-9)
    }


    @Test
    fun `Vallado deep-space 12-hour resonant case 08195 matches independent SGP4 vectors`() {
        val input = Sgp4Sdp4Input(
            noradCatId = 8195L,
            objectName = "MOLNIYA 2-14",
            objectId = "1975-081A",
            classification = "U",
            epochUtc = Instant.parse("2006-06-25T07:58:18.143636Z"),
            epochRaw = "2006-06-25T07:58:18.143636Z",
            meanMotionRadPerMin = 0.008748086888067465,
            eccentricity = 0.6877146,
            inclinationRad = 1.119778813470034,
            raanRad = 4.87072001413786,
            argOfPericenterRad = 4.621022739372039,
            meanAnomalyRad = 0.3530050585206171,
            bstar = 1.1873e-4,
            meanMotionDotRevPerDay2 = 9.9e-7,
            meanMotionDdotRevPerDay3 = 0.0,
            ephemerisType = 0,
            elementSetNo = 81,
            revAtEpoch = 22565L,
        )
        val backend = OrbitCoreSgp4Sdp4Backend()
        val expected = listOf(
            0.0 to doubleArrayOf(2349.8948335005193, -14785.938115615325, 0.021193784148377418, 2.7214880955588243, -3.256811654658782, 4.498416672371417),
            120.0 to doubleArrayOf(15223.917136582058, -17852.958817127143, 25280.395582242327, 1.0790417322899628, 0.8751873723849997, 2.485682812742269),
            1440.0 to doubleArrayOf(2890.8063826773023, -15446.439523001181, 948.7701017643215, 2.6544074895934378, -2.9093448948293292, 4.486437361921106),
        )
        for ((minutes, e) in expected) {
            val result = backend.propagate(input, minutes)
            assertTrue("$minutes min result must be Ok: $result", result is BackendResult.Ok)
            result as BackendResult.Ok
            assertEquals(PropagationModelFamily.SDP4_DEEP_SPACE, result.modelUsed)
            assertStateClose(result.state, e[0], e[1], e[2], e[3], e[4], e[5])
        }
    }

}