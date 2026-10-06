package com.parodison.orbit.core.satellite

import com.parodison.orbit.core.sgp4.constants.WGS72Constants.EARTH_RADIUS_KM
import com.parodison.orbit.core.sgp4.model.GeodeticCoordinates
import com.parodison.orbit.core.sgp4.model.PositionVelocity
import com.parodison.orbit.core.sgp4.model.Vector3
import com.parodison.orbit.core.sgp4.time.greenwichSiderealTime
import com.parodison.orbit.core.sgp4.time.toJulianDate
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Instant

/** Location of an observer on the Earth's surface (ground station). */
data class ObserverCoordinates(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val altitudeKm: Double,
)

/** Result of RAZEL: how the satellite looks from an [ObserverCoordinates] at a given instant. */
data class LookAngles(
    val azimuthDeg: Double,
    val elevationDeg: Double,
    val rangeKm: Double,
    val rangeRateKmPerSec: Double,
)

/** Sub-satellite point (lat/lon/altitude) at a given instant — doesn't depend on any observer. */
fun subPointOf(satellite: PositionVelocity, at: Instant): GeodeticCoordinates {
    val gst = greenwichSiderealTime(at.toJulianDate())
    val (satelliteEcefPosition, _) = temeToEcef(satellite.position, satellite.velocity, gst)
    return ecefToGeodetic(satelliteEcefPosition)
}

/**
 * Positional astronomy: transforms a TEME state vector (the one returned by
 * [com.parodison.orbit.core.sgp4.SGP4Engine.propagate]) into topocentric horizontal
 * coordinates (azimuth/elevation/range) as seen from an observer on Earth — Vallado's RAZEL
 * algorithm ("Fundamentals of Astrodynamics and Applications").
 *
 * This is a separate theory from SGP4 (positional astronomy / geodesy, not astrodynamics):
 * it only needs a position and velocity vector, regardless of which propagator produced it.
 */
class PositionalAstronomy(private val groundStation: ObserverCoordinates) {

    private val observerEcef = geodeticToEcef(groundStation)

    fun computeLookAngles(satellite: PositionVelocity, at: Instant): LookAngles {
        val gst = greenwichSiderealTime(at.toJulianDate())
        val (satelliteEcefPosition, satelliteEcefVelocity) = temeToEcef(satellite.position, satellite.velocity, gst)

        val rangeVectorEcef = Vector3(
            x = satelliteEcefPosition.x - observerEcef.x,
            y = satelliteEcefPosition.y - observerEcef.y,
            z = satelliteEcefPosition.z - observerEcef.z,
        )
        val range = sqrt(
            rangeVectorEcef.x * rangeVectorEcef.x +
                rangeVectorEcef.y * rangeVectorEcef.y +
                rangeVectorEcef.z * rangeVectorEcef.z,
        )

        // The observer is fixed in ECEF (zero velocity there), so the relative velocity is
        // directly the satellite's; the dot product is invariant under rotations, so there's
        // no need to convert velocity to SEZ for the range rate.
        val rangeRate = (
            rangeVectorEcef.x * satelliteEcefVelocity.x +
                rangeVectorEcef.y * satelliteEcefVelocity.y +
                rangeVectorEcef.z * satelliteEcefVelocity.z
            ) / range

        val latitudeRad = groundStation.latitudeDeg.toRadians()
        val longitudeRad = groundStation.longitudeDeg.toRadians()
        val sez = ecefToSez(rangeVectorEcef, latitudeRad, longitudeRad)

        val elevationRad = asin((sez.z / range).coerceIn(-1.0, 1.0))
        var azimuthRad = atan2(sez.y, -sez.x)
        if (azimuthRad < 0.0) azimuthRad += 2.0 * PI

        return LookAngles(
            azimuthDeg = azimuthRad.toDegrees(),
            elevationDeg = elevationRad.toDegrees(),
            rangeKm = range,
            rangeRateKmPerSec = rangeRate,
        )
    }

}

