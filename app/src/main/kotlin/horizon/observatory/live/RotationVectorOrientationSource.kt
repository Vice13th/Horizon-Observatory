package horizon.observatory.live

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import horizon.observatory.astronomy.orientation.ControllableOrientationSource
import horizon.observatory.astronomy.orientation.DeviceOrientationState
import horizon.observatory.astronomy.time.MonotonicElapsedNanos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live device pose from TYPE_ROTATION_VECTOR. UI-lifecycle driven (start on resume, stop on pause);
 * independent of recording, and it never writes to storage or to any observation. The pose is a
 * presentation input only.
 *
 * Heading reference is MAGNETIC north (rotation-vector convention); conversion to true north happens
 * in SkyProjection.decide with an explicit declination. SensorEvent.timestamp is stored as
 * MonotonicElapsedNanos; that it is in the elapsedRealtime domain on every device is UNVERIFIED and
 * it is never compared with UTC. Runtime behaviour on a device is UNVERIFIED.
 */
class RotationVectorOrientationSource(context: Context) : ControllableOrientationSource, SensorEventListener {
    private val sensorManager: SensorManager? =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val state = MutableStateFlow<DeviceOrientationState>(
        DeviceOrientationState.Unavailable(
            if (sensor == null) "no rotation-vector sensor on this device" else "sensor not started"
        )
    )
    override val orientation: StateFlow<DeviceOrientationState> = state.asStateFlow()

    private var registered = false

    @Synchronized
    override fun start() {
        val manager = sensorManager ?: return
        val rotation = sensor ?: return
        if (registered) return
        registered = manager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_UI)
        if (!registered) state.value = DeviceOrientationState.Unavailable("rotation-vector listener registration failed")
    }

    @Synchronized
    override fun stop() {
        if (registered) {
            sensorManager?.unregisterListener(this)
            registered = false
        }
        if (sensor != null) state.value = DeviceOrientationState.Unavailable("sensor not started")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val values = DoubleArray(event.values.size) { event.values[it].toDouble() }
        state.value = PoseMath.fromRotationVector(
            values = values,
            sensorTime = MonotonicElapsedNanos(event.timestamp),
            accuracy = event.accuracy
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
