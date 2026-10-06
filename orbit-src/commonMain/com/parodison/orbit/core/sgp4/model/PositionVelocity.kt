package com.parodison.orbit.core.sgp4.model

/** Position (km) and velocity (km/s) in the TEME (True Equator, Mean Equinox) frame. */
data class PositionVelocity(
    val position: Vector3,
    val velocity: Vector3,
)
