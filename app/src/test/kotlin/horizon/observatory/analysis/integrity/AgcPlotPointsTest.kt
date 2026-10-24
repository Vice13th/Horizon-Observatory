package horizon.observatory.analysis.integrity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgcPlotPointsTest {
    @Test fun sortsEventsAndUsesElapsedSecondsFromFirstRetainedTimestamp() {
        val points = agcPlotPoints(
            listOf(
                AgcPlotPoint(3_500_000_000L, -7.0),
                AgcPlotPoint(1_000_000_000L, -2.0),
                AgcPlotPoint(2_250_000_000L, -4.0)
            )
        )
        assertEquals(listOf(0.0, 1.25, 2.5), points.map { it.first })
        assertEquals(listOf(-2.0, -4.0, -7.0), points.map { it.second })
    }

    @Test fun filtersInvalidLevelsAndNegativeTimestamps() {
        val points = agcPlotPoints(
            listOf(
                AgcPlotPoint(-1L, 3.0),
                AgcPlotPoint(2_000_000_000L, Double.NaN),
                AgcPlotPoint(4_000_000_000L, 8.0)
            )
        )
        assertEquals(1, points.size)
        assertEquals(0.0, points.single().first, 0.0)
        assertEquals(8.0, points.single().second, 0.0)
    }

    @Test fun emptySeriesRemainsEmpty() {
        assertTrue(agcPlotPoints(emptyList()).isEmpty())
    }
}
