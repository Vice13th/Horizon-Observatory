package horizon.observatory.live

import horizon.observatory.astronomy.orientation.DeviceOrientationState
import horizon.observatory.astronomy.orientation.HeadingReference
import horizon.observatory.astronomy.time.MonotonicElapsedNanos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Expected headings follow from the rotation geometry (identity -> top edge points north; +90 deg
 * about up turns the top edge counter-clockwise seen from above -> west). They are geometric
 * identities, not measurements from a real device.
 */
class PoseMathTest {
    private val t = MonotonicElapsedNanos(123L)
    private val s = Math.sqrt(0.5)

    private fun pose(vararg v: Double) = PoseMath.fromRotationVector(v, t, accuracy = 3)

    private fun available(vararg v: Double): DeviceOrientationState.Available {
        val p = pose(*v)
        assertTrue("expected Available but was $p", p is DeviceOrientationState.Available)
        return p as DeviceOrientationState.Available
    }

    @Test
    fun `identity rotation points the top edge at magnetic north lying flat`() {
        val p = available(0.0, 0.0, 0.0, 1.0)
        assertEquals(0.0, p.headingDeg, 1e-9)
        assertEquals(0.0, p.tiltDeg, 1e-9)
        assertEquals(HeadingReference.MAGNETIC_NORTH, p.reference)
        assertEquals(3, p.accuracy)
        assertEquals(t, p.sensorTime)
    }

    @Test
    fun `quarter turns about the vertical axis give the cardinal headings`() {
        assertEquals(270.0, available(0.0, 0.0, s, s).headingDeg, 1e-9)
        assertEquals(90.0, available(0.0, 0.0, -s, s).headingDeg, 1e-9)
        assertEquals(180.0, available(0.0, 0.0, 1.0, 0.0).headingDeg, 1e-9)
    }

    @Test
    fun `three-component vector reconstructs w and matches the four-component result`() {
        assertEquals(available(0.0, 0.0, s, s).headingDeg, available(0.0, 0.0, s).headingDeg, 1e-9)
    }

    @Test
    fun `negative w is the same rotation with signs flipped`() {
        assertEquals(270.0, available(0.0, 0.0, -s, -s).headingDeg, 1e-9)
    }

    @Test
    fun `moderate tilt keeps a heading and reports the tilt`() {
        val half = Math.toRadians(15.0)
        val p = available(Math.sin(half), 0.0, 0.0, Math.cos(half))
        assertEquals(30.0, p.tiltDeg, 1e-6)
        assertEquals(0.0, p.headingDeg, 1e-6)
    }

    @Test
    fun `upright device has no defined top-edge heading`() {
        val p = pose(s, 0.0, 0.0, s)
        p as DeviceOrientationState.HeadingUndefined
        assertEquals(90.0, p.tiltDeg, 1e-6)
    }

    @Test
    fun `face-down device has no defined heading`() {
        val p = pose(1.0, 0.0, 0.0, 0.0)
        p as DeviceOrientationState.HeadingUndefined
        assertEquals(180.0, p.tiltDeg, 1e-6)
    }

    @Test
    fun `malformed vectors are Unavailable not zero headings`() {
        assertTrue(pose(0.0, 0.0) is DeviceOrientationState.Unavailable)
        assertTrue(pose(Double.NaN, 0.0, 0.0, 1.0) is DeviceOrientationState.Unavailable)
        assertTrue(pose(0.0, Double.POSITIVE_INFINITY, 0.0) is DeviceOrientationState.Unavailable)
        assertTrue(pose(1.0, 1.0, 0.0) is DeviceOrientationState.Unavailable)
    }

    @Test
    fun `declination converts magnetic to true and wraps`() {
        val magnetic = available(0.0, 0.0, 0.0, 1.0).copy(headingDeg = 350.0)
        val trueNorth = PoseMath.toTrueNorth(magnetic, 20.0)
        assertEquals(10.0, trueNorth.headingDeg, 1e-9)
        assertEquals(HeadingReference.TRUE_NORTH, trueNorth.reference)
        assertEquals(340.0, PoseMath.toTrueNorth(magnetic, -10.0).headingDeg, 1e-9)
    }

    @Test
    fun `missing or non-finite declination leaves the heading magnetic`() {
        val magnetic = available(0.0, 0.0, 0.0, 1.0)
        assertEquals(magnetic, PoseMath.toTrueNorth(magnetic, null))
        assertEquals(magnetic, PoseMath.toTrueNorth(magnetic, Double.NaN))
    }

    @Test
    fun `an already true-north heading is not corrected twice`() {
        val trueAlready = available(0.0, 0.0, 0.0, 1.0).copy(reference = HeadingReference.TRUE_NORTH, headingDeg = 40.0)
        assertEquals(trueAlready, PoseMath.toTrueNorth(trueAlready, 25.0))
    }
}
