package com.parodison.orbit.core.sgp4.propagation

import kotlin.math.cos
import kotlin.math.sin

/** Vectores unitarios que orientan el plano orbital en el espacio (para rotar a TEME). */
internal data class OrientationVectors(
    val ux: Double, val uy: Double, val uz: Double,
    val vx: Double, val vy: Double, val vz: Double,
)

internal fun computeOrientationVectors(shortPeriod: ShortPeriodState): OrientationVectors {
    val sinsu = sin(shortPeriod.argLatitude)
    val cossu = cos(shortPeriod.argLatitude)
    val snod = sin(shortPeriod.node)
    val cnod = cos(shortPeriod.node)
    val sini = sin(shortPeriod.inclination)
    val cosi = cos(shortPeriod.inclination)

    val xmx = -snod * cosi
    val xmy = cnod * cosi

    return OrientationVectors(
        ux = xmx * sinsu + cnod * cossu,
        uy = xmy * sinsu + snod * cossu,
        uz = sini * sinsu,
        vx = xmx * cossu - cnod * sinsu,
        vy = xmy * cossu - snod * sinsu,
        vz = sini * cossu,
    )
}
