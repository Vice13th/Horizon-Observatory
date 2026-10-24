package horizon.observatory.live

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyDomeMathTest {
    private val topDown = Math.PI / 2

    @Test fun topDownKeepsSameBearingAs2dPlot() {
        val p = SkyDomeMath.project(130.0, 30.0, 0.0, topDown)!!
        val flat = SkyProjection.project(130.0, 30.0, SkyOrientationMode.NorthUp)!!
        assertEquals(kotlin.math.atan2(flat.x, flat.y), kotlin.math.atan2(p.x, p.y), 1e-9)
    }

    @Test fun zenithIsCentreWhenTopDown() {
        val p = SkyDomeMath.project(0.0, 90.0, 0.3, topDown)!!
        assertTrue(abs(p.x) < 1e-9 && abs(p.y) < 1e-9)
    }

    @Test fun northIsFartherThanSouthWhenPitched() {
        val n = SkyDomeMath.project(0.0, 0.0, 0.0, 0.6)!!
        val s = SkyDomeMath.project(180.0, 0.0, 0.0, 0.6)!!
        assertTrue(n.depth > s.depth && n.y > s.y)
    }

    @Test fun nonFiniteReturnsNull() {
        assertNull(SkyDomeMath.project(Double.NaN, 10.0, 0.0, 0.5))
    }
}
