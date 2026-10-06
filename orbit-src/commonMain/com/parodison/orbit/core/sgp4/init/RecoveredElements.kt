package com.parodison.orbit.core.sgp4.init

/** Elementos recuperados a partir del movimiento medio "de Kozai" del TLE (ver [OrbitInitializer]). */
internal data class RecoveredElements(
    val meanMotionRadPerMin: Double, // n0'' (no_unkozai)
    val semiMajorAxisEarthRadii: Double, // a0'' (ao), en radios terrestres
    val cosInclination: Double,
    val cosInclinationSquared: Double,
    val sinInclination: Double,
    val oneMinusEccentricitySquared: Double,
    val beta: Double, // sqrt(1 - e^2)
    val con41: Double,
    val con42: Double,
    val perigeeRadiusEarthRadii: Double,
    val perigeeAltitudeKm: Double,
    val semiLatusRectumSquared: Double, // posq
    val greenwichSiderealTimeAtEpoch: Double,
)
