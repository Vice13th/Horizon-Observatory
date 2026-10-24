package horizon.observatory.acquisition.gnss

import org.junit.Assert.assertEquals
import org.junit.Test

class GnssEventTimestampTest {
    @Test
    fun api28UsesCallbackReceiptTimeWithoutCallingReceiverElapsedRealtimeApi() {
        val selected = selectGnssEventTimestamp(
            apiLevel = 28,
            hasReceiverElapsedRealtime = false,
            receiverElapsedRealtimeNanos = 123L,
            callbackElapsedRealtimeNanos = 456L
        )

        assertEquals(456L, selected.elapsedRealtimeNanos)
        assertEquals("CALLBACK_RECEIPT_API_UNAVAILABLE", selected.source)
    }

    @Test
    fun api29UsesCallbackReceiptTimeWhenReceiverClockDoesNotAdvertiseElapsedRealtime() {
        val selected = selectGnssEventTimestamp(
            apiLevel = 29,
            hasReceiverElapsedRealtime = false,
            receiverElapsedRealtimeNanos = 123L,
            callbackElapsedRealtimeNanos = 456L
        )

        assertEquals(456L, selected.elapsedRealtimeNanos)
        assertEquals("CALLBACK_RECEIPT_CLOCK_FIELD_UNAVAILABLE", selected.source)
    }

    @Test
    fun api29UsesReceiverElapsedRealtimeOnlyWhenAdvertisedAvailable() {
        val selected = selectGnssEventTimestamp(
            apiLevel = 29,
            hasReceiverElapsedRealtime = true,
            receiverElapsedRealtimeNanos = 123L,
            callbackElapsedRealtimeNanos = 456L
        )

        assertEquals(123L, selected.elapsedRealtimeNanos)
        assertEquals("GNSS_CLOCK_ELAPSED_REALTIME", selected.source)
    }

    @Test
    fun apiBelow29NeverUsesReceiverElapsedRealtimeEvenIfCallerPassesTrue() {
        val selected = selectGnssEventTimestamp(
            apiLevel = 26,
            hasReceiverElapsedRealtime = true,
            receiverElapsedRealtimeNanos = 123L,
            callbackElapsedRealtimeNanos = 456L
        )

        assertEquals(456L, selected.elapsedRealtimeNanos)
        assertEquals("CALLBACK_RECEIPT_API_UNAVAILABLE", selected.source)
    }
}
