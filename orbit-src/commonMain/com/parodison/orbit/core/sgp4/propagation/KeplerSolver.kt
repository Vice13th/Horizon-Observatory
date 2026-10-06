package com.parodison.orbit.core.sgp4.propagation

import com.parodison.orbit.core.sgp4.constants.WGS72Constants.TWO_PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Solves Kepler's equation (Newton-Raphson) to get the eccentric anomaly. */
internal object KeplerSolver {
    private const val TOLERANCE = 1.0e-12
    private const val MAX_ITERATIONS = 10

    fun solveKeplerEquation(meanLongitude: Double, node: Double, axnl: Double, aynl: Double): Double {
        val u = (meanLongitude - node).mod(TWO_PI)
        var eccentricAnomaly = u
        var correction = 9999.9
        var iteration = 1

        while (abs(correction) >= TOLERANCE && iteration <= MAX_ITERATIONS) {
            val sineo1 = sin(eccentricAnomaly)
            val coseo1 = cos(eccentricAnomaly)
            val denom = 1.0 - coseo1 * axnl - sineo1 * aynl
            correction = (u - aynl * coseo1 + axnl * sineo1 - eccentricAnomaly) / denom
            if (abs(correction) >= 0.95) {
                correction = if (correction > 0.0) 0.95 else -0.95
            }
            eccentricAnomaly += correction
            iteration++
        }
        return eccentricAnomaly
    }
}
