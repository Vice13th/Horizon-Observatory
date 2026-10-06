package horizon.observatory.live

import android.hardware.GeomagneticField

/**
 * Magnetic declination for converting a magnetic-north heading to true north. Returns degrees,
 * positive east (true = magnetic + declination), or null when it cannot be determined.
 */
interface MagneticDeclinationProvider {
    fun declinationDeg(latitudeDeg: Double, longitudeDeg: Double, altitudeMeters: Double?, utcMillis: Long): Double?
}

/**
 * Android's built-in World Magnetic Model implementation. Its accuracy has not been characterized
 * here: UNVERIFIED. Altitude changes declination by far less than 0.01 deg; when absent, sea level
 * is used for this single input (a disclosed, negligible fallback, not a position value).
 */
class GeomagneticFieldDeclinationProvider : MagneticDeclinationProvider {
    override fun declinationDeg(
        latitudeDeg: Double,
        longitudeDeg: Double,
        altitudeMeters: Double?,
        utcMillis: Long
    ): Double? {
        if (!latitudeDeg.isFinite() || !longitudeDeg.isFinite()) return null
        if (latitudeDeg < -90.0 || latitudeDeg > 90.0 || longitudeDeg < -180.0 || longitudeDeg > 180.0) return null
        val alt = altitudeMeters?.takeIf { it.isFinite() } ?: 0.0
        val value = GeomagneticField(latitudeDeg.toFloat(), longitudeDeg.toFloat(), alt.toFloat(), utcMillis).declination
        return if (value.isFinite()) value.toDouble() else null
    }
}
