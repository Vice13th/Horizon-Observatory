package horizon.observatory.astronomy

import horizon.observatory.astronomy.time.RetrievalTimeUtcMs
import horizon.observatory.astronomy.time.TimeBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitFreshnessTest {
    private val record = OmmParser.parse(
        """[{"NORAD_CAT_ID":1,"EPOCH":"2026-01-01T00:00:00","MEAN_MOTION":2.0,"ECCENTRICITY":0.01,"INCLINATION":55.0,
        "RA_OF_ASC_NODE":1.0,"ARG_OF_PERICENTER":1.0,"MEAN_ANOMALY":1.0,"BSTAR":0.0001}]"""
    ).records.single()
    private val epochMs = record.epochUtc.toEpochMilli()
    private val retrieved = epochMs + 3_600_000L
    private val orbit = CatalogedOrbit(record, OrbitProvenance("S", null, "OMM_JSON", RetrievalTimeUtcMs(retrieved)))
    private val policy = StalenessPolicy(maxDataAgeMs = 10_000L, maxEpochAgeMs = 7_200_000L)

    @Test
    fun `freshness carries epoch retrieval and raw ages`() {
        val now = retrieved + 5_000L
        val f = orbit.freshness(now)
        assertEquals(epochMs, f.catalogEpoch.value)
        assertEquals(retrieved, f.retrievedAt.value)
        assertEquals(5_000L, f.dataAgeMs)
        assertEquals(3_605_000L, f.epochAgeMs)
        assertEquals(TimeBasis.DEVICE_CLOCK_UTC, f.evaluationBasis)
    }

    @Test
    fun `reading later does not reset retrieval time`() {
        assertEquals(retrieved, orbit.freshness(retrieved + 99_000L).retrievedAt.value)
        assertEquals(99_000L, orbit.dataAgeMs(retrieved + 99_000L))
    }

    @Test
    fun `classification covers current stale and inconsistent`() {
        assertEquals(CatalogStaleness.CURRENT, orbit.freshness(retrieved + 1_000L).classify(policy))
        assertEquals(CatalogStaleness.STALE_RETRIEVAL, orbit.freshness(retrieved + 20_000L).classify(policy))
        assertEquals(CatalogStaleness.STALE_EPOCH, orbit.freshness(epochMs + 8_000_000L).classify(StalenessPolicy(10_000_000L, 7_200_000L)))
        assertEquals(CatalogStaleness.STALE_RETRIEVAL_AND_EPOCH, orbit.freshness(retrieved + 9_000_000L).classify(policy))
        val skew = orbit.freshness(retrieved - 1L)
        assertTrue(skew.dataAgeMs < 0)
        assertEquals(CatalogStaleness.CLOCK_INCONSISTENT, skew.classify(policy))
    }
}
