package com.parodison.orbit.core.sgp4.constants

import kotlin.math.PI
import kotlin.math.sqrt

internal object WGS72Constants {
    const val MU = 398600.8 // km^3/s^2
    const val EARTH_RADIUS_KM = 6378.135
    const val J2 = 0.001082616
    const val J3 = -0.00000253881
    const val J4 = -0.00000165597
    const val J3OJ2 = J3 / J2

    val XKE = 60.0 / sqrt(EARTH_RADIUS_KM * EARTH_RADIUS_KM * EARTH_RADIUS_KM / MU)

    const val X2O3 = 2.0 / 3.0
    val TWO_PI = 2.0 * PI
    const val DEEP_SPACE_PERIOD_THRESHOLD_MIN = 225.0
}
