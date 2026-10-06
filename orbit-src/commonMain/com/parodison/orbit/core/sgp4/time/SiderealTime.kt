package com.parodison.orbit.core.sgp4.time

import com.parodison.orbit.core.sgp4.constants.WGS72Constants.TWO_PI
import kotlin.math.PI

/** Greenwich sidereal time (radians) for a given Julian date. */
internal fun greenwichSiderealTime(julianDate: Double): Double {
    val centuriesSinceJ2000 = (julianDate - 2451545.0) / 36525.0

    val seconds = -6.2e-6 * centuriesSinceJ2000 * centuriesSinceJ2000 * centuriesSinceJ2000 +
        0.093104 * centuriesSinceJ2000 * centuriesSinceJ2000 +
        (876600.0 * 3600.0 + 8640184.812866) * centuriesSinceJ2000 +
        67310.54841

    val degreesToRadians = PI / 180.0
    var radians = (seconds * degreesToRadians / 240.0).mod(TWO_PI)
    if (radians < 0.0) radians += TWO_PI
    return radians
}
