package horizon.observatory.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobeMathTest {
    @Test fun centreMapsToOriginFacingViewer() {
        val p = GlobeMath.project(50.0, 8.0, 50.0, 8.0)!!
        assertEquals(0.0, p.x, 1e-12); assertEquals(0.0, p.y, 1e-12); assertEquals(1.0, p.depth, 1e-12)
    }
    @Test fun antipodeIsBehind() {
        val p = GlobeMath.project(-50.0, -172.0, 50.0, 8.0)!!
        assertEquals(-1.0, p.depth, 1e-12)
    }
    @Test fun northPoleIsUpAndEastIsRight() {
        val n = GlobeMath.project(90.0, 0.0, 0.0, 0.0)!!
        assertEquals(1.0, n.y, 1e-12)
        val e = GlobeMath.project(0.0, 30.0, 0.0, 0.0)!!
        assertTrue(e.x > 0)
    }
    @Test fun nonFiniteIsNull() { assertNull(GlobeMath.project(Double.NaN, 0.0, 0.0, 0.0)) }
}
