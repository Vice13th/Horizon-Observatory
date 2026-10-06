package horizon.observatory.live

import horizon.observatory.astronomy.orientation.DeviceOrientationState
import horizon.observatory.astronomy.orientation.HeadingReference
import horizon.observatory.astronomy.time.MonotonicElapsedNanos
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Pure (Android-free) device-pose math.
 *
 * Input: an Android TYPE_ROTATION_VECTOR sample (x*sin(a/2), y*sin(a/2), z*sin(a/2), [cos(a/2)]).
 * Its frame is East-North-Up with north = MAGNETIC north, so every heading produced here is
 * [HeadingReference.MAGNETIC_NORTH] until [toTrueNorth] applies an explicit declination.
 *
 * R is the device->world rotation matrix (same as SensorManager.getRotationMatrixFromVector).
 * The world direction of the device top edge (+Y device axis) is (R[1], R[4], R[7]); the heading is
 * its horizontal bearing atan2(east, north). The screen normal (+Z device) has world-up component
 * R[8]; tilt = acos(R[8]).
 */
object PoseMath {
    /** Policy (not physics): beyond this tilt the top-edge heading is not offered for the sky plot. */
    const val DEFAULT_MAX_TILT_DEG: Double = 60.0

    fun fromRotationVector(
        values: DoubleArray,
        sensorTime: MonotonicElapsedNanos,
        accuracy: Int?,
        maxTiltDeg: Double = DEFAULT_MAX_TILT_DEG
    ): DeviceOrientationState {
        if (values.size < 3) return DeviceOrientationState.Unavailable("rotation vector has ${values.size} components")
        val x = values[0]
        val y = values[1]
        val z = values[2]
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) {
            return DeviceOrientationState.Unavailable("rotation vector contains a non-finite component")
        }
        // Android: values[3] (cos(a/2)) is optional and, when present, >= 0. If a device reports a
        // negative w it is the same rotation with every sign flipped, so flip x, y, z, w back.
        val hasW = values.size >= 4 && values[3].isFinite()
        val sign = if (hasW && values[3] < 0.0) -1.0 else 1.0
        val qx = sign * x
        val qy = sign * y
        val qz = sign * z
        val w: Double = if (hasW) {
            sign * values[3]
        } else {
            val arg = 1.0 - x * x - y * y - z * z
            if (arg < -1.0e-6) return DeviceOrientationState.Unavailable("rotation vector norm exceeds 1")
            sqrt(arg.coerceAtLeast(0.0))
        }
        val norm = sqrt(qx * qx + qy * qy + qz * qz + w * w)
        if (norm < 1.0e-9) return DeviceOrientationState.Unavailable("degenerate rotation vector")
        val nx = qx / norm
        val ny = qy / norm
        val nz = qz / norm
        val nw = w / norm

        val r1 = 2.0 * (nx * ny - nz * nw)
        val r4 = 1.0 - 2.0 * (nx * nx + nz * nz)
        val r8 = 1.0 - 2.0 * (nx * nx + ny * ny)

        val tiltDeg = Math.toDegrees(acos(r8.coerceIn(-1.0, 1.0)))
        if (tiltDeg > maxTiltDeg) {
            return DeviceOrientationState.HeadingUndefined(
                reason = "device tilt ${"%.0f".format(tiltDeg)} deg exceeds ${"%.0f".format(maxTiltDeg)} deg; top-edge heading not defined",
                tiltDeg = tiltDeg,
                sensorTime = sensorTime,
                accuracy = accuracy
            )
        }
        var headingRad = atan2(r1, r4)
        if (headingRad < 0.0) headingRad += 2.0 * PI
        var headingDeg = Math.toDegrees(headingRad)
        if (headingDeg >= 360.0) headingDeg -= 360.0
        return DeviceOrientationState.Available(
            headingDeg = headingDeg,
            tiltDeg = tiltDeg,
            reference = HeadingReference.MAGNETIC_NORTH,
            sensorTime = sensorTime,
            accuracy = accuracy
        )
    }

    /**
     * Applies a magnetic declination (degrees, positive east: true = magnetic + declination).
     * Returns the input unchanged when the declination is null (reference stays MAGNETIC_NORTH) or
     * the heading is already true-north.
     */
    fun toTrueNorth(pose: DeviceOrientationState.Available, declinationDeg: Double?): DeviceOrientationState.Available {
        if (pose.reference == HeadingReference.TRUE_NORTH) return pose
        if (declinationDeg == null || !declinationDeg.isFinite()) return pose
        var h = (pose.headingDeg + declinationDeg) % 360.0
        if (h < 0.0) h += 360.0
        return pose.copy(headingDeg = h, reference = HeadingReference.TRUE_NORTH)
    }
}
