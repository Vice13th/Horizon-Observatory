package com.parodison.orbit.core.sgp4.propagation

/** Mean elements already updated to an instant t (minutes since epoch). */
internal data class SecularEffects(
    val semiMajorAxis: Double,
    val meanMotion: Double,
    val eccentricity: Double,
    val inclination: Double,
    val meanAnomaly: Double,
    val argPerigee: Double,
    val node: Double,
)
