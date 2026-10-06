package horizon.observatory.domain.assistance

import horizon.observatory.domain.model.ObservationType
import horizon.observatory.domain.model.TimestampDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistanceEvidenceRecorderTest {

    private fun evidence(
        monotonicNs: Long? = 100L,
        wallClockMs: Long? = null,
        provenance: AssistanceProvenance = AssistanceProvenance.OBSERVED
    ) = AssistanceEvidence(
        eventId = "e1",
        category = AssistanceCategory.NETWORK,
        type = AssistanceType.TIME,
        monotonicTimestampNs = monotonicNs,
        wallClockTimestampMs = wallClockMs,
        ingestionMonotonicTimestampNs = 100L,
        provenance = provenance
    )

    @Test
    fun `type is ASSISTANCE_EVENT not a GNSS type`() {
        val obs = AssistanceEvidenceRecorder.toRawObservation(evidence())
        assertEquals(ObservationType.ASSISTANCE_EVENT, obs.type)
        assertTrue(obs.type.name.startsWith("GNSS").not())
    }

    @Test
    fun `missing monotonic timestamp yields UNKNOWN timestamp domain, not fabricated`() {
        val obs = AssistanceEvidenceRecorder.toRawObservation(evidence(monotonicNs = null))
        assertEquals(TimestampDomain.UNKNOWN, obs.timestampDomain)
    }

    @Test
    fun `missing wall-clock falls back to real ingestion time, never epoch 0`() {
        val obs = AssistanceEvidenceRecorder.toRawObservation(evidence(wallClockMs = null), nowUtcMillis = 5_000_000L)
        assertEquals(5_000_000L, obs.utcTimestampMs)
    }

    @Test
    fun `provenance INFERRED never becomes OBSERVED`() {
        val obs = AssistanceEvidenceRecorder.toRawObservation(evidence(provenance = AssistanceProvenance.INFERRED))
        assertTrue(obs.payloadJson.contains("\"provenance\":\"INFERRED\""))
    }
}
