package horizon.observatory.astronomy.prediction

import horizon.observatory.astronomy.ObserverLocation

enum class ObserverSource { RECEIVER_LOCATION_FIX, USER_SUPPLIED }

/**
 * Observer used for predicted az/el. Carries where the position came from so a prediction can state
 * its own basis. [fixTimeUtcMs] is the device-clock time of the fix when known.
 */
data class ObserverPosition(
    val location: ObserverLocation,
    val source: ObserverSource,
    val fixTimeUtcMs: Long?
)
