package horizon.observatory.live

import horizon.observatory.astronomy.orientation.DeviceOrientationState
import horizon.observatory.astronomy.orientation.HeadingReference
import kotlin.math.cos
import kotlin.math.sin

/** How the sky plot is oriented on screen. A PRESENTATION choice; it never changes stored values. */
sealed interface SkyOrientationMode {
    /** True north at the top of the plot (the existing behaviour). */
    data object NorthUp : SkyOrientationMode

    /** The device's top edge direction (true-north bearing [headingTrueDeg]) is at the top. */
    data class HeadingUp(val headingTrueDeg: Double) : SkyOrientationMode
}

enum class SkyOrientationStatus {
    ROTATING_TRUE_NORTH,
    NORTH_UP_NOT_LIVE,
    NORTH_UP_ORIENTATION_UNAVAILABLE,
    NORTH_UP_HEADING_UNDEFINED,
    NORTH_UP_DECLINATION_UNAVAILABLE
}

/** The orientation chosen for the plot together with the explicit reason (never silent). */
data class SkyOrientationDecision(
    val mode: SkyOrientationMode,
    val status: SkyOrientationStatus,
    val detail: String
)

/** Unit-disk plot coordinates: x to the right, y up; radius 0 = zenith, 1 = horizon, >1 = below it. */
data class SkyPlotPoint(val x: Double, val y: Double)

/**
 * Presentation transform: observed (azimuth, elevation) + orientation -> screen-plane position.
 * Pure. Takes values in, returns new values; mutates nothing.
 */
object SkyProjection {

    /**
     * [declinationDeg] is positive east (true = magnetic + declination) or null when unknown. The plot
     * rotates only for a true-north heading of a LIVE session; otherwise it stays north-up and says
     * why. Rotating a plot of recorded (past) satellite positions by the current device heading would
     * be meaningless, so [isLive] = false always yields north-up.
     */
    fun decide(pose: DeviceOrientationState, declinationDeg: Double?, isLive: Boolean): SkyOrientationDecision =
        if (!isLive) {
            SkyOrientationDecision(
                SkyOrientationMode.NorthUp,
                SkyOrientationStatus.NORTH_UP_NOT_LIVE,
                "recorded data (no active session); live orientation not applied"
            )
        } else when (pose) {
            is DeviceOrientationState.Unavailable ->
                SkyOrientationDecision(
                    SkyOrientationMode.NorthUp,
                    SkyOrientationStatus.NORTH_UP_ORIENTATION_UNAVAILABLE,
                    "orientation unavailable: ${pose.reason}"
                )
            is DeviceOrientationState.HeadingUndefined ->
                SkyOrientationDecision(
                    SkyOrientationMode.NorthUp,
                    SkyOrientationStatus.NORTH_UP_HEADING_UNDEFINED,
                    pose.reason
                )
            is DeviceOrientationState.Available -> {
                val trueHeading = PoseMath.toTrueNorth(pose, declinationDeg)
                if (trueHeading.reference == HeadingReference.TRUE_NORTH) {
                    SkyOrientationDecision(
                        SkyOrientationMode.HeadingUp(trueHeading.headingDeg),
                        SkyOrientationStatus.ROTATING_TRUE_NORTH,
                        "heading ${"%.0f".format(trueHeading.headingDeg)} deg true"
                    )
                } else {
                    SkyOrientationDecision(
                        SkyOrientationMode.NorthUp,
                        SkyOrientationStatus.NORTH_UP_DECLINATION_UNAVAILABLE,
                        "magnetic heading only; declination unavailable (no observer position)"
                    )
                }
            }
        }

    /** Returns null for non-finite input; never substitutes a position. */
    fun project(azimuthDeg: Double, elevationDeg: Double, mode: SkyOrientationMode): SkyPlotPoint? {
        if (!azimuthDeg.isFinite() || !elevationDeg.isFinite()) return null
        val bearingDeg = when (mode) {
            SkyOrientationMode.NorthUp -> azimuthDeg
            is SkyOrientationMode.HeadingUp -> azimuthDeg - mode.headingTrueDeg
        }
        val el = elevationDeg.coerceIn(-90.0, 90.0)
        val radius = (90.0 - el) / 90.0
        val rad = Math.toRadians(bearingDeg)
        return SkyPlotPoint(x = sin(rad) * radius, y = cos(rad) * radius)
    }

    /** Plot position of a compass direction on the horizon ring (for rotating N/E/S/W labels). */
    fun cardinal(azimuthDeg: Double, mode: SkyOrientationMode): SkyPlotPoint =
        project(azimuthDeg, 0.0, mode) ?: SkyPlotPoint(0.0, 0.0)
}
