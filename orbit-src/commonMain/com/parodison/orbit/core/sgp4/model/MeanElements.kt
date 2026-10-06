package com.parodison.orbit.core.sgp4.model

import kotlin.time.Instant

/** Elementos orbitales medios ya convertidos a las unidades internas de SGP4 (radianes, rad/min). */
internal data class MeanElements(
    val epoch: Instant,
    val meanMotionRadPerMin: Double,
    val eccentricity: Double,
    val inclinationRad: Double,
    val raanRad: Double,
    val argPerigeeRad: Double,
    val meanAnomalyRad: Double,
    val bstar: Double,
)
