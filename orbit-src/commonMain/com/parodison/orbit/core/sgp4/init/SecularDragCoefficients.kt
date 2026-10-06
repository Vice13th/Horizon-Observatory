package com.parodison.orbit.core.sgp4.init

/** Atmospheric drag coefficients and their associated periodics, precomputed once. */
internal data class SecularDragCoefficients(
    val isSimplifiedDrag: Boolean,
    val eta: Double,
    val cc1: Double,
    val cc4: Double,
    val cc5: Double,
    val d2: Double,
    val d3: Double,
    val d4: Double,
    val t2cof: Double,
    val t3cof: Double,
    val t4cof: Double,
    val t5cof: Double,
    val omgcof: Double,
    val xmcof: Double,
    val nodecf: Double,
    val delmo: Double,
    val sinmao: Double,
    val x7thm1: Double,
    val x1mth2: Double,
    val xlcof: Double,
    val aycof: Double,
)
