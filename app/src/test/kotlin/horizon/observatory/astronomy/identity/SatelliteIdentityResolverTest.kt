package horizon.observatory.astronomy.identity

import horizon.observatory.astronomy.time.PropagationTime
import horizon.observatory.astronomy.time.TimeBasis
import horizon.observatory.domain.gnss.SatelliteCorrelationKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SatelliteIdentityResolverTest {
    private val gps13 = GnssSatelliteId(constellationType = 1, svid = 13)
    private val t = PropagationTime(1_000_000L, TimeBasis.DEVICE_CLOCK_UTC)

    private fun prov(
        confidence: MappingConfidence = MappingConfidence.VERIFIED,
        from: Long? = null,
        to: Long? = null
    ) = MappingProvenance("test-table", MappingMethod.OPERATOR_SUPPLIED_TABLE, confidence, "unit-test entry", from, to)

    @Test
    fun `empty table is UNMATCHED and exposes no NORAD id`() {
        val r = TableSatelliteIdentityResolver(emptyList()).resolve(gps13, t)
        assertTrue(r is SatelliteIdentityMapping.Unmatched)
        assertEquals(SatelliteMappingStatus.UNMATCHED, r.status)
    }

    @Test
    fun `verified single entry is MATCHED with provenance`() {
        val entry = SatelliteIdentityEntry(gps13, 4242L, prov())
        val r = TableSatelliteIdentityResolver(listOf(entry)).resolve(gps13, t)
        r as SatelliteIdentityMapping.Matched
        assertEquals(4242L, r.noradCatId)
        assertEquals(entry.provenance, r.provenance)
        assertEquals(SatelliteMappingStatus.MATCHED, r.status)
    }

    @Test
    fun `unverified single entry is UNVERIFIED not MATCHED`() {
        val entry = SatelliteIdentityEntry(gps13, 4242L, prov(confidence = MappingConfidence.UNVERIFIED))
        val r = TableSatelliteIdentityResolver(listOf(entry)).resolve(gps13, t)
        assertTrue(r is SatelliteIdentityMapping.Unverified)
        assertEquals(SatelliteMappingStatus.UNVERIFIED, r.status)
    }

    @Test
    fun `two different NORAD ids valid at the same time are AMBIGUOUS`() {
        val r = TableSatelliteIdentityResolver(
            listOf(SatelliteIdentityEntry(gps13, 1L, prov()), SatelliteIdentityEntry(gps13, 2L, prov()))
        ).resolve(gps13, t)
        r as SatelliteIdentityMapping.Ambiguous
        assertEquals(listOf(1L, 2L), r.candidateNoradCatIds)
    }

    @Test
    fun `temporal validity separates satellites that reused the same SVID`() {
        val resolver = TableSatelliteIdentityResolver(
            listOf(
                SatelliteIdentityEntry(gps13, 1L, prov(to = 2_000_000L)),
                SatelliteIdentityEntry(gps13, 2L, prov(from = 2_000_000L))
            )
        )
        assertEquals(1L, (resolver.resolve(gps13, PropagationTime(1_999_999L, TimeBasis.DEVICE_CLOCK_UTC)) as SatelliteIdentityMapping.Matched).noradCatId)
        assertEquals(2L, (resolver.resolve(gps13, PropagationTime(2_000_000L, TimeBasis.DEVICE_CLOCK_UTC)) as SatelliteIdentityMapping.Matched).noradCatId)
    }

    @Test
    fun `entry outside its validity window is UNMATCHED`() {
        val resolver = TableSatelliteIdentityResolver(listOf(SatelliteIdentityEntry(gps13, 1L, prov(from = 5_000_000L))))
        assertTrue(resolver.resolve(gps13, t) is SatelliteIdentityMapping.Unmatched)
    }

    @Test
    fun `different constellation with same SVID does not match`() {
        val resolver = TableSatelliteIdentityResolver(listOf(SatelliteIdentityEntry(gps13, 1L, prov())))
        assertTrue(resolver.resolve(GnssSatelliteId(constellationType = 6, svid = 13), t) is SatelliteIdentityMapping.Unmatched)
    }

    @Test
    fun `duplicate entries for the same NORAD with one verified resolve MATCHED`() {
        val resolver = TableSatelliteIdentityResolver(
            listOf(
                SatelliteIdentityEntry(gps13, 7L, prov(confidence = MappingConfidence.UNVERIFIED)),
                SatelliteIdentityEntry(gps13, 7L, prov(confidence = MappingConfidence.VERIFIED))
            )
        )
        assertTrue(resolver.resolve(gps13, t) is SatelliteIdentityMapping.Matched)
    }

    @Test
    fun `correlation key maps to satellite id without carrier frequency`() {
        val key = SatelliteCorrelationKey(constellationType = 1, svid = 13, carrierFrequencyHz = 1_575_420_000L)
        assertEquals(gps13, key.toSatelliteId())
    }
}
