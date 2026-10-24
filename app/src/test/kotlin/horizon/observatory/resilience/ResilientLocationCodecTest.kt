package horizon.observatory.resilience

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** G1: snapshot serialization. */
class ResilientLocationCodecTest {
    private fun snap(
        alt: Double? = null, vert: Double? = null, kind: ResilientFixKind? = ResilientFixKind.OBSERVED_GNSS,
        speed: Double? = null, bearing: Double? = null, utc: Long? = 1_700_000_000_000L, estimator: String? = null,
        lat: Double = 50.0, lon: Double = 8.0, hu: Double = 4.0, ts: Long = 123_000_000_000L
    ) = ResilientLocationSnapshot(lat, lon, alt, hu, vert, ts, NavigationState.FULL_GNSS, "GNSS_OBSERVED", kind, utc, speed, bearing, estimator)

    private fun roundTrip(s: ResilientLocationSnapshot): SnapshotDecodeResult = ResilientLocationCodec.decode(ResilientLocationCodec.encode(s))

    // 1-4 + 10: every optional-field combination survives publish -> read
    @Test fun roundTrip_horizontalFixOnly_altitudeNull_verticalNull() {
        val s = snap()
        assertEquals(SnapshotDecodeResult.Valid(s), roundTrip(s))
    }
    @Test fun roundTrip_altitudePresent_verticalNull() {
        val s = snap(alt = 123.4)
        val r = roundTrip(s) as SnapshotDecodeResult.Valid
        assertEquals(123.4, r.snapshot.altitudeM!!, 0.0); assertNull(r.snapshot.verticalUncertaintyM)
    }
    @Test fun roundTrip_altitudeNull_verticalPresent() {
        val r = roundTrip(snap(vert = 6.5)) as SnapshotDecodeResult.Valid
        assertNull(r.snapshot.altitudeM); assertEquals(6.5, r.snapshot.verticalUncertaintyM!!, 0.0)
    }
    @Test fun roundTrip_bothPresent_and_allOptionalFields() {
        val s = snap(alt = 10.0, vert = 3.0, speed = 12.5, bearing = 359.5, estimator = null)
        assertEquals(SnapshotDecodeResult.Valid(s), roundTrip(s))
    }
    @Test fun roundTrip_estimateKind_keepsEstimatorAndProvenance() {
        val s = snap(kind = ResilientFixKind.SHORT_HORIZON_ESTIMATE, estimator = CoastingPolicy.ESTIMATOR_ID, speed = 10.0, bearing = 90.0)
        assertEquals(SnapshotDecodeResult.Valid(s), roundTrip(s))
    }

    @Test fun nullableCoordinatesAndUncertaintyRemainExplicitNullAcrossRoundTrip() {
        val s = snap(alt = null, vert = null, speed = null, bearing = null, utc = null)
        val encoded = ResilientLocationCodec.encode(s)
        val json = JSONObject(encoded)
        for (key in listOf("altitudeM", "verticalUncertaintyM", "speedMps", "bearingDeg", "utcTimestampMs", "estimator")) {
            assertTrue("missing $key", json.has(key))
            assertTrue("$key must be JSON null", json.isNull(key))
        }
        assertEquals(SnapshotDecodeResult.Valid(s), ResilientLocationCodec.decode(encoded))
    }
    @Test fun regression_nullOptionalsAreExplicitJsonNull_notDeletedKeys() {
        val j = JSONObject(ResilientLocationCodec.encode(snap()))
        for (k in listOf("altitudeM", "verticalUncertaintyM", "speedMps", "bearingDeg", "estimator")) {
            assertTrue("key $k must exist", j.has(k)); assertTrue("key $k must be JSON null", j.isNull(k))
        }
        assertEquals(ResilientLocationCodec.SCHEMA_VERSION, j.getInt("schemaVersion"))
    }
    @Test fun missingValuesAreNeverZero() {
        val r = roundTrip(snap()) as SnapshotDecodeResult.Valid
        assertNull(r.snapshot.altitudeM); assertNull(r.snapshot.verticalUncertaintyM); assertNull(r.snapshot.speedMps); assertNull(r.snapshot.bearingDeg)
    }

