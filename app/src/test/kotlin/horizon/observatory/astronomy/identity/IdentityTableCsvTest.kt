package horizon.observatory.astronomy.identity

import horizon.observatory.astronomy.time.PropagationTime
import horizon.observatory.astronomy.time.TimeBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityTableCsvTest {
    private val h = IdentityTableCsv.HEADER
    private fun at(ms: Long) = PropagationTime(ms, TimeBasis.DEVICE_CLOCK_UTC)

    @Test fun parsesValidRowsAndSkipsComments() {
        val r = IdentityTableCsv.parse("# note\n$h\n1,13,99001,\"Test, table\",OPERATOR_SUPPLIED_TABLE,VERIFIED,doc-1,1000,2000\n")
        assertEquals(1, r.entries.size)
        assertTrue(r.issues.isEmpty())
        assertEquals("Test, table", r.entries[0].provenance.source)
        assertEquals(1000L, r.entries[0].provenance.validFromUtcMs)
    }

    @Test fun wrongHeaderRejectsEverything() {
        val r = IdentityTableCsv.parse("a,b,c\n1,2,3\n")
        assertTrue(r.entries.isEmpty()); assertEquals(1, r.issues.size)
    }

    @Test fun invalidRowsAreSkippedNeverRepaired() {
        val csv = listOf(
            h,
            "9,13,1,s,OPERATOR_SUPPLIED_TABLE,VERIFIED,e,,",        // constellation out of range
            "1,13,1,s,NAME_PATTERN_INFERRED,VERIFIED,e,,",          // inferred can't be verified
            "1,13,1,s,OPERATOR_SUPPLIED_TABLE,VERIFIED,,,",         // evidence missing
            "1,13,1,s,OPERATOR_SUPPLIED_TABLE,VERIFIED,e,5,5",      // empty window
            "1,13,1,s,OPERATOR_SUPPLIED_TABLE,VERIFIED,e,x,",       // bad number
            "1,13,1,s,OPERATOR_SUPPLIED_TABLE,MAYBE,e,,",           // bad confidence
            "1,13,1,s,GEOMETRIC_CONSISTENCY_HINT,VERIFIED,e,,",     // a hint can never be VERIFIED
            "1,13,1,s,NAME_PATTERN_INFERRED,UNVERIFIED,e,,"         // ok (hint only)
        ).joinToString("\n")
        val r = IdentityTableCsv.parse(csv)
        assertEquals(8, r.dataRows)
        assertEquals(1, r.entries.size)
        assertEquals(7, r.issues.size)
    }

    @Test fun resolverFollowsValidityWindowAndAmbiguity() {
        val csv = "$h\n1,13,100,s,OPERATOR_SUPPLIED_TABLE,VERIFIED,e,1000,2000\n1,13,200,s,OPERATOR_SUPPLIED_TABLE,VERIFIED,e,1500,3000\n"
        val res = ReloadableIdentityResolver()
        val id = GnssSatelliteId(1, 13)
        assertTrue(res.resolve(id, at(1200)) is SatelliteIdentityMapping.Unmatched)   // empty before replace
        res.replace(IdentityTableCsv.parse(csv).entries)
        assertEquals(2, res.entryCount)
        assertTrue(res.resolve(id, at(1200)) is SatelliteIdentityMapping.Matched)
        assertTrue(res.resolve(id, at(1700)) is SatelliteIdentityMapping.Ambiguous)
        assertTrue(res.resolve(id, at(2500)) is SatelliteIdentityMapping.Matched)
        assertTrue(res.resolve(id, at(5000)) is SatelliteIdentityMapping.Unmatched)
    }

    @Test fun readBoundedAcceptsSmallAndRejectsOversizedInput() {
        assertEquals("abc", IdentityTableCsv.readBounded("abc".byteInputStream()))
        val big = java.io.ByteArrayInputStream(ByteArray(IdentityTableCsv.MAX_BYTES + 1) { 'a'.code.toByte() })
        val failed = runCatching { IdentityTableCsv.readBounded(big) }.exceptionOrNull()
        assertTrue(failed is IllegalStateException)
    }
}
