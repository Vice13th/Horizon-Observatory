package horizon.observatory.acquisition.gnss

import horizon.observatory.domain.model.ObservationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test

class GnssDeliveryDiagnosticsTest {
    @Test
    fun accountsAcceptedRejectedAndQueueFailureByType() {
        val diagnostics = GnssDeliveryDiagnostics()
        diagnostics.onMeasurementsCallback(3)
        diagnostics.recordDelivery(ObservationType.GNSS_CLOCK, success = true)
        diagnostics.recordDelivery(ObservationType.GNSS_RAW_MEASUREMENT, success = true)
        diagnostics.recordDelivery(
            ObservationType.GNSS_RAW_MEASUREMENT,
            success = false,
            reason = GnssDeliveryDiagnostics.FailureReason.CHANNEL_FULL
        )
        diagnostics.recordDelivery(
            ObservationType.GNSS_RAW_MEASUREMENT,
            success = false,
            reason = GnssDeliveryDiagnostics.FailureReason.CHANNEL_CLOSED
        )

        val snapshot = diagnostics.snapshot()
        val byType = snapshot.getJSONObject("deliveryByObservationType")
        val clock = byType.getJSONObject("GNSS_CLOCK")
        val raw = byType.getJSONObject("GNSS_RAW_MEASUREMENT")
        assertEquals(1L, clock.getLong("attempted"))
        assertEquals(1L, clock.getLong("accepted"))
        assertEquals(0L, clock.getLong("rejected"))
        assertEquals(3L, raw.getLong("attempted"))
        assertEquals(1L, raw.getLong("accepted"))
        assertEquals(2L, raw.getLong("rejected"))
        assertEquals(1L, raw.getLong("channelFull"))
        assertEquals(1L, raw.getLong("channelClosed"))
        assertTrue(snapshot.getJSONObject("callbackCounts").getLong("gnssMeasurements") == 1L)
        assertEquals(3L, snapshot.getJSONObject("callbackCounts").getLong("gnssMeasurementRows"))
        assertTrue(snapshot.getBoolean("invariantsHold"))
    }

    @Test
    fun classifiesClosedSeparatelyFromCapacityFailure() {
        assertEquals(
            GnssDeliveryDiagnostics.FailureReason.CHANNEL_CLOSED,
            GnssDeliveryDiagnostics.classifyFailure(isClosed = true)
        )
        assertEquals(
            GnssDeliveryDiagnostics.FailureReason.CHANNEL_FULL,
            GnssDeliveryDiagnostics.classifyFailure(isClosed = false)
        )
    }

    @Test
    fun perSessionResetClearsCountersAndRestoresRecordingState() {
        val diagnostics = GnssDeliveryDiagnostics()
        diagnostics.recordDelivery(ObservationType.GNSS_STATUS, success = true)
        diagnostics.finalizeSession()
        diagnostics.resetForSession()
        val snapshot = diagnostics.snapshot()
        val status = snapshot.getJSONObject("deliveryByObservationType").getJSONObject("GNSS_STATUS")
        assertEquals("RECORDING", snapshot.getString("state"))
        assertFalse(snapshot.getBoolean("countsComplete"))
        assertEquals(0L, status.getLong("attempted"))
        assertEquals(0L, status.getLong("accepted"))
        assertTrue(snapshot.getBoolean("invariantsHold"))
    }

    @Test
    fun finalizationIsIdempotentAndSnapshotRoundTrips() {
        val diagnostics = GnssDeliveryDiagnostics()
        diagnostics.recordDelivery(ObservationType.GNSS_STATUS, success = true)
        diagnostics.finalizeSession()
        diagnostics.finalizeSession("CRASH_CLOSED")
        val stored = diagnostics.snapshot().toString()
        val readBack = JSONObject(stored)
        assertEquals("FINALIZED", readBack.getString("state"))
        assertTrue(readBack.getBoolean("countsComplete"))
        assertTrue(readBack.getBoolean("invariantsHold"))
        assertTrue(readBack.getJSONObject("deliveryByObservationType")
            .getJSONObject("GNSS_STATUS").getBoolean("invariantHolds"))
    }

    @Test
    fun finalizedAcceptedCountsMustMatchPersistedRows() {
        val diagnostics = JSONObject()
            .put("state", "FINALIZED")
            .put("deliveryByObservationType", JSONObject()
                .put("GNSS_RAW_MEASUREMENT", JSONObject().put("attempted", 2).put("accepted", 2).put("rejected", 0))
                .put("GNSS_STATUS", JSONObject().put("attempted", 1).put("accepted", 1).put("rejected", 0)))

        val mismatches = reconcileAcceptedDeliveriesWithPersistedRows(
            diagnostics,
            mapOf("GNSS_RAW_MEASUREMENT" to 1, "GNSS_STATUS" to 1)
        )

        assertEquals(listOf("GNSS_RAW_MEASUREMENT accepted=2 persisted=1"), mismatches)
    }

    @Test
    fun incompleteDiagnosticsDoNotClaimPersistenceReconciliation() {
        val diagnostics = JSONObject()
            .put("state", "FINALIZATION_INCOMPLETE")
            .put("deliveryByObservationType", JSONObject()
                .put("GNSS_STATUS", JSONObject().put("attempted", 1).put("accepted", 1).put("rejected", 0)))
        assertTrue(reconcileAcceptedDeliveriesWithPersistedRows(diagnostics, emptyMap()).isEmpty())
    }
}
