package horizon.observatory.live

import kotlin.math.cos
import kotlin.math.sin

/** depth > 0 ⇒ point is on the hemisphere facing the viewer. x right, y up, unit-sphere units. */
data class GlobePoint(val x: Double, val y: Double, val depth: Double)

/**
 * Orthographic globe projection centred on (centerLat, centerLon).
 * v = (cosφ cosλ, cosφ sinλ, sinφ); R_z(−λc) then R_y(φc): sx = y₁, sy = z₂, depth = x₂.
 */
object GlobeMath {
    fun project(latDeg: Double, lonDeg: Double, centerLatDeg: Double, centerLonDeg: Double): GlobePoint? {
        if (!latDeg.isFinite() || !lonDeg.isFinite()) return null
        val la = Math.toRadians(latDeg); val lo = Math.toRadians(lonDeg)
        val pc = Math.toRadians(centerLatDeg); val lc = Math.toRadians(centerLonDeg)
        val x = cos(la) * cos(lo); val y = cos(la) * sin(lo); val z = sin(la)
        val x1 = x * cos(lc) + y * sin(lc)
        val y1 = -x * sin(lc) + y * cos(lc)
        val x2 = x1 * cos(pc) + z * sin(pc)
        val z2 = -x1 * sin(pc) + z * cos(pc)
        return GlobePoint(y1, z2, x2)
    }
}
