package com.parodison.orbit.core.sgp4.init

/**
 * Orbital regime by period. Deep-space (SDP4, period >= 225 min) adds lunisolar
 * perturbations and 12h/24h geopotential resonance on top of the near-earth model.
 */
internal enum class OrbitRegime {
    NEAR_EARTH,
    DEEP_SPACE,
}
