package horizon.observatory.astronomy

import kotlin.math.*

/** ECI<->ECEF and geodetic<->ECEF. GMST via IAU 1982 polynomial (standard approximation). */
object CoordinateTransforms {
    private const val WGS84_A_KM = 6378.137
    private const val WGS84_F = 1.0 / 298.257223563
    private const val EARTH_ROT_RAD_S = 7.2921150e-5

    fun geodeticToEcef(latRad: Double, lonRad: Double, altKm: Double): EcefState {
        val e2 = 2 * WGS84_F - WGS84_F * WGS84_F
        val nPhi = WGS84_A_KM / sqrt(1 - e2 * sin(latRad).pow(2))
        val x = (nPhi + altKm) * cos(latRad) * cos(lonRad)
        val y = (nPhi + altKm) * cos(latRad) * sin(lonRad)
        val z = (nPhi * (1 - e2) + altKm) * sin(latRad)
        return EcefState(x, y, z, 0.0, 0.0, 0.0)
    }

    fun gmstRad(utcMillis: Long): Double {
        val jd = utcMillis / 86400000.0 + 2440587.5
        val t = (jd - 2451545.0) / 36525.0
        var deg = 280.46061837 + 360.98564736629 * (jd - 2451545.0) + 0.000387933 * t * t - t * t * t / 38710000.0
        deg %= 360.0
        if (deg < 0) deg += 360.0
        return Math.toRadians(deg)
    }

    fun eciToEcef(eci: EciState): EcefState {
        val g = gmstRad(eci.atUtcMillis)
        val x = eci.xKm * cos(g) + eci.yKm * sin(g)
        val y = -eci.xKm * sin(g) + eci.yKm * cos(g)
        val z = eci.zKm
        var vx = eci.vxKmS * cos(g) + eci.vyKmS * sin(g)
        var vy = -eci.vxKmS * sin(g) + eci.vyKmS * cos(g)
        val vz = eci.vzKmS
        // Earth-rotation correction: v_ecef = R(g)*v_eci - omega x r_ecef
        vx += EARTH_ROT_RAD_S * y
        vy -= EARTH_ROT_RAD_S * x
        return EcefState(x, y, z, vx, vy, vz)
    }

    fun ecefToEnu(observerEcef: EcefState, targetEcef: EcefState, obsLatRad: Double, obsLonRad: Double): DoubleArray {
        val dx = targetEcef.xKm - observerEcef.xKm
        val dy = targetEcef.yKm - observerEcef.yKm
        val dz = targetEcef.zKm - observerEcef.zKm
        val e = -sin(obsLonRad) * dx + cos(obsLonRad) * dy
        val n = -sin(obsLatRad) * cos(obsLonRad) * dx - sin(obsLatRad) * sin(obsLonRad) * dy + cos(obsLatRad) * dz
        val u = cos(obsLatRad) * cos(obsLonRad) * dx + cos(obsLatRad) * sin(obsLonRad) * dy + sin(obsLatRad) * dz
        return doubleArrayOf(e, n, u)
    }
}
