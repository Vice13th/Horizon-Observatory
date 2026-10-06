package com.parodison.orbit.core.sgp4.model

import kotlin.math.sqrt

data class Vector3(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    operator fun minus(other: Vector3): Vector3 = Vector3(x - other.x, y - other.y, z - other.z)

    fun dot(other: Vector3): Double = x * other.x + y * other.y + z * other.z

    fun scale(factor: Double): Vector3 = Vector3(x * factor, y * factor, z * factor)

    fun magnitude(): Double = sqrt(x * x + y * y + z * z)

    fun normalized(): Vector3 = scale(1.0 / magnitude())
}
