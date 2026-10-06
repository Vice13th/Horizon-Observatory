package horizon.observatory.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EvidenceMetadataTest {
    @Test
    fun rawObservationDefaultsToMeasuredRawAcquisitionContract() {
        val observation = RawObservation(
            type = ObservationType.GNSS_FIX,
            utcTimestampMs = 10L,
            provider = "test",
            payloadJson = "{}"
        )
        assertEquals(CapabilityState.UNKNOWN, observation.capabilityState)
        assertEquals(EvidenceStatus.OBSERVED, observation.evidenceStatus)
        assertEquals(ObservationProvenance.RAW_ACQUISITION, observation.provenance)
    }
}
