package com.parodison.orbit.core.sgp4.propagation

import kotlin.math.cos
import kotlin.math.sin

internal data class LongPeriodPeriodics(
    val axnl: Double,
    val aynl: Double,
    val meanLongitude: Double,
)

/**
 * Adds the long-period (J3) periodic corrections before solving Kepler's equation.
 *
 * [aycof]/[xlcof] are computed once at initialization for near-earth satellites, but
 * deep-space recomputes them on every propagation from the inclination corrected by the
 * lunisolar periodics — that's why they're received already resolved instead of being read
 * from a fixed [com.parodison.orbit.core.sgp4.init.SecularDragCoefficients].
 */
internal fun applyLongPeriodPeriodics(
    secularEffects: SecularEffects,
    aycof: Double,
    xlcof: Double,
): LongPeriodPeriodics {
    val axnl = secularEffects.eccentricity * cos(secularEffects.argPerigee)
    val temp = 1.0 / (secularEffects.semiMajorAxis * (1.0 - secularEffects.eccentricity * secularEffects.eccentricity))
    val aynl = secularEffects.eccentricity * sin(secularEffects.argPerigee) + temp * aycof
    val meanLongitude = secularEffects.meanAnomaly + secularEffects.argPerigee + secularEffects.node +
        temp * xlcof * axnl

    return LongPeriodPeriodics(axnl, aynl, meanLongitude)
}
