package horizon.observatory.astronomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmmParserTest {

    private fun record(
        norad: String = "99001",
        epoch: String = "2026-01-01T12:00:00.123456",
        extra: String = ""
    ) = """
        {"OBJECT_NAME":"TEST SAT","OBJECT_ID":"2020-001A","NORAD_CAT_ID":$norad,"CLASSIFICATION_TYPE":"U",
         "EPOCH":"$epoch","MEAN_MOTION":2.00561730,"ECCENTRICITY":0.0123,"INCLINATION":55.0,
         "RA_OF_ASC_NODE":120.5,"ARG_OF_PERICENTER":45.25,"MEAN_ANOMALY":300.75,
         "EPHEMERIS_TYPE":0,"ELEMENT_SET_NO":999,"REV_AT_EPOCH":1234,
         "BSTAR":0.00001,"MEAN_MOTION_DOT":0.00000012,"MEAN_MOTION_DDOT":0.0$extra}
    """.trimIndent()

    @Test
    fun `valid record preserves every field`() {
        val result = OmmParser.parse("[${record()}]")
        assertEquals(1, result.acceptedCount)
        assertEquals(0, result.skippedCount)
        assertTrue(result.errors.isEmpty())
        val r = result.records.single()
        assertEquals(99001L, r.noradCatId)
        assertEquals("TEST SAT", r.objectName)
        assertEquals("2020-001A", r.objectId)
        assertEquals("U", r.classification)
        assertEquals(2.00561730, r.meanMotionRevPerDay, 0.0)
        assertEquals(0.0123, r.eccentricity, 0.0)
        assertEquals(55.0, r.inclinationDeg, 0.0)
        assertEquals(120.5, r.raanDeg, 0.0)
        assertEquals(45.25, r.argOfPericenterDeg, 0.0)
        assertEquals(300.75, r.meanAnomalyDeg, 0.0)
        assertEquals(0, r.ephemerisType)
        assertEquals(999, r.elementSetNo)
        assertEquals(1234L, r.revAtEpoch)
        assertEquals(0.00001, r.bstar, 0.0)
        assertEquals(0.00000012, r.meanMotionDot!!, 0.0)
        assertEquals(0.0, r.meanMotionDdot!!, 0.0)
    }

    @Test
    fun `epoch text and microsecond precision are preserved`() {
        val r = OmmParser.parse("[${record(epoch = "2026-01-01T12:00:00.123456")}]").records.single()
        assertEquals("2026-01-01T12:00:00.123456", r.epochRaw)
        assertEquals(123_456_000, r.epochUtc.nano)
        assertEquals(java.time.Instant.parse("2026-01-01T12:00:00.123456Z"), r.epochUtc)
    }

    @Test
    fun `epoch with fewer fractional digits and with Z suffix still parses as UTC`() {
        assertNotNull(OmmEpoch.parse("2026-01-01T12:00:00"))
        assertNotNull(OmmEpoch.parse("2026-01-01T12:00:00.5"))
        assertEquals(java.time.Instant.parse("2026-01-01T12:00:00Z"), OmmEpoch.parse("2026-01-01T12:00:00Z"))
        assertNull(OmmEpoch.parse("not-a-date"))
    }

    @Test
    fun `BSTAR is required and never defaulted`() {
        val json = """[{"NORAD_CAT_ID":1,"EPOCH":"2026-01-01T00:00:00","MEAN_MOTION":2.0,"ECCENTRICITY":0.01,
            "INCLINATION":55.0,"RA_OF_ASC_NODE":1.0,"ARG_OF_PERICENTER":1.0,"MEAN_ANOMALY":1.0}]"""
        val result = OmmParser.parse(json)
        assertEquals(0, result.acceptedCount)
        assertEquals(1, result.skippedCount)
        assertTrue(result.errors.any { it.field == "BSTAR" })
    }

    @Test
    fun `missing required field is skipped and reported`() {
        val json = """[{"NORAD_CAT_ID":1,"EPOCH":"2026-01-01T00:00:00","ECCENTRICITY":0.01}]"""
        val result = OmmParser.parse(json)
        assertEquals(0, result.acceptedCount)
        assertEquals(1, result.skippedCount)
        assertTrue(result.errors.any { it.field == "MEAN_MOTION" })
    }

    @Test
    fun `malformed and out-of-range values are skipped not zeroed`() {
        val badEcc = record(norad = "5").replace("\"ECCENTRICITY\":0.0123", "\"ECCENTRICITY\":1.5")
        val badNum = record(norad = "6").replace("\"MEAN_MOTION\":2.00561730", "\"MEAN_MOTION\":\"abc\"")
        val badEpoch = record(norad = "7", epoch = "yesterday")
        val result = OmmParser.parse("[$badEcc,$badNum,$badEpoch]")
        assertEquals(0, result.acceptedCount)
        assertEquals(3, result.skippedCount)
        assertTrue(result.errors.any { it.field == "ECCENTRICITY" })
        assertTrue(result.errors.any { it.field == "MEAN_MOTION" })
        assertTrue(result.errors.any { it.field == "EPOCH" })
    }

    @Test
    fun `multiple records with one bad give accepted and skipped counts`() {
        val good1 = record(norad = "10")
        val good2 = record(norad = "11")
        val bad = """{"NORAD_CAT_ID":12}"""
        val result = OmmParser.parse("[$good1,$bad,$good2]")
        assertEquals(2, result.acceptedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(listOf(10L, 11L), result.records.map { it.noradCatId })
        assertEquals(1, result.errors.map { it.recordIndex }.distinct().size)
    }

    @Test
    fun `duplicate NORAD id keeps later epoch and counts the other as skipped`() {
        val older = record(norad = "20", epoch = "2026-01-01T00:00:00")
        val newer = record(norad = "20", epoch = "2026-01-02T00:00:00")
        val result = OmmParser.parse("[$newer,$older]")
        assertEquals(1, result.acceptedCount)
        assertEquals(1, result.skippedCount)
        assertEquals("2026-01-02T00:00:00", result.records.single().epochRaw)
        assertTrue(result.warnings.any { it.message.contains("duplicate") })
    }

    @Test
    fun `empty array is not a payload error and accepts nothing`() {
        val result = OmmParser.parse("[]")
        assertEquals(0, result.acceptedCount)
        assertEquals(0, result.skippedCount)
        assertFalse(result.payloadRejected)
    }

    @Test
    fun `non-json and non-array payloads are rejected as payload errors`() {
        assertTrue(OmmParser.parse("No GP data found").payloadRejected)
        assertTrue(OmmParser.parse("{\"a\":1}").payloadRejected)
        assertTrue(OmmParser.parse("").payloadRejected)
    }

    @Test
    fun `non-object array elements are skipped and reported`() {
        val result = OmmParser.parse("[1, null, ${record(norad = "30")}]")
        assertEquals(1, result.acceptedCount)
        assertEquals(2, result.skippedCount)
    }

    @Test
    fun `non-zero ephemeris type keeps the record with a warning`() {
        val json = "[${record(norad = "40").replace("\"EPHEMERIS_TYPE\":0", "\"EPHEMERIS_TYPE\":2")}]"
        val result = OmmParser.parse(json)
        assertEquals(1, result.acceptedCount)
        assertEquals(2, result.records.single().ephemerisType)
        assertTrue(result.warnings.any { it.field == "EPHEMERIS_TYPE" })
    }

    @Test
    fun `optional fields absent stay null and are not defaulted`() {
        val json = """[{"NORAD_CAT_ID":50,"EPOCH":"2026-01-01T00:00:00","MEAN_MOTION":2.0,"ECCENTRICITY":0.01,
            "INCLINATION":55.0,"RA_OF_ASC_NODE":1.0,"ARG_OF_PERICENTER":1.0,"MEAN_ANOMALY":1.0,"BSTAR":0.0001}]"""
        val r = OmmParser.parse(json).records.single()
        assertNull(r.objectName)
        assertNull(r.meanMotionDot)
        assertNull(r.meanMotionDdot)
        assertNull(r.elementSetNo)
        assertNull(r.revAtEpoch)
        assertNull(r.ephemerisType)
    }

    @Test
    fun `derived semi-major axis is a derived convenience only`() {
        val r = OmmParser.parse("[${record()}]").records.single()
        assertTrue(r.derivedSemiMajorAxisKmTwoBody > 6378.0)
    }
}
