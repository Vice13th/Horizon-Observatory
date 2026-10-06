package horizon.observatory.astronomy.propagation

import java.time.Instant

/**
 * Input contract for a vetted SGP4/SDP4 implementation, in the unit conventions such libraries use.
 * Built from an OmmRecord by Sgp4Sdp4PropagationEngine; nothing here is invented.
 */
data class Sgp4Sdp4Input(
    val noradCatId: Long,
    val objectName: String?,
    val objectId: String?,
    val classification: String?,
    val epochUtc: Instant,
    val epochRaw: String,
    val meanMotionRadPerMin: Double,
    val eccentricity: Double,
    val inclinationRad: Double,
    val raanRad: Double,
    val argOfPericenterRad: Double,
    val meanAnomalyRad: Double,
    val bstar: Double,
    val meanMotionDotRevPerDay2: Double?,
    val meanMotionDdotRevPerDay3: Double?,
    val ephemerisType: Int?,
    val elementSetNo: Int?,
    val revAtEpoch: Long?
)

data class BackendState(
    val xKm: Double,
    val yKm: Double,
    val zKm: Double,
    val vxKmS: Double,
    val vyKmS: Double,
    val vzKmS: Double
)

sealed interface BackendResult {
    data class Ok(val state: BackendState, val modelUsed: PropagationModelFamily) : BackendResult
    data class Error(val code: Int?, val message: String) : BackendResult
    data class Unavailable(val reason: String) : BackendResult
}

interface Sgp4Sdp4Backend {
    val name: String
    val version: String?
    val supportsDeepSpace: Boolean
    fun propagate(input: Sgp4Sdp4Input, minutesSinceEpoch: Double): BackendResult
}

object UnavailableSgp4Sdp4Backend : Sgp4Sdp4Backend {
    override val name: String = "NONE"
    override val version: String? = null
    override val supportsDeepSpace: Boolean = false
    override fun propagate(input: Sgp4Sdp4Input, minutesSinceEpoch: Double): BackendResult =
        BackendResult.Unavailable("no verified SGP4/SDP4 backend is integrated")
}