    // 5: invalid numbers are refused at publish time and by the decoder
    @Test fun encode_rejects_nonFiniteAndOutOfRange() {
        val bad = listOf(
            snap(lat = Double.NaN), snap(lat = Double.POSITIVE_INFINITY), snap(lat = 90.0001), snap(lat = -90.0001),
            snap(lon = Double.NaN), snap(lon = 180.0001), snap(lon = -180.0001),
            snap(hu = -0.1), snap(hu = Double.POSITIVE_INFINITY), snap(hu = Double.NaN),
            snap(alt = Double.NaN), snap(vert = -1.0), snap(vert = Double.POSITIVE_INFINITY),
            snap(speed = -0.1), snap(bearing = 360.0), snap(bearing = -1.0), snap(ts = 0L), snap(ts = -5L), snap(utc = 0L),
            snap(kind = ResilientFixKind.SHORT_HORIZON_ESTIMATE, estimator = null)
        )
        for (s in bad) {
            assertNotNull("expected a problem for $s", ResilientLocationValidator.problem(s))
            try { ResilientLocationCodec.encode(s); fail("encode must reject $s") } catch (e: IllegalArgumentException) { /* expected */ }
        }
    }
    @Test fun boundaryValuesAreAccepted() {
        for (s in listOf(snap(lat = 90.0, lon = 180.0), snap(lat = -90.0, lon = -180.0), snap(hu = 0.0), snap(bearing = 0.0), snap(speed = 0.0))) {
            assertNull(ResilientLocationValidator.problem(s)); assertTrue(roundTrip(s) is SnapshotDecodeResult.Valid)
        }
    }
    @Test fun decode_rejects_invalidNumbersInStoredJson() {
        val base = JSONObject(ResilientLocationCodec.encode(snap()))
        fun with(key: String, value: Any?): String = JSONObject(base.toString()).put(key, value ?: JSONObject.NULL).toString()
        assertCorrupt(with("latitudeDeg", 91.0)); assertCorrupt(with("longitudeDeg", -181.0)); assertCorrupt(with("horizontalUncertaintyM", -2.0))
        assertCorrupt(with("verticalUncertaintyM", -2.0)); assertCorrupt(with("latitudeDeg", "50.0")); assertCorrupt(with("altitudeM", "abc"))
        assertCorrupt(with("timestampMonotonicNs", 1.5)); assertCorrupt(with("timestampMonotonicNs", "123")); assertCorrupt(with("latitudeDeg", null))
        assertCorrupt("{\"latitudeDeg\":1e999}".replace("{", "{\"schemaVersion\":2,\"navigationState\":\"FULL_GNSS\",\"provenance\":\"x\",\"longitudeDeg\":1,\"horizontalUncertaintyM\":1,\"timestampMonotonicNs\":5,"))
    }

    // 6: missing keys / explicit nulls
    @Test fun decode_acceptsMissingOptionalKeys_and_explicitNulls() {
        val minimal = """{"schemaVersion":2,"latitudeDeg":50.0,"longitudeDeg":8.0,"horizontalUncertaintyM":4.0,"timestampMonotonicNs":5000000000,"navigationState":"FULL_GNSS","provenance":"GNSS_OBSERVED"}"""
        val r = ResilientLocationCodec.decode(minimal) as SnapshotDecodeResult.Valid
        assertNull(r.snapshot.altitudeM); assertNull(r.snapshot.verticalUncertaintyM); assertNull(r.snapshot.fixKind); assertNull(r.snapshot.utcTimestampMs)
        val nulls = minimal.replace("\"provenance\"", "\"altitudeM\":null,\"verticalUncertaintyM\":null,\"fixKind\":null,\"estimator\":null,\"provenance\"")
        assertEquals(r, ResilientLocationCodec.decode(nulls))
    }
    @Test fun decode_missingRequiredKeys_isCorrupt_withReason() {
        val full = JSONObject(ResilientLocationCodec.encode(snap()))
        for (k in listOf("latitudeDeg", "longitudeDeg", "horizontalUncertaintyM", "timestampMonotonicNs", "navigationState", "provenance")) {
            val copy = JSONObject(full.toString()); copy.remove(k)
            val r = ResilientLocationCodec.decode(copy.toString())
            assertTrue("missing $k -> $r", r is SnapshotDecodeResult.Corrupt)
            assertEquals("MISSING_$k", (r as SnapshotDecodeResult.Corrupt).reason)
        }
    }
    @Test fun decode_legacyV1Payload_withoutSchemaVersion_isStillReadable() {
        val legacy = """{"latitudeDeg":50.0,"longitudeDeg":8.0,"altitudeM":100.0,"horizontalUncertaintyM":4.0,"verticalUncertaintyM":7.0,"timestampMonotonicNs":123,"navigationState":"GNSS_LOST","provenance":"DEAD_RECKONED_FROM_LAST_TRUSTED_PVT"}"""
        val r = ResilientLocationCodec.decode(legacy) as SnapshotDecodeResult.Valid
        assertEquals(7.0, r.snapshot.verticalUncertaintyM!!, 0.0); assertNull(r.snapshot.fixKind)
    }

