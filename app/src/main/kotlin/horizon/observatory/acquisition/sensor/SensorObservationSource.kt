package horizon.observatory.acquisition.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import horizon.observatory.acquisition.base.ObservationSource
import horizon.observatory.domain.model.CapabilityState
import horizon.observatory.domain.model.EvidenceStatus
import horizon.observatory.domain.model.ObservationProvenance
import horizon.observatory.domain.model.ObservationType
import horizon.observatory.domain.model.RawObservation
import horizon.observatory.domain.model.SourceStatus
import horizon.observatory.domain.model.TimestampDomain
import horizon.observatory.core.time.TimestampEngine
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject

class SensorObservationSource(private val context: Context) : ObservationSource {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private var listener: SensorEventListener? = null
    private var closeFlow: (() -> Unit)? = null

    override val status: SourceStatus
        get() = when {
            sensorManager == null -> SourceStatus.UNAVAILABLE
            sensorManager.getSensorList(Sensor.TYPE_ALL).none { it.type in SUPPORTED_TYPES } -> SourceStatus.UNAVAILABLE
            else -> SourceStatus.AVAILABLE
        }

    override val observationFlow: Flow<RawObservation> = callbackFlow {
        val manager = sensorManager ?: run {
            close()
            return@callbackFlow
        }
        val activityRecognitionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val sensors = manager.getSensorList(Sensor.TYPE_ALL)
            .filter { it.type in SUPPORTED_TYPES || it.isStepSensor() }
            .filterNot { it.isStepSensor() && !activityRecognitionGranted }
        if (sensors.isEmpty()) {
            close()
            return@callbackFlow
        }

        val eventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.toRawObservation())
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = eventListener
        closeFlow = { close() }
        sensors.forEach { sensor ->
            manager.registerListener(eventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        awaitClose {
            manager.unregisterListener(eventListener)
            listener = null
            closeFlow = null
        }
    }

    override fun start() = Unit
    override fun stop() { listener?.let { sensorManager?.unregisterListener(it) }; listener = null; closeFlow?.invoke() }

    private fun SensorEvent.toRawObservation(): RawObservation {
        val elapsedNs = timestamp
        val values = values.map { it.toDouble() }
        val sensor = sensor
        val type = when (sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> ObservationType.SENSOR_ACCEL
            Sensor.TYPE_GYROSCOPE -> ObservationType.SENSOR_GYRO
            Sensor.TYPE_MAGNETIC_FIELD -> ObservationType.SENSOR_MAGNETIC
            Sensor.TYPE_LIGHT -> ObservationType.SENSOR_LIGHT
            Sensor.TYPE_PROXIMITY -> ObservationType.SENSOR_PROXIMITY
            Sensor.TYPE_GRAVITY -> ObservationType.SENSOR_GRAVITY
            Sensor.TYPE_LINEAR_ACCELERATION -> ObservationType.SENSOR_LINEAR_ACCEL
            Sensor.TYPE_ROTATION_VECTOR -> ObservationType.SENSOR_ROTATION
            Sensor.TYPE_PRESSURE -> ObservationType.SENSOR_PRESSURE
            Sensor.TYPE_STEP_DETECTOR,
            Sensor.TYPE_STEP_COUNTER -> ObservationType.SENSOR_STEP
            else -> ObservationType.SENSOR_GENERIC
        }
        val payload = JSONObject().apply {
            put("sensorType", sensor.type)
            put("sensorName", sensor.name ?: JSONObject.NULL)
            put("vendor", sensor.vendor ?: JSONObject.NULL)
            put("accuracy", accuracy)
            // Fixed: JSONObject.put(String, Any) with a raw Kotlin List has implicit,
            // version-dependent serialization behavior in org.json. Explicit JSONArray
            // construction is unambiguous and guarantees a real JSON array of numbers.
            put("values", org.json.JSONArray(values))
            put("sensorTimestampNs", elapsedNs)
        }
        return RawObservation(
            type = type,
            utcTimestampMs = TimestampEngine.elapsedNanosToUtcMillis(elapsedNs),
            monotonicTimestampNs = elapsedNs,
            provider = "SensorManager",
            payloadJson = payload.toString(),
            technology = "SENSOR",
            capabilityState = CapabilityState.AVAILABLE_NOW,
            evidenceStatus = EvidenceStatus.MEASURED,
            provenance = ObservationProvenance.RAW_ACQUISITION,
            timestampDomain = TimestampDomain.ANDROID_ELAPSED_REALTIME_NANOS
        )
    }

    private fun Sensor.isStepSensor(): Boolean =
        type == Sensor.TYPE_STEP_DETECTOR ||
            type == Sensor.TYPE_STEP_COUNTER ||
            name?.contains("step", ignoreCase = true) == true

    companion object {
        private val SUPPORTED_TYPES = setOf(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_LIGHT,
            Sensor.TYPE_PROXIMITY,
            Sensor.TYPE_GRAVITY,
            Sensor.TYPE_LINEAR_ACCELERATION,
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_PRESSURE,
            Sensor.TYPE_STEP_DETECTOR,
            Sensor.TYPE_STEP_COUNTER
        )
    }
}
