package horizon.observatory.astronomy.prediction

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassFinderTest {
    private val period = 12 * 3_600_000.0
    private val peak = 60.0
    private fun el(t: Long) = AzEl(0.0, peak * sin(2 * PI * t / period))

    @Test fun findsRiseSetAndCulmination() = runBlocking {
        val p = PassFinder.find(0L, 13 * 3_600_000L, 10.0, at = { el(it) }).first()
        val riseExpected = (asin(10.0 / peak) / (2 * PI) * period).toLong()
        assertTrue(abs(p.riseMs!! - riseExpected) <= 1_500)
        assertTrue(abs(p.culminationMs - (period / 4).toLong()) <= 5_000)
        assertEquals(peak, p.maxElDeg, 1e-3)
        assertEquals(((period / 2).toLong() - riseExpected).toDouble(), p.setMs!!.toDouble(), 1_500.0)
    }

    @Test fun windowStartingMidPassHasUnknownRise() = runBlocking {
        assertNull(PassFinder.find(3_600_000L, 6 * 3_600_000L, 10.0, at = { el(it) }).first().riseMs)
    }

    @Test fun passRunningPastWindowEndHasUnknownSet() = runBlocking {
        assertNull(PassFinder.find(0L, 2 * 3_600_000L, 10.0, at = { el(it) }).first().setMs)
    }

    @Test fun noPassWhenNeverAboveMask() = runBlocking {
        assertTrue(PassFinder.find(0L, 13 * 3_600_000L, 80.0, at = { el(it) }).isEmpty())
    }

    @Test fun missingPredictionNeverStartsAPass() = runBlocking {
        assertTrue(PassFinder.find(0L, 13 * 3_600_000L, 10.0, at = { null }).isEmpty())
    }
}
