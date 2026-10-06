package com.parodison.orbit.core.sgp4.model

/** Sub-satellite point: lat/lon/altitude of the satellite itself, unrelated to any observer. */
data class GeodeticCoordinates(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val altitudeKm: Double,
)
