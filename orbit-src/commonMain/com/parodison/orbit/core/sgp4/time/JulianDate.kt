package com.parodison.orbit.core.sgp4.time

import kotlin.time.Instant

/** Julian date corresponding to this instant (UT1 ~ UTC for these purposes). */
internal fun Instant.toJulianDate(): Double {
    val secondsSinceUnixEpoch = epochSeconds + nanosecondsOfSecond / 1_000_000_000.0
    return secondsSinceUnixEpoch / 86400.0 + 2440587.5
}
