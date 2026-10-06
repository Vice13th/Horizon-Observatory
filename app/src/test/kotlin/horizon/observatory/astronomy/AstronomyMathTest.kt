package horizon.observatory.astronomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class AstronomyMathTest {

    @Test
    fun `geodetic equator prime meridian sea level equals WGS84 semi-major axis`() {
        val ecef = CoordinateTransforms.geodeticToEcef(0.0, 0.0, 0.0)
        assertEquals(6378.137, ecef.xKm, 0.001)
        assertEquals(0.0, ecef.yKm, 0.001)
        assertEquals(0.0, ecef.zKm, 0.001)
    }

    @Test
    fun `circular equatorial orbit at epoch has expected radius and zero z`() {
        val altKm = 550.0
        val a = 6378.137 + altKm
        val elements = OrbitalElements("TEST-1", 0L, a, 0.0, 0.0, 0.0, 0.0, 0.0)
        val eci = OrbitPropagator.propagateToEci(elements, 0L)
        val r = Math.sqrt(eci.xKm * eci.xKm + eci.yKm * eci.yKm + eci.zKm * eci.zKm)
        assertEquals(a, r, 0.01)
        assertEquals(0.0, eci.zKm, 0.001)
    }

    @Test
    fun `circular orbit after quarter period has moved 90 degrees`() {
        val a = 6378.137 + 550.0
        val elements = OrbitalElements("TEST-2", 0L, a, 0.0, 0.0, 0.0, 0.0, 0.0)
        val n = Math.sqrt(398600.4418 / (a * a * a))
        val periodMs = (2 * Math.PI / n * 1000).toLong()
        val eci = OrbitPropagator.propagateToEci(elements, periodMs / 4)
        assertTrue(abs(eci.xKm) < a * 0.01)
        assertTrue(eci.yKm > a * 0.99)
    }

    @Test
    fun `inclined orbit stays within inclination bound`() {
        val a = 6378.137 + 550.0
        val incDeg = 53.0
        val elements = OrbitalElements("TEST-3", 0L, a, 0.0, Math.toRadians(incDeg), 0.0, 0.0, 0.0)
        for (frac in listOf(0.0, 0.1, 0.25, 0.5, 0.75, 0.9)) {
            val n = Math.sqrt(398600.4418 / (a * a * a))
            val periodMs = (2 * Math.PI / n * 1000).toLong()
            val eci = OrbitPropagator.propagateToEci(elements, (periodMs * frac).toLong())
            val r = Math.sqrt(eci.xKm * eci.xKm + eci.yKm * eci.yKm + eci.zKm * eci.zKm)
            assertEquals(a, r, 0.1)
            val latAngle = Math.toDegrees(Math.asin((eci.zKm / r).coerceIn(-1.0, 1.0)))
            assertTrue(abs(latAngle) <= incDeg + 0.01)
        }
    }

    @Test
    fun `overhead satellite has elevation near 90 degrees`() {
        val obs = ObserverLocation(0.0, 0.0, 0.0)
        val altKm = 550.0
        val testUtcMillis = 0L
        val gmst = CoordinateTransforms.gmstRad(testUtcMillis)
        val ecefOverheadX = 6378.137 + altKm
        val eciX = ecefOverheadX * cos(gmst)
        val eciY = ecefOverheadX * sin(gmst)
        // The ECI fixture is expressed so that the existing ECI->ECEF rotation places it
        // exactly above the equator/prime-meridian observer at this timestamp. Its velocity is
        // irrelevant to the angular/range assertion, so keep it zero.
        val eci = EciState(eciX, eciY, 0.0, 0.0, 0.0, 0.0, testUtcMillis)
        val topo = TopocentricCalculator.compute(obs, eci)
        assertTrue(topo.elevationDeg > 85.0)
        assertEquals(altKm, topo.rangeKm, 1.0)
    }

    @Test
    fun `below horizon satellite is not visible`() {
        val obs = ObserverLocation(0.0, 0.0, 0.0)
        // Satellite on the opposite side of Earth (antipodal-ish), should be below horizon.
        val eci = EciState(-(6378.137 + 550.0), 0.0, 0.0, 0.0, 0.0, 0.0, 0L)
        val topo = TopocentricCalculator.compute(obs, eci)
        assertTrue(!VisibilityCalculator.isAboveHorizon(topo))
    }
}
