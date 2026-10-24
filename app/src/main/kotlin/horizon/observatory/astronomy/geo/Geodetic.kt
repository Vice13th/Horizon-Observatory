package horizon.observatory.astronomy.geo

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

data class GeodeticPoint(val latDeg: Double, val lonDeg: Double, val altKm: Double)

/**
 * WGS-84 ECEF → geodetic by fixed-point iteration of
 * φ = atan2(z, p·(1 − e²·N/(N + h))), N = a/√(1 − e² sin²φ), h = p/cosφ − N  (p = √(x²+y²)).
 * Converges to <1e-12 rad in a few iterations for any altitude incl. GNSS orbits. Pole-safe.
 * Returns null for non-finite input (nothing is substituted).
 */
object Geodetic {
    private const val A_KM = 6378.137
    private const val F = 1.0 / 298.257223563
    private val E2 = 2 * F - F * F
    private val B_KM = A_KM * (1 - F)

    fun fromEcef(xKm: Double, yKm: Double, zKm: Double): GeodeticPoint? {
        if (!xKm.isFinite() || !yKm.isFinite() || !zKm.isFinite()) return null
        val p = hypot(xKm, yKm)
        val lon = if (p < 1e-9) 0.0 else atan2(yKm, xKm)
        if (p < 1e-9) {
            return GeodeticPoint(if (zKm >= 0) 90.0 else -90.0, 0.0, abs(zKm) - B_KM)
        }
        var lat = atan2(zKm, p * (1 - E2))
        var alt = 0.0
        repeat(10) {
            val n = A_KM / sqrt(1 - E2 * sin(lat) * sin(lat))
            alt = p / cos(lat) - n
            lat = atan2(zKm, p * (1 - E2 * n / (n + alt)))
        }
        return GeodeticPoint(Math.toDegrees(lat), Math.toDegrees(lon), alt)
    }
}
