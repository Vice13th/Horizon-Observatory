package horizon.observatory.acquisition.gnss

/**
 * Selects the monotonic timestamp used to index a GNSS measurements callback.
 *
 * Android's receiver-clock elapsed-realtime accessors are API 29+. If the API is
 * unavailable or the clock does not advertise the field, callback receipt time
 * is used and explicitly labelled; it must not be described as receiver measurement time.
 */
internal data class GnssEventTimestamp(
    val elapsedRealtimeNanos: Long,
    val source: String
)

internal fun selectGnssEventTimestamp(
    apiLevel: Int,
    hasReceiverElapsedRealtime: Boolean,
    receiverElapsedRealtimeNanos: Long,
    callbackElapsedRealtimeNanos: Long
): GnssEventTimestamp {
    if (apiLevel >= 29 && hasReceiverElapsedRealtime) {
        return GnssEventTimestamp(
            elapsedRealtimeNanos = receiverElapsedRealtimeNanos,
            source = "GNSS_CLOCK_ELAPSED_REALTIME"
        )
    }

    val source = if (apiLevel >= 29) {
        "CALLBACK_RECEIPT_CLOCK_FIELD_UNAVAILABLE"
    } else {
        "CALLBACK_RECEIPT_API_UNAVAILABLE"
    }
    return GnssEventTimestamp(
        elapsedRealtimeNanos = callbackElapsedRealtimeNanos,
        source = source
    )
}
