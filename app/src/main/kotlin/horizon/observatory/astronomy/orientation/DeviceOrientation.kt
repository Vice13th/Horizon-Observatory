package horizon.observatory.astronomy.orientation

import horizon.observatory.astronomy.time.MonotonicElapsedNanos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HeadingReference {
    /** Heading measured from magnetic north (Android rotation-vector convention). */
    MAGNETIC_NORTH,

    /** Heading after an explicit magnetic-declination correction. */
    TRUE_NORTH
}

/**
 * Real device orientation, or explicit UNAVAILABLE. There is no static-heading default: a consumer
 * that receives [Unavailable] must show orientation as unavailable and must not rotate the sky plot.
 *
 * Pose is a PRESENTATION input. It is never written into, and never alters, stored observations or
 * predicted az/el. The sensor-backed source is RotationVectorOrientationSource (live package); its
 * runtime behaviour on a device is UNVERIFIED.
 */
sealed interface DeviceOrientationState {
    data class Unavailable(val reason: String) : DeviceOrientationState

    /**
     * A usable heading. [headingDeg] is the compass bearing of the device's top edge projected on
     * the horizontal plane, in [0, 360). [tiltDeg] is the angle between the screen normal and
     * vertical (0 = lying flat, screen up; 90 = upright; 180 = face down).
     */
    data class Available(
        val headingDeg: Double,
        val tiltDeg: Double,
        val reference: HeadingReference,
        /** Sensor event time in the monotonic domain; never compared with UTC values. */
        val sensorTime: MonotonicElapsedNanos,
        val accuracy: Int?
    ) : DeviceOrientationState

    /**
     * DEGRADED: the sensor works but the heading of the top edge is not defined for this posture
     * (device too upright or face down). No heading value is produced.
     */
    data class HeadingUndefined(
        val reason: String,
        val tiltDeg: Double,
        val sensorTime: MonotonicElapsedNanos,
        val accuracy: Int?
    ) : DeviceOrientationState
}

interface DeviceOrientationSource {
    val orientation: StateFlow<DeviceOrientationState>
}

/** Orientation source whose sensor listener follows the UI lifecycle. start/stop are idempotent. */
interface ControllableOrientationSource : DeviceOrientationSource {
    fun start()
    fun stop()
}

/** Honest default until a sensor-backed source exists. */
class UnavailableDeviceOrientationSource(
    reason: String = "sensor-driven orientation is not integrated"
) : ControllableOrientationSource {
    private val state = MutableStateFlow<DeviceOrientationState>(DeviceOrientationState.Unavailable(reason))
    override val orientation: StateFlow<DeviceOrientationState> = state.asStateFlow()
    override fun start() = Unit
    override fun stop() = Unit
}
