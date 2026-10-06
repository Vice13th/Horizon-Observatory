package horizon.observatory.astronomy

import horizon.observatory.astronomy.time.RetrievalTimeUtcMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrbitCatalogMapperTest {
    private val record = OmmParser.parse(
        """[{"OBJECT_NAME":"X","NORAD_CAT_ID":77,"EPOCH":"2026-03-04T05:06:07.654321","MEAN_MOTION":2.0056,
        "ECCENTRICITY":0.01,"INCLINATION":55.0,"RA_OF_ASC_NODE":10.0,"ARG_OF_PERICENTER":20.0,"MEAN_ANOMALY":30.0,
        "BSTAR":0.0002,"EPHEMERIS_TYPE":0}]"""
    ).records.single()
    private val provenance = OrbitProvenance("CELESTRAK_GP", "https://example.invalid/gp", "OMM_JSON", RetrievalTimeUtcMs(1_700_000_000_000L))

    @Test
    fun `entity round trip preserves record, epoch text and provenance`() {
        val back = CatalogedOrbit(record, provenance).toEntity().toCatalogedOrbit()
        assertEquals(record, back.record)
        assertEquals(provenance, back.provenance)
        assertEquals("2026-03-04T05:06:07.654321", back.record.epochRaw)
        assertEquals(654_321_000, back.record.epochUtc.nano)
    }

    @Test
    fun `optional fields stay null through the entity`() {
        val back = CatalogedOrbit(record, provenance).toEntity().toCatalogedOrbit()
        assertNull(back.record.meanMotionDot)
        assertNull(back.record.revAtEpoch)
    }

    @Test
    fun `retrieval time and source survive the entity to domain boundary`() {
        val entity = CatalogedOrbit(record, provenance).toEntity()
        assertEquals(1_700_000_000_000L, entity.retrievedAtUtcMillis)
        assertEquals("CELESTRAK_GP", entity.catalogSource)
        val domain = entity.toCatalogedOrbit()
        assertEquals(1_700_000_000_000L, domain.provenance.retrievedAt.value)
        assertEquals(record.epochUtc.toEpochMilli(), domain.catalogEpoch.value)
    }

    @Test
    fun `unparseable stored epoch text falls back to stored millis without inventing data`() {
        val entity = CatalogedOrbit(record, provenance).toEntity().copy(epochRaw = "corrupt")
        val back = entity.toCatalogedOrbit()
        assertEquals(entity.epochUtcMillis, back.record.epochUtc.toEpochMilli())
    }
}
