package horizon.observatory.live

import kotlin.math.cos
import kotlin.math.sin

/** Screen-space result of projecting a sky direction onto the 3D dome. depth: +1 far .. -1 near. */
data class DomePoint(val x: Double, val y: Double, val depth: Double, val scale: Double)

/**
 * Pure presentation math for the 3D sky dome. Direction (ENU unit vector):
 * E = cos(el)·sin(b), N = cos(el)·cos(b), U = sin(el), b = azimuth (− heading when heading-up).
 * Camera: yaw ψ about Up, pitch φ above horizon (φ = 90° ⇒ top-down: same bearings as the 2D plot, orthographic radius cos(el) instead of (90°−el)/90°).
 * sx = x₁, sy = U·cosφ + y₁·sinφ, depth = y₁·cosφ − U·sinφ, perspective scale = 1/(1 + 0.3·depth).
 * Non-finite input returns null; no position is ever substituted.
 */
object SkyDomeMath {
    fun project(bearingDeg: Double, elevationDeg: Double, yawRad: Double, pitchRad: Double): DomePoint? {
        if (!bearingDeg.isFinite() || !elevationDeg.isFinite()) return null
        val el = Math.toRadians(elevationDeg.coerceIn(-90.0, 90.0))
        val b = Math.toRadians(bearingDeg)
        val e = cos(el) * sin(b)
        val n = cos(el) * cos(b)
        val u = sin(el)
        val x1 = e * cos(yawRad) - n * sin(yawRad)
        val y1 = e * sin(yawRad) + n * cos(yawRad)
        val sy = u * cos(pitchRad) + y1 * sin(pitchRad)
        val depth = y1 * cos(pitchRad) - u * sin(pitchRad)
        return DomePoint(x1, sy, depth, 1.0 / (1.0 + 0.3 * depth))
    }
}