    // 7: malformed / truncated
    @Test fun decode_malformedAndTruncatedJson_isControlledCorrupt_notAnException() {
        val good = ResilientLocationCodec.encode(snap(alt = 1.0, vert = 2.0))
        for (raw in listOf("", " ", "not json", "{", "[1,2,3]", "null", good.substring(0, good.length / 2), good.dropLast(1), "{\"a\":")) {
            val r = ResilientLocationCodec.decode(raw)
            assertTrue("raw='$raw' -> $r", r is SnapshotDecodeResult.Corrupt)
        }
        assertEquals(SnapshotDecodeResult.Absent, ResilientLocationCodec.decode(null))
    }

    // 8: unknown enum / state / schema
    @Test fun decode_unknownEnumValues_areCorrupt() {
        val base = JSONObject(ResilientLocationCodec.encode(snap()))
        assertEquals("UNKNOWN_navigationState", (ResilientLocationCodec.decode(JSONObject(base.toString()).put("navigationState", "WARP_DRIVE").toString()) as SnapshotDecodeResult.Corrupt).reason)
        assertEquals("UNKNOWN_fixKind", (ResilientLocationCodec.decode(JSONObject(base.toString()).put("fixKind", "ORACLE").toString()) as SnapshotDecodeResult.Corrupt).reason)
        assertEquals("UNSUPPORTED_SCHEMA_VERSION", (ResilientLocationCodec.decode(JSONObject(base.toString()).put("schemaVersion", 99).toString()) as SnapshotDecodeResult.Corrupt).reason)
        assertEquals("UNSUPPORTED_SCHEMA_VERSION", (ResilientLocationCodec.decode(JSONObject(base.toString()).put("schemaVersion", 0).toString()) as SnapshotDecodeResult.Corrupt).reason)
        assertTrue(ResilientLocationCodec.decode(JSONObject(base.toString()).put("navigationState", JSONObject.NULL).toString()) is SnapshotDecodeResult.Corrupt)
    }

    // 9: out-of-date snapshot and missing timestamps
    @Test fun missingOrInvalidTimestamps_areCorrupt() {
        val base = JSONObject(ResilientLocationCodec.encode(snap()))
        val noTs = JSONObject(base.toString()).also { it.remove("timestampMonotonicNs") }
        assertEquals("MISSING_timestampMonotonicNs", (ResilientLocationCodec.decode(noTs.toString()) as SnapshotDecodeResult.Corrupt).reason)
        assertCorrupt(JSONObject(base.toString()).put("timestampMonotonicNs", 0).toString())
        assertCorrupt(JSONObject(base.toString()).put("timestampMonotonicNs", -9).toString())
        assertCorrupt(JSONObject(base.toString()).put("utcTimestampMs", -1).toString())
    }
    @Test fun outOfDateSnapshot_isStale_notCurrent() {
        val s = snap(ts = 100 * S)
        val fresh = SnapshotFreshness.evaluate(SnapshotDecodeResult.Valid(s), 100 * S + 4_999_000_000L)
        val stale = SnapshotFreshness.evaluate(SnapshotDecodeResult.Valid(s), 100 * S + 5_001_000_000L)
        assertEquals(SnapshotStatus.FRESH, fresh.status); assertEquals(SnapshotStatus.STALE, stale.status); assertEquals("MAX_AGE_EXCEEDED", stale.reason)
        assertEquals(SnapshotStatus.NO_SNAPSHOT, SnapshotFreshness.evaluate(SnapshotDecodeResult.Absent, 1L).status)
        assertEquals(SnapshotStatus.CORRUPT, SnapshotFreshness.evaluate(SnapshotDecodeResult.Corrupt("X"), 1L).status)
    }

    private fun assertCorrupt(raw: String) {
        val r = ResilientLocationCodec.decode(raw)
        assertTrue("expected Corrupt for $raw but was $r", r is SnapshotDecodeResult.Corrupt)
    }
}