/** Position (and velocity, correcting for Earth's rotation) TEME -> ECEF/PEF. */
private fun temeToEcef(position: Vector3, velocity: Vector3, gst: Double): Pair<Vector3, Vector3> {
    val cosGst = cos(gst)
    val sinGst = sin(gst)

    val x = position.x * cosGst + position.y * sinGst
    val y = -position.x * sinGst + position.y * cosGst
    val z = position.z

    val rotatedVx = velocity.x * cosGst + velocity.y * sinGst
    val rotatedVy = -velocity.x * sinGst + velocity.y * cosGst

    val vx = rotatedVx + EARTH_ROTATION_RATE_RAD_PER_SEC * y
    val vy = rotatedVy - EARTH_ROTATION_RATE_RAD_PER_SEC * x
    val vz = velocity.z

    return Vector3(x, y, z) to Vector3(vx, vy, vz)
}

/** Lat/lon/altitude (WGS-72, ellipsoidal) -> ECEF, in km. */
private fun geodeticToEcef(station: ObserverCoordinates): Vector3 {
    val latitudeRad = station.latitudeDeg.toRadians()
    val longitudeRad = station.longitudeDeg.toRadians()
    val sinLat = sin(latitudeRad)
    val cosLat = cos(latitudeRad)

    val eccentricitySquared = 2.0 * EARTH_FLATTENING - EARTH_FLATTENING * EARTH_FLATTENING
    val primeVerticalRadius = EARTH_RADIUS_KM / sqrt(1.0 - eccentricitySquared * sinLat * sinLat)

    val x = (primeVerticalRadius + station.altitudeKm) * cosLat * cos(longitudeRad)
    val y = (primeVerticalRadius + station.altitudeKm) * cosLat * sin(longitudeRad)
    val z = (primeVerticalRadius * (1.0 - eccentricitySquared) + station.altitudeKm) * sinLat

    return Vector3(x, y, z)
}

/**
 * ECEF (km) -> lat/lon/altitude (WGS-72, ellipsoidal), inverse of [geodeticToEcef] (iterative,
 * Bowring).
 */
private fun ecefToGeodetic(position: Vector3): GeodeticCoordinates {
    val longitudeRad = atan2(position.y, position.x)
    val eccentricitySquared = 2.0 * EARTH_FLATTENING - EARTH_FLATTENING * EARTH_FLATTENING
    val equatorialDistance = sqrt(position.x * position.x + position.y * position.y)

    var latitudeRad = atan2(position.z, equatorialDistance * (1.0 - eccentricitySquared))
    var altitudeKm = 0.0

    repeat(GEODETIC_ITERATIONS) {
        val primeVerticalRadius = EARTH_RADIUS_KM / sqrt(1.0 - eccentricitySquared * sin(latitudeRad) * sin(latitudeRad))
        altitudeKm = equatorialDistance / cos(latitudeRad) - primeVerticalRadius
        latitudeRad = atan2(
            position.z,
            equatorialDistance * (1.0 - eccentricitySquared * primeVerticalRadius / (primeVerticalRadius + altitudeKm)),
        )
    }

    return GeodeticCoordinates(
        latitudeDeg = latitudeRad.toDegrees(),
        longitudeDeg = longitudeRad.toDegrees(),
        altitudeKm = altitudeKm,
    )
}

/** Rotates an ECEF vector into the observer's local topocentric South-East-Zenith (SEZ) frame. */
private fun ecefToSez(vector: Vector3, latitudeRad: Double, longitudeRad: Double): Vector3 {
    val sinLat = sin(latitudeRad)
    val cosLat = cos(latitudeRad)
    val sinLon = sin(longitudeRad)
    val cosLon = cos(longitudeRad)

    val south = sinLat * cosLon * vector.x + sinLat * sinLon * vector.y - cosLat * vector.z
    val east = -sinLon * vector.x + cosLon * vector.y
    val zenith = cosLat * cosLon * vector.x + cosLat * sinLon * vector.y + sinLat * vector.z

    return Vector3(south, east, zenith)
}

internal fun Double.toRadians(): Double = this * PI / 180.0
internal fun Double.toDegrees(): Double = this * 180.0 / PI

// WGS-72 flattening (the same reference ellipsoid used by SGP4Engine).
private const val EARTH_FLATTENING = 1.0 / 298.26
// Earth's sidereal rotation rate, rad/s.
private const val EARTH_ROTATION_RATE_RAD_PER_SEC = 7.292115e-5
// Iterations for the ECEF -> geodetic conversion; converges in just a few iterations.
private const val GEODETIC_ITERATIONS = 5
