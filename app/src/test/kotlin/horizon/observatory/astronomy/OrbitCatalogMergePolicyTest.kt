package horizon.observatory.astronomy

import org.junit.Assert.assertEquals
import org.junit.Test

class OrbitCatalogMergePolicyTest {
    private fun rec(norad: Long, epoch: String) = OmmParser.parse(
        """[{"NORAD_CAT_ID":$norad,"EPOCH":"$epoch","MEAN_MOTION":2.0,"ECCENTRICITY":0.01,"INCLINATION":55.0,
        "RA_OF_ASC_NODE":1.0,"ARG_OF_PERICENTER":1.0,"MEAN_ANOMALY":1.0,"BSTAR":0.0001}]"""
    ).records.single()

    @Test
    fun `new object is written`() {
        val d = OrbitCatalogMergePolicy.decide(emptyMap(), listOf(rec(1, "2026-01-01T00:00:00")))
        assertEquals(1, d.toWrite.size)
        assertEquals(0, d.rejectedOlderEpoch.size)
    }

    @Test
    fun `older epoch never replaces a newer stored epoch`() {
        val newer = rec(1, "2026-01-02T00:00:00").epochUtc.toEpochMilli()
        val d = OrbitCatalogMergePolicy.decide(mapOf(1L to newer), listOf(rec(1, "2026-01-01T00:00:00")))
        assertEquals(0, d.toWrite.size)
        assertEquals(listOf(1L), d.rejectedOlderEpoch)
    }

    @Test
    fun `equal epoch is written so retrieval time can be updated`() {
        val same = rec(1, "2026-01-01T00:00:00")
        val d = OrbitCatalogMergePolicy.decide(mapOf(1L to same.epochUtc.toEpochMilli()), listOf(same))
        assertEquals(1, d.toWrite.size)
    }

    @Test
    fun `newer epoch replaces`() {
        val old = rec(1, "2026-01-01T00:00:00").epochUtc.toEpochMilli()
        val d = OrbitCatalogMergePolicy.decide(mapOf(1L to old), listOf(rec(1, "2026-01-02T00:00:00")))
        assertEquals(1, d.toWrite.size)
    }
}
