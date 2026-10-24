package horizon.observatory.ui

import java.util.Locale

/**
 * Human-readable description for the semantic node representing one observed satellite.
 * This is descriptive only: it never infers missing measurements or fix use.
 */
internal fun satelliteAccessibilityDescription(
    identifier: String,
    cn0DbHz: Double?,
    elevationDeg: Double?,
    azimuthDeg: Double?,
    usedInFix: Boolean?
): String {
    val label = identifier.ifBlank { "Unknown" }
    fun measurement(value: Double?, unit: String): String =
        if (value == null || !value.isFinite()) "unavailable"
        else "${String.format(Locale.ROOT, "%.1f", value)} $unit"

    val fixUse = when (usedInFix) {
        true -> "reported as used in the position fix"
        false -> "reported as not used in the position fix"
        null -> "not reported"
    }

    return "$label satellite. C/N0: ${measurement(cn0DbHz, "dB-Hz")}. " +
        "Elevation: ${measurement(elevationDeg, "degrees")}. " +
        "Azimuth: ${measurement(azimuthDeg, "degrees")}. " +
        "Position-fix use: $fixUse."
}
