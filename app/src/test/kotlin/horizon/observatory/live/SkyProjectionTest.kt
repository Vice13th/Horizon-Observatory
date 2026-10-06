package horizon.observatory.live

import horizon.observatory.astronomy.orientation.DeviceOrientationState
import horizon.observatory.astronomy.orientation.HeadingReference
import horizon.observatory.astronomy.time.MonotonicElapsedNanos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyProjectionTest {
    private val t = MonotonicElapsedNanos(1L)
    private val north = SkyOrientationMode.NorthUp

    private fun magnetic(h: Double) = DeviceOrientationState.Available(h, 5.0, HeadingReference.MAGNETIC_NORTH, t, 3)

    @Test
    fun `north-up projection matches the existing plot convention`() {
        val zenith = SkyProjection.project(123.0, 90.0, north)!!
        assertEquals(0.0, zenith.x, 1e-12); assertEquals(0.0, zenith.y, 1e-12)
        val east = SkyProjection.project(90.0, 0.0, north)!!
        assertEquals(1.0, east.x, 1e-12); assertEquals(0.0, east.y, 1e-12)
        val northHorizon = SkyProjection.project(0.0, 0.0, north)!!
        assertEquals(0.0, northHorizon.x, 1e-12); assertEquals(1.0, northHorizon.y, 1e-12)
        val half = SkyProjection.project(180.0, 45.0, north)!!
        assertEquals(0.0, half.x, 1e-12); assertEquals(-0.5, half.y, 1e-12)
    }

    @Test
    fun `heading-up rotates the same observation without changing its azimuth or elevation`() {
        val mode = SkyOrientationMode.HeadingUp(90.0)
        val east = SkyProjection.project(90.0, 0.0, mode)!!
        assertEquals(0.0, east.x, 1e-12); assertEquals(1.0, east.y, 1e-12)
        val north = SkyProjection.project(0.0, 0.0, mode)!!
        assertEquals(-1.0, north.x, 1e-12); assertEquals(0.0, north.y, 1e-12)
        assertEquals(1.0, Math.hypot(east.x, east.y), 1e-12)
    }

    @Test
    fun `rotation preserves radius for every heading`() {
        for (h in listOf(0.0, 33.0, 180.0, 359.0)) {
            val p = SkyProjection.project(77.0, 30.0, SkyOrientationMode.HeadingUp(h))!!
            assertEquals(60.0 / 90.0, Math.hypot(p.x, p.y), 1e-12)
        }
    }

    @Test
    fun `below-horizon satellites fall outside the horizon ring`() {
        val p = SkyProjection.project(0.0, -10.0, north)!!
        assertTrue(Math.hypot(p.x, p.y) > 1.0)
    }

    @Test
    fun `non-finite input is not plotted`() {
        assertNull(SkyProjection.project(Double.NaN, 10.0, north))
        assertNull(SkyProjection.project(10.0, Double.POSITIVE_INFINITY, north))
    }

    @Test
    fun `cardinal labels rotate with the heading`() {
        val n = SkyProjection.cardinal(0.0, SkyOrientationMode.HeadingUp(180.0))
        assertEquals(0.0, n.x, 1e-12); assertEquals(-1.0, n.y, 1e-12)
        assertNotNull(SkyProjection.cardinal(90.0, north))
    }

    @Test
    fun `recorded data is never rotated`() {
        val d = SkyProjection.decide(magnetic(10.0), 5.0, isLive = false)
        assertEquals(SkyOrientationMode.NorthUp, d.mode)
        assertEquals(SkyOrientationStatus.NORTH_UP_NOT_LIVE, d.status)
    }

    @Test
    fun `unavailable and undefined poses stay north-up with an explicit reason`() {
        val u = SkyProjection.decide(DeviceOrientationState.Unavailable("no sensor"), 5.0, true)
        assertEquals(SkyOrientationStatus.NORTH_UP_ORIENTATION_UNAVAILABLE, u.status)
        assertTrue(u.detail.contains("no sensor"))
        val h = SkyProjection.decide(DeviceOrientationState.HeadingUndefined("too upright", 80.0, t, 3), 5.0, true)
        assertEquals(SkyOrientationStatus.NORTH_UP_HEADING_UNDEFINED, h.status)
        assertEquals(SkyOrientationMode.NorthUp, h.mode)
    }

    @Test
    fun `magnetic heading without declination does not rotate the true-north plot`() {
        val d = SkyProjection.decide(magnetic(40.0), null, true)
        assertEquals(SkyOrientationMode.NorthUp, d.mode)
        assertEquals(SkyOrientationStatus.NORTH_UP_DECLINATION_UNAVAILABLE, d.status)
    }

    @Test
    fun `live magnetic heading plus declination rotates by the true heading`() {
        val d = SkyProjection.decide(magnetic(350.0), 20.0, true)
        assertEquals(SkyOrientationStatus.ROTATING_TRUE_NORTH, d.status)
        assertEquals(SkyOrientationMode.HeadingUp(10.0), d.mode)
    }
}
