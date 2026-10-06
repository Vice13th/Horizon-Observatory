package horizon.observatory.astronomy.propagation

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class Sgp4Sdp4BackendContractTest {
    @Test
    fun `input preserves OMM identity and optional fields without third party types`() {
        val input = Sgp4Sdp4Input(
            noradCatId = 25544L,
            objectName = "ISS",
            objectId = "1998-067A",
            classification = "U",
            epochUtc = Instant.parse("2026-01-01T00:00:00Z"),
            epochRaw = "2026-01-01T00:00:00.000000",
            meanMotionRadPerMin = 2.0 * Math.PI / 92.0,
            eccentricity = 0.001,
            inclinationRad = Math.toRadians(51.6),
            raanRad = 0.1,
            argOfPericenterRad = 0.2,
            meanAnomalyRad = 0.3,
            bstar = 0.0001,
            meanMotionDotRevPerDay2 = 0.0,
            meanMotionDdotRevPerDay3 = 0.0,
            ephemerisType = 0,
            elementSetNo = 999,
            revAtEpoch = 12345L,
        )

        assertEquals("ISS", input.objectName)
        assertEquals("1998-067A", input.objectId)
        assertEquals("U", input.classification)
        assertEquals(999, input.elementSetNo)
        assertEquals(12345L, input.revAtEpoch)
    }

    @Test
    fun `unavailable backend remains explicit and never fabricates a state`() {
        val result = UnavailableSgp4Sdp4Backend.propagate(
            Sgp4Sdp4Input(
                noradCatId = 1L,
                objectName = null,
                objectId = null,
                classification = null,
                epochUtc = Instant.EPOCH,
                epochRaw = "1970-01-01T00:00:00Z",
                meanMotionRadPerMin = 1.0,
                eccentricity = 0.0,
                inclinationRad = 0.0,
                raanRad = 0.0,
                argOfPericenterRad = 0.0,
                meanAnomalyRad = 0.0,
                bstar = 0.0,
                meanMotionDotRevPerDay2 = null,
                meanMotionDdotRevPerDay3 = null,
                ephemerisType = null,
                elementSetNo = null,
                revAtEpoch = null,
            ),
            0.0,
        )
        assertEquals(BackendResult.Unavailable::class, result::class)
    }
}
