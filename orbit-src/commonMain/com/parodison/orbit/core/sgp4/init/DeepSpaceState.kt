package com.parodison.orbit.core.sgp4.init

/**
 * Common lunisolar terms ("dscom" in the Vallado reference), computed once at epoch and used
 * by the deep-space periodics on every propagation.
 */
internal data class DeepSpaceCommonTerms(
    val e3: Double,
    val ee2: Double,
    val se2: Double,
    val se3: Double,
    val sgh2: Double,
    val sgh3: Double,
    val sgh4: Double,
    val sh2: Double,
    val sh3: Double,
    val si2: Double,
    val si3: Double,
    val sl2: Double,
    val sl3: Double,
    val sl4: Double,
    val xgh2: Double,
    val xgh3: Double,
    val xgh4: Double,
    val xh2: Double,
    val xh3: Double,
    val xi2: Double,
    val xi3: Double,
    val xl2: Double,
    val xl3: Double,
    val xl4: Double,
    val zmol: Double,
    val zmos: Double,
)

/**
 * Geopotential resonance state ("dsinit" in the reference), computed once at epoch and used
 * by the resonance integration on every propagation.
 *
 * [irez] = 0 no resonance, 1 synchronous resonance (~24h, geostationary),
 * 2 semi-diurnal resonance (~12h, GPS/Molniya-type).
 */
internal data class DeepSpaceResonance(
    val irez: Int,
    val d2201: Double,
    val d2211: Double,
    val d3210: Double,
    val d3222: Double,
    val d4410: Double,
    val d4422: Double,
    val d5220: Double,
    val d5232: Double,
    val d5421: Double,
    val d5433: Double,
    val dedt: Double,
    val didt: Double,
    val dmdt: Double,
    val dnodt: Double,
    val domdt: Double,
    val del1: Double,
    val del2: Double,
    val del3: Double,
    val xfact: Double,
    val xlamo: Double,
)

internal data class DeepSpaceState(
    val common: DeepSpaceCommonTerms,
    val resonance: DeepSpaceResonance,
)
