package horizon.observatory.resilience

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Bounds for the short-horizon coasting projection.
 *
 * PROVISIONAL: these constants are engineering placeholders. They have NOT been calibrated against
 * recorded traces, and the projection is NOT validated inertial navigation (it does not integrate
 * raw IMU samples).
 */
data class CoastingPolicy(
    val maxAgeSeconds: Double = 20.0,
    val maxHorizontalUncertaintyM: Double = 150.0,
    val horizontalGrowthMps: Double = 1.5,
    val verticalGrowthMps: Double = 1.0,
    /** Unmodelled manoeuvre allowance; uncertainty grows with 0.5 * a * t^2. */
    val unmodelledAccelerationMps2: Double = 0.5,
    /** At or below this speed the projection may hold position without a heading. */
    val stationarySpeedMps: Double = 0.5,
    val calibrated: Boolean = false
) {
    companion object {
        const val ESTIMATOR_ID = "CONSTANT_VELOCITY_COASTING_V1_PROVISIONAL"
    }
}

enum class CoastingRejection {
    INVALID_ANCHOR,
    INVALID_ELAPSED_TIME,
    MAX_AGE_EXCEEDED,
    SPEED_UNAVAILABLE,
    INVALID_SPEED,
    HEADING_UNAVAILABLE_WHILE_MOVING,
    INVALID_ACCELERATION,
    UNCERTAINTY_CEILING_EXCEEDED
}

sealed interface CoastingResult {
    data class Estimate(val estimate: NavigationEstimate) : CoastingResult
    data class Rejected(val reason: CoastingRejection) : CoastingResult
}

/**
 * Constant-velocity projection from the last trusted PVT. Emits an estimate only when anchor, speed,
 * course and timing are valid and the age/uncertainty ceilings hold; otherwise it says why not.
 * It never invents a heading, never invents an altitude, and never resets the time of the anchor.
 */
class DeadReckoningBridge(private val policy: CoastingPolicy = CoastingPolicy()) {

    fun estimate(input: BridgeInput): CoastingResult {
        val anchor = input.lastTrusted
        if (!isValidAnchor(anchor)) return CoastingResult.Rejected(CoastingRejection.INVALID_ANCHOR)
        val t = input.elapsedSeconds
        if (!t.isFinite() || t < 0.0) return CoastingResult.Rejected(CoastingRejection.INVALID_ELAPSED_TIME)
        if (t > policy.maxAgeSeconds) return CoastingResult.Rejected(CoastingRejection.MAX_AGE_EXCEEDED)
        val speed = input.velocityMps ?: return CoastingResult.Rejected(CoastingRejection.SPEED_UNAVAILABLE)
        if (!speed.isFinite() || speed < 0.0) return CoastingResult.Rejected(CoastingRejection.INVALID_SPEED)
        val acceleration = input.accelerationMps2
        if (acceleration != null && !acceleration.isFinite()) return CoastingResult.Rejected(CoastingRejection.INVALID_ACCELERATION)
        val heading = input.headingDeg?.takeIf { it.isFinite() }?.let { ((it % 360.0) + 360.0) % 360.0 }
        val moving = speed > policy.stationarySpeedMps
        if (moving && heading == null) return CoastingResult.Rejected(CoastingRejection.HEADING_UNAVAILABLE_WHILE_MOVING)

        val a = acceleration ?: 0.0
        // A decelerating target stops; it never reverses.
        val tEffective = if (a < 0.0 && speed + a * t < 0.0) speed / -a else t
        val alongTrackM = max(0.0, speed * tEffective + 0.5 * a * tEffective * tEffective)
        // Without a course the only supportable estimate is "held position" with the possible
        // displacement added to the uncertainty.
        val distanceM = if (heading == null) 0.0 else alongTrackM
        val unknownDirectionM = if (heading == null) speed * t else 0.0
        val uncertaintyM = anchor.horizontalUncertaintyM +
            policy.horizontalGrowthMps * t +
            0.5 * policy.unmodelledAccelerationMps2 * t * t +
            unknownDirectionM
        if (uncertaintyM > policy.maxHorizontalUncertaintyM) {
            return CoastingResult.Rejected(CoastingRejection.UNCERTAINTY_CEILING_EXCEEDED)
        }

        val (latitude, longitude) = if (distanceM == 0.0 || heading == null) {
            anchor.latitudeDeg to anchor.longitudeDeg
        } else {
            destination(anchor.latitudeDeg, anchor.longitudeDeg, heading, distanceM)
        }
        // Altitude is carried forward only when its uncertainty is known and can be grown honestly.
        val verticalUncertaintyM = anchor.verticalUncertaintyM?.let { it + policy.verticalGrowthMps * t }
        val altitudeM = if (verticalUncertaintyM != null) anchor.altitudeM else null
        return CoastingResult.Estimate(
            NavigationEstimate(
                latitudeDeg = latitude,
                longitudeDeg = longitude,
                altitudeM = altitudeM,
                timestampMonotonicNs = anchor.timestampMonotonicNs + (t * 1e9).toLong(),
                horizontalUncertaintyM = uncertaintyM,
                verticalUncertaintyM = verticalUncertaintyM,
                provenance = "DEAD_RECKONED_FROM_LAST_TRUSTED_PVT",
                navigationState = if (input.imuAvailable) NavigationState.INERTIAL_BRIDGING else NavigationState.GNSS_LOST,
                speedMps = if (heading != null) max(0.0, speed + a * t) else null,
                bearingDeg = heading,
                estimator = CoastingPolicy.ESTIMATOR_ID
            )
        )
    }

    private fun isValidAnchor(p: TrustedPvt): Boolean =
        p.latitudeDeg.isFinite() && p.latitudeDeg >= -90.0 && p.latitudeDeg <= 90.0 &&
            p.longitudeDeg.isFinite() && p.longitudeDeg >= -180.0 && p.longitudeDeg <= 180.0 &&
            p.horizontalUncertaintyM.isFinite() && p.horizontalUncertaintyM >= 0.0 &&
            p.timestampMonotonicNs > 0L &&
            (p.verticalUncertaintyM == null || (p.verticalUncertaintyM.isFinite() && p.verticalUncertaintyM >= 0.0)) &&
            (p.altitudeM == null || p.altitudeM.isFinite())

    companion object {
        private const val EARTH_RADIUS_M = 6_371_008.8

        /** Great-circle destination; handles the antimeridian and the poles without special cases. */
        fun destination(latDeg: Double, lonDeg: Double, bearingDeg: Double, distanceM: Double): Pair<Double, Double> {
            val delta = distanceM / EARTH_RADIUS_M
            val theta = Math.toRadians(bearingDeg)
            val phi1 = Math.toRadians(latDeg)
            val lambda1 = Math.toRadians(lonDeg)
            val sinPhi2 = sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta)
            val phi2 = asin(sinPhi2.coerceIn(-1.0, 1.0))
            val lambda2 = lambda1 + atan2(sin(theta) * sin(delta) * cos(phi1), cos(delta) - sin(phi1) * sin(phi2))
            val lon = ((Math.toDegrees(lambda2) + 540.0) % 360.0) - 180.0
            return Math.toDegrees(phi2) to lon
        }
    }
}
