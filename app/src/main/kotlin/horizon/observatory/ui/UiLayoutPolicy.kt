package horizon.observatory.ui

/**
 * Presentation-only layout decisions shared with JVM regression tests.
 * Invalid constraints deliberately choose the most conservative, least dense layout.
 */
internal fun metricColumnsForWidth(widthDp: Float, fontScale: Float): Int {
    if (!widthDp.isFinite() || widthDp <= 0f || !fontScale.isFinite() || fontScale <= 0f) return 1
    return when {
        fontScale >= 1.8f || widthDp < 300f -> 1
        widthDp < 340f || fontScale >= 1.35f -> 2
        widthDp < 520f -> 2
        else -> 3
    }
}

internal fun satelliteColumnsForWidth(widthDp: Float, fontScale: Float): Int {
    if (!widthDp.isFinite() || widthDp <= 0f || !fontScale.isFinite() || fontScale <= 0f) return 1
    return when {
        fontScale >= 1.8f || widthDp < 300f -> 1
        fontScale >= 1.5f || widthDp < 520f -> 2
        else -> 3
    }
}


/** Device identity shown before and after a recording session exists. */
internal data class DeviceIdentityDisplay(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val apiLevel: Int
)

internal fun deviceIdentityDisplay(
    recordedManufacturer: String?,
    recordedModel: String?,
    recordedAndroidRelease: String?,
    recordedApiLevel: Int?,
    runtimeManufacturer: String?,
    runtimeModel: String?,
    runtimeAndroidRelease: String?,
    runtimeApiLevel: Int
): DeviceIdentityDisplay {
    fun usable(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() && !it.equals("UNKNOWN", ignoreCase = true) }

    return DeviceIdentityDisplay(
        manufacturer = usable(recordedManufacturer) ?: usable(runtimeManufacturer) ?: "UNKNOWN",
        model = usable(recordedModel) ?: usable(runtimeModel) ?: "UNKNOWN",
        androidRelease = usable(recordedAndroidRelease) ?: usable(runtimeAndroidRelease) ?: "UNKNOWN",
        apiLevel = recordedApiLevel?.takeIf { it > 0 } ?: runtimeApiLevel.takeIf { it > 0 } ?: 0
    )
}
