package horizon.observatory.acquisition.base

import horizon.observatory.domain.model.ObservationType
import horizon.observatory.domain.model.RawObservation
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcquisitionDiagnosticsTest {
    private fun observation() = RawObservation(
        type = ObservationType.GNSS_RAW_MEASUREMENT,
        utcTimestampMs = 123L,
        monotonicTimestampNs = 456L,
        provider = "GnssMeasurement",
        payloadJson = "{\"svid\":7}",
    )

    @Test
    fun acceptedSendIncrementsExpectedAdmissionCounters() {
        val diagnostics = AcquisitionDiagnostics("gnss", sessionId = "S1")
        val channel = Channel<RawObservation>(capacity = 1)

        assertTrue(diagnostics.offer(channel) { observation() })

        val snapshot = diagnostics.snapshot()
        assertEquals("gnss", snapshot.sourceName)
        assertEquals("S1", snapshot.sessionId)
        assertEquals(1L, snapshot.observationsConstructed)
        assertEquals(1L, snapshot.sendAttempts)
        assertEquals(1L, snapshot.sendAccepted)
        assertEquals(0L, snapshot.sendRejectedCapacity)
        assertEquals(0L, snapshot.sendRejectedClosed)
        assertEquals(0L, snapshot.constructionFailures)
    }

    @Test
    fun fullChannelRejectionIsCountedAsCapacityLoss() {
        val diagnostics = AcquisitionDiagnostics("gnss")
        val channel = Channel<RawObservation>(capacity = 1)
        assertTrue(diagnostics.offer(channel) { observation() })

        assertFalse(diagnostics.offer(channel) { observation() })

        val snapshot = diagnostics.snapshot()
        assertEquals(2L, snapshot.observationsConstructed)
        assertEquals(2L, snapshot.sendAttempts)
        assertEquals(1L, snapshot.sendAccepted)
        assertEquals(1L, snapshot.sendRejectedCapacity)
        assertEquals(0L, snapshot.sendRejectedClosed)
        assertEquals("CHANNEL_CAPACITY", snapshot.lastFailure)
    }

    @Test
    fun closedChannelRejectionIsDistinctFromCapacityLoss() {
        val diagnostics = AcquisitionDiagnostics("gnss")
        val channel = Channel<RawObservation>(capacity = 1)
        channel.close()

        assertFalse(diagnostics.offer(channel) { observation() })

        val snapshot = diagnostics.snapshot()
        assertEquals(1L, snapshot.observationsConstructed)
        assertEquals(1L, snapshot.sendAttempts)
        assertEquals(0L, snapshot.sendAccepted)
        assertEquals(0L, snapshot.sendRejectedCapacity)
        assertEquals(1L, snapshot.sendRejectedClosed)
        assertEquals("CHANNEL_CLOSED", snapshot.lastFailure)
    }

    @Test
    fun constructorFailureDoesNotCountAsConstructedOrAttempted() {
        val diagnostics = AcquisitionDiagnostics("gnss")
        val channel = Channel<RawObservation>(capacity = 1)

        assertFalse(diagnostics.offer(channel) { throw IllegalArgumentException("bad observation") })

        val snapshot = diagnostics.snapshot()
        assertEquals(0L, snapshot.observationsConstructed)
        assertEquals(0L, snapshot.sendAttempts)
        assertEquals(0L, snapshot.sendAccepted)
        assertEquals(1L, snapshot.constructionFailures)
        assertEquals(IllegalArgumentException::class.java.name, snapshot.lastFailure)
    }

    @Test
    fun singleCallbackConstructionFailureIsCountedAndCallbackFinished() {
        val diagnostics = AcquisitionDiagnostics("gnss")
        val channel = Channel<RawObservation>(capacity = 1)

        assertFalse(
            diagnostics.recordSingleCallback(
                block = { throw IllegalStateException("payload serialization failed") },
                channel = channel,
            )
        )

        val snapshot = diagnostics.snapshot()
        assertEquals(1L, snapshot.callbackEventsReceived)
        assertEquals(0L, snapshot.callbackEventsInFlight)
        assertEquals(1L, snapshot.expectedObservations)
        assertEquals(0L, snapshot.observationsConstructed)
        assertEquals(0L, snapshot.sendAttempts)
        assertEquals(1L, snapshot.constructionFailures)
        assertEquals(IllegalStateException::class.java.name, snapshot.lastFailure)
    }

    @Test
    fun snapshotJsonPreservesSessionAndPerTypeAdmissionCounters() {
        val diagnostics = AcquisitionDiagnostics("gnss", sessionId = "session-42")
        val channel = Channel<RawObservation>(capacity = 1)
        val started = diagnostics.callbackStarted(expectedObservations = 1)
        diagnostics.offer(channel) { observation() }
        diagnostics.callbackFinished(started)

        val json = diagnostics.snapshot().toJsonObject()
        assertEquals("gnss", json.getString("sourceName"))
        assertEquals("session-42", json.getString("sessionId"))
        assertEquals(1L, json.getLong("callbackEventsReceived"))
        assertEquals(1L, json.getLong("expectedObservations"))
        assertEquals(1L, json.getLong("sendAccepted"))
        assertEquals(
            1L,
            json.getJSONObject("observationsByType")
                .getJSONObject(ObservationType.GNSS_RAW_MEASUREMENT.name)
                .getLong("sendAccepted")
        )
    }

    @Test
    fun callbackBatchExpectedCardinalityIsTrackedSeparatelyFromAdmissions() {
        val diagnostics = AcquisitionDiagnostics("gnss")
        val channel = Channel<RawObservation>(capacity = 2)
        val started = diagnostics.callbackStarted(expectedObservations = 4)
        repeat(4) { diagnostics.offer(channel) { observation() } }
        diagnostics.callbackFinished(started)

        val snapshot = diagnostics.snapshot()
        assertEquals(1L, snapshot.callbackEventsReceived)
        assertEquals(0L, snapshot.callbackEventsInFlight)
        assertEquals(4L, snapshot.expectedObservations)
        assertEquals(4L, snapshot.observationsConstructed)
        assertEquals(4L, snapshot.sendAttempts)
        assertEquals(2L, snapshot.sendAccepted)
        assertEquals(2L, snapshot.sendRejectedCapacity)
        assertEquals(0L, snapshot.sendRejectedClosed)
        assertEquals(4L, snapshot.observationsByType["GNSS_RAW_MEASUREMENT"]!!.observationsConstructed)
        assertEquals(2L, snapshot.observationsByType["GNSS_RAW_MEASUREMENT"]!!.sendAccepted)
        assertTrue(snapshot.callbackDurationTotalNs >= 0L)
        assertTrue(snapshot.callbackDurationMaxNs >= 0L)
    }
}
