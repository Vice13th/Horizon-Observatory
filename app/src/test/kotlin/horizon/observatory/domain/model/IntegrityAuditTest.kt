package horizon.observatory.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrityAuditTest {
    @Test
    fun contiguousAndMonotonicSessionPasses() {
        val result = IntegrityAudit.audit(listOf(
            RawObservationView(1, 100),
            RawObservationView(2, 200),
            RawObservationView(3, 300)
        ))
        assertTrue(result.isClean)
    }

    @Test
    fun sequenceGapFails() {
        val result = IntegrityAudit.audit(listOf(
            RawObservationView(1, 100),
            RawObservationView(3, 300)
        ))
        assertFalse(result.sequenceContiguous)
        assertFalse(result.isClean)
    }

    @Test
    fun sourceTimestampRegressionDoesNotFailIngressIntegrity() {
        val result = IntegrityAudit.audit(listOf(
            RawObservationView(sequenceNumber = 1, sourceMonotonicTimestampNs = 300, ingestionMonotonicTimestampNs = 100),
            RawObservationView(sequenceNumber = 2, sourceMonotonicTimestampNs = 200, ingestionMonotonicTimestampNs = 200)
        ))
        assertTrue(result.ingestionTimestampsNonDecreasing)
        assertEquals(1, result.sourceTimestampRegressions)
        assertTrue(result.isClean)
    }

    @Test
    fun ingestionTimestampRegressionFailsIntegrity() {
        val result = IntegrityAudit.audit(listOf(
            RawObservationView(sequenceNumber = 1, sourceMonotonicTimestampNs = 100, ingestionMonotonicTimestampNs = 300),
            RawObservationView(sequenceNumber = 2, sourceMonotonicTimestampNs = 200, ingestionMonotonicTimestampNs = 200)
        ))
        assertFalse(result.ingestionTimestampsNonDecreasing)
        assertFalse(result.isClean)
    }
}
