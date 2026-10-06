package horizon.observatory.core.capability

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class CapabilityScanner(private val context: Context) {
    private val packageManager = context.packageManager
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val locationManager = context.getSystemService(LocationManager::class.java)

    fun buildReport(): CapabilityReport {
        val root = JSONObject()
        val device = JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("androidRelease", Build.VERSION.RELEASE ?: JSONObject.NULL)
            .put("api", Build.VERSION.SDK_INT)
            .put("fingerprint", Build.FINGERPRINT ?: JSONObject.NULL)
        root.put("device", device)
        root.put("permissions", permissionState())
        root.put("features", featureState())
        root.put("gnss", gnssState())
        root.put("cellular", cellularState())
        root.put("sensors", sensorState())
        root.put("status", "OBSERVED_AT_SESSION_START")
        return CapabilityReport(root)
    }

    private fun permissionState() = JSONObject().apply {
        put("fine_location", permissionState(Manifest.permission.ACCESS_FINE_LOCATION))
        put("coarse_location", permissionState(Manifest.permission.ACCESS_COARSE_LOCATION))
        put("read_phone_state", permissionState(Manifest.permission.READ_PHONE_STATE))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            put("post_notifications", permissionState(Manifest.permission.POST_NOTIFICATIONS))
        } else {
            put("post_notifications", "NOT_APPLICABLE")
        }
    }

    private fun featureState() = JSONObject().apply {
        put("location", packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION))
        put("gps", packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS))
        put("telephony", packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            put("telephony_radio_access", packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS))
        } else {
            put("telephony_radio_access", "NOT_EXPOSED")
        }
    }

    private fun gnssState() = JSONObject().apply {
        put("location_provider", locationManager?.let { runCatching { it.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) } ?: false)
        put("raw_measurements", capability30 { it.hasMeasurements() })
        put("navigation_messages", capability30 { it.hasNavigationMessages() })
        put("antenna_info", capability30 { it.hasAntennaInfo() })
        if (Build.VERSION.SDK_INT >= 34) {
            put("accumulated_delta_range", runCatching { capabilityValue(locationManager?.gnssCapabilities?.hasAccumulatedDeltaRange()) }.getOrDefault("UNKNOWN"))
            val signalTypes = runCatching { locationManager?.gnssCapabilities?.gnssSignalTypes?.map { it.toString() } ?: emptyList() }.getOrDefault(emptyList())
            put("signal_types", JSONArray(signalTypes))
            put("signal_type_count", signalTypes.size)
        } else {
            put("accumulated_delta_range", "UNVERIFIED")
            put("signal_types", JSONArray())
            put("signal_type_count", "UNVERIFIED")
        }
        put("carrier_phase", "UNVERIFIED")
        put("automatic_gain_control", "UNVERIFIED")
        put("multi_frequency", if (Build.VERSION.SDK_INT >= 34) {
            val count = runCatching { locationManager?.gnssCapabilities?.gnssSignalTypes?.size ?: 0 }.getOrDefault(0)
            if (count > 1) "SUPPORTED" else "UNVERIFIED"
        } else "UNVERIFIED")
        put("hardware_model", if (Build.VERSION.SDK_INT >= 28) locationManager?.gnssHardwareModelName ?: JSONObject.NULL else JSONObject.NULL)
        put("hardware_year", if (Build.VERSION.SDK_INT >= 28) locationManager?.gnssYearOfHardware ?: JSONObject.NULL else JSONObject.NULL)
        put("capabilities_string", if (Build.VERSION.SDK_INT >= 30) locationManager?.gnssCapabilities?.toString() ?: "UNKNOWN" else "UNVERIFIED")
    }

    private fun cellularState() = JSONObject().apply {
        put("telephony_feature", packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
        put("fine_location_required", granted(Manifest.permission.ACCESS_FINE_LOCATION))
        put("phone_state_permission", permissionState(Manifest.permission.READ_PHONE_STATE))
        put("live_cell_info_callback", if (Build.VERSION.SDK_INT >= 31) "REQUIRES_READ_PHONE_STATE_AND_FINE_LOCATION" else "DEPRECATED_PHONE_STATE_LISTENER")
        put("status", if (packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) "SUPPORTED" else "UNAVAILABLE")
        put("fresh_update_api", if (Build.VERSION.SDK_INT >= 29) "SUPPORTED" else "UNAVAILABLE")
        put("fresh_update_rate_limit", "ANDROID_MANAGED")
    }

    private fun sensorState() = JSONObject().apply {
        val types = linkedMapOf(
            "accelerometer" to Sensor.TYPE_ACCELEROMETER,
            "gyroscope" to Sensor.TYPE_GYROSCOPE,
            "magnetometer" to Sensor.TYPE_MAGNETIC_FIELD,
            "light" to Sensor.TYPE_LIGHT,
            "proximity" to Sensor.TYPE_PROXIMITY,
            "rotation_vector" to Sensor.TYPE_ROTATION_VECTOR,
            "gravity" to Sensor.TYPE_GRAVITY,
            "linear_acceleration" to Sensor.TYPE_LINEAR_ACCELERATION,
            "pressure" to Sensor.TYPE_PRESSURE,
            "step_detector" to Sensor.TYPE_STEP_DETECTOR,
            "step_counter" to Sensor.TYPE_STEP_COUNTER
        )
        types.forEach { (name, type) ->
            val matches = sensorManager?.getSensorList(type).orEmpty()
            put(name, if (matches.isEmpty()) "UNAVAILABLE" else JSONArray(matches.map { sensorJson(it) }))
        }
    }

    private fun sensorJson(sensor: Sensor) = JSONObject().apply {
        put("name", sensor.name ?: JSONObject.NULL)
        put("vendor", sensor.vendor ?: JSONObject.NULL)
        put("version", sensor.version)
        put("powerMah", sensor.power)
        put("resolution", sensor.resolution)
        put("maxRange", sensor.maximumRange)
        put("wakeUp", sensor.isWakeUpSensor)
        if (Build.VERSION.SDK_INT >= 24) put("maxDelayUs", sensor.maxDelay)
        put("minDelayUs", sensor.minDelay)
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun permissionState(permission: String): String =
        if (granted(permission)) "GRANTED" else "PERMISSION_DENIED"

    private inline fun capability30(block: (android.location.GnssCapabilities) -> Boolean): Any =
        if (Build.VERSION.SDK_INT >= 30) runCatching { if (block(locationManager!!.gnssCapabilities)) "SUPPORTED" else "UNSUPPORTED" }.getOrDefault("UNKNOWN") else "UNVERIFIED"

    private fun capabilityValue(value: Int?): String = when (value) {
        1 -> "SUPPORTED"
        0 -> "UNSUPPORTED"
        else -> "UNKNOWN"
    }
}

data class CapabilityReport(val json: JSONObject)
