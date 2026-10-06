package com.parodison.orbit.core.sgp4.init

/** Tasas seculares de deriva por achatamiento terrestre (J2/J4), en rad/min. */
internal data class SecularGravityCoefficients(
    val meanAnomalyDot: Double,
    val argPerigeeDot: Double,
    val nodeDot: Double,
)
