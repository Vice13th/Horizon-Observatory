package horizon.observatory.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkyDomeStyleTest {
    @Test fun missingOrNonFiniteCn0IsNull() {
        assertNull(SkyDomeStyle.cn0Fraction(null))
        assertNull(SkyDomeStyle.cn0Fraction(Double.NaN))
    }
    @Test fun cn0MapsAndClamps() {
        assertEquals(0.0, SkyDomeStyle.cn0Fraction(10.0)!!, 1e-12)
        assertEquals(0.5, SkyDomeStyle.cn0Fraction(35.0)!!, 1e-12)
        assertEquals(1.0, SkyDomeStyle.cn0Fraction(60.0)!!, 1e-12)
    }
    @Test fun labels() {
        assertEquals("G12", SkyDomeStyle.label(1, 12))
        assertEquals("E5", SkyDomeStyle.label(6, 5))
        assertEquals("?9", SkyDomeStyle.label(99, 9))
    }
}
