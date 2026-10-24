package horizon.observatory.astronomy.geo

import horizon.observatory.astronomy.CoordinateTransforms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeodeticTest {
    private fun roundTrip(lat: Double, lon: Double, altKm: Double) {
        val e = CoordinateTransforms.geodeticToEcef(Math.toRadians(lat), Math.toRadians(lon), altKm)
        val g = Geodetic.fromEcef(e.xKm, e.yKm, e.zKm)!!
        assertEquals(lat, g.latDeg, 1e-8)
        assertEquals(lon, g.lonDeg, 1e-8)
        assertEquals(altKm, g.altKm, 1e-6)
    }
    @Test fun roundTrips() {
        roundTrip(50.11, 8.68, 0.1)
        roundTrip(-33.9, 151.2, 0.0)
        roundTrip(55.0, -120.0, 20200.0)
        roundTrip(-80.0, 170.0, 550.0)
    }
    @Test fun poleIsSafe() {
        val g = Geodetic.fromEcef(0.0, 0.0, 6356.752314245)!!
        assertEquals(90.0, g.latDeg, 1e-9)
        assertEquals(0.0, g.altKm, 1e-6)
    }
    @Test fun nonFiniteIsNull() { assertNull(Geodetic.fromEcef(Double.NaN, 0.0, 0.0)) }
}
