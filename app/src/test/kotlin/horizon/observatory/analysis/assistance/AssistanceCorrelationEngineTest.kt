package horizon.observatory.analysis.assistance

import horizon.observatory.storage.entity.ObservationEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistanceCorrelationEngineTest {
    private fun row(id: Long, type: String, monoNs: Long?, sessionId: String = "s1") = ObservationEntity(
        observationId = id, sessionId = sessionId, sequenceNumber = id, timestampUtcMs = 0L,
        monotonicTimestampNs = monoNs, ingestionMonotonicTimestampNs = monoNs,
        source = "GNSS", technology = "GNSS", type = type, provider = type, rawPayloadJson = "{}"
    )

    @Test
    fun `assistance before first PVT within window is BEFORE_FIRST_PVT`() {
        val gnss = listOf(row(1, "GNSS_FIX", 10_000_000_000L))
        val assistance = listOf(row(2, "ASSISTANCE_EVENT", 9_000_000_000L))
        val result = AssistanceCorrelationEngine(correlationWindowNs = 5_000_000_000L).correlate(assistance, gnss)
        assertEquals(AssistanceTemporalRelationship.BEFORE_FIRST_PVT, result.single().relationship)
    }

    @Test
    fun `assistance far outside window is NO_TEMPORAL_OVERLAP`() {
        val gnss = listOf(row(1, "GNSS_FIX", 10_000_000_000L))
        val assistance = listOf(row(2, "ASSISTANCE_EVENT", 1_000_000_000L))
        val result = AssistanceCorrelationEngine(correlationWindowNs = 2_000_000_000L).correlate(assistance, gnss)
        assertEquals(AssistanceTemporalRelationship.NO_TEMPORAL_OVERLAP, result.single().relationship)
    }

    @Test
    fun `missing assistance timestamp is UNKNOWN not defaulted`() {
        val gnss = listOf(row(1, "GNSS_FIX", 10_000_000_000L))
        val assistance = listOf(row(2, "ASSISTANCE_EVENT", null))
        val result = AssistanceCorrelationEngine().correlate(assistance, gnss)
        assertEquals(AssistanceTemporalRelationship.UNKNOWN, result.single().relationship)
    }

    @Test
    fun `overlapping GNSS window is OVERLAPS_ACQUISITION`() {
        val gnss = listOf(row(1, "GNSS_FIX", 10_000_000_000L), row(2, "GNSS_RAW_MEASUREMENT", 12_000_000_000L))
        val assistance = listOf(row(3, "ASSISTANCE_EVENT", 11_000_000_000L))
        val result = AssistanceCorrelationEngine().correlate(assistance, gnss)
        assertEquals(AssistanceTemporalRelationship.OVERLAPS_ACQUISITION, result.single().relationship)
    }

    @Test
    fun `session with no assistance events returns empty list, not an error`() {
        val gnss = listOf(row(1, "GNSS_FIX", 10_000_000_000L))
        val result = AssistanceCorrelationEngine().correlate(emptyList(), gnss)
        assertEquals(0, result.size)
    }
}
