package horizon.observatory.resilience

import kotlin.math.cos
import kotlin.math.max

class DeadReckoningBridge(
    private val horizontalGrowthMps: Double = 1.5,
    private val verticalGrowthMps: Double = 1.0
) {
    fun estimate(input: BridgeInput): NavigationEstimate {
        require(input.elapsedSeconds >= 0.0) { "elapsedSeconds must be non-negative" }
        val speed = input.velocityMps ?: 0.0
        val distance = speed * input.elapsedSeconds + 0.5 * (input.accelerationMps2 ?: 0.0) * input.elapsedSeconds * input.elapsedSeconds
        val heading = input.headingDeg
        val east = if (heading != null) distance * kotlin.math.sin(Math.toRadians(heading)) else 0.0
        val north = if (heading != null) distance * cos(Math.toRadians(heading)) else 0.0
        val metersPerDegLat = 111_320.0
        val metersPerDegLon = max(1.0, metersPerDegLat * cos(Math.toRadians(input.lastTrusted.latitudeDeg)))
        val uncertaintyGrowth = horizontalGrowthMps * input.elapsedSeconds
        val verticalGrowth = verticalGrowthMps * input.elapsedSeconds
        return NavigationEstimate(
            latitudeDeg = input.lastTrusted.latitudeDeg + north / metersPerDegLat,
            longitudeDeg = input.lastTrusted.longitudeDeg + east / metersPerDegLon,
            altitudeM = input.lastTrusted.altitudeM,
            timestampMonotonicNs = input.lastTrusted.timestampMonotonicNs + (input.elapsedSeconds * 1e9).toLong(),
            horizontalUncertaintyM = input.lastTrusted.horizontalUncertaintyM + uncertaintyGrowth,
            verticalUncertaintyM = input.lastTrusted.verticalUncertaintyM + verticalGrowth,
            provenance = "DEAD_RECKONED_FROM_LAST_TRUSTED_PVT",
            navigationState = if (input.imuAvailable) NavigationState.INERTIAL_BRIDGING else NavigationState.GNSS_LOST
        )
    }
}
