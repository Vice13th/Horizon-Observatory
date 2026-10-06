package horizon.observatory.astronomy

/** Mean (Keplerian) orbital elements at a reference epoch. Angles in radians, distances in km. */
data class OrbitalElements(
    val catalogId: String,
    val epochUtcMillis: Long,
    val semiMajorAxisKm: Double,
    val eccentricity: Double,
    val inclinationRad: Double,
    val raanRad: Double,
    val argOfPerigeeRad: Double,
    val meanAnomalyRad: Double
)

/** Position/velocity in the Earth-Centered Inertial frame. km, km/s. */
data class EciState(val xKm: Double, val yKm: Double, val zKm: Double, val vxKmS: Double, val vyKmS: Double, val vzKmS: Double, val atUtcMillis: Long)

/** Position/velocity in the Earth-Centered Earth-Fixed frame. km, km/s. */
data class EcefState(val xKm: Double, val yKm: Double, val zKm: Double, val vxKmS: Double, val vyKmS: Double, val vzKmS: Double)

/** Observer-relative topocentric result. Degrees for angles, km for range, km/s for rangeRate. */
data class TopocentricState(
    val azimuthDeg: Double,
    val elevationDeg: Double,
    val rangeKm: Double,
    val rangeRateKmS: Double
)
