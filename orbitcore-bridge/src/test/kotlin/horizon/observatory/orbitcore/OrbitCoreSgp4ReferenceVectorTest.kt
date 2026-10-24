package horizon.observatory.orbitcore

import horizon.observatory.astronomy.propagation.BackendResult
import horizon.observatory.astronomy.propagation.Sgp4Sdp4Input
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Conformance of the REAL OrbitCore SGP4/SDP4, driven through the production adapter [OrbitCoreSgp4Sdp4Backend],
 * against the published Vallado verification set (AIAA 2006-6753): `SGP4-VER.TLE` (inputs) and `tcppver.out`
 * (reference TEME states in km and km/s, WGS-72). Data files are byte-identical copies, see `sgp4/PROVENANCE.md`.
 *
 * Scope: position/velocity agreement at every tabulated reference time, near-earth and deep-space, negative
 * tsince included. Not covered: TEME->ECEF/topocentric frames and propagation beyond the reference grid.
 * Evidence and root-cause analysis: docs/verification/RECEIPT_2026-10-16_JVM_HARNESS_AND_SGP4.md.
 */
class OrbitCoreSgp4ReferenceVectorTest {

    private class Case(val satnum: Int, val input: Sgp4Sdp4Input)
    private class Ref(val tsinceMin: Double, val pos: DoubleArray, val vel: DoubleArray)
    private class Row(val satnum: Int, val tsinceMin: Double, val dPosKm: Double, val dVelKmS: Double, val tolPosKm: Double, val tolVelKmS: Double) {
        val inside: Boolean get() = dPosKm <= tolPosKm && dVelKmS <= tolVelKmS
    }

    private fun fmt(format: String, vararg args: Any?): String = String.format(Locale.ROOT, format, *args)

    private fun resourceBytes(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/sgp4/$name")) { "missing test resource /sgp4/$name" }.use { it.readBytes() }

    private fun lines(name: String): List<String> = String(resourceBytes(name), Charsets.US_ASCII).lines()

    /** TLE "assumed decimal point" field, e.g. " 28098-4" means 0.28098e-4. */
    private fun implied(field: String): Double {
        val s = field.trim()
        val sign = if (s.startsWith("-")) -1.0 else 1.0
        val body = s.trimStart('+', '-')
        val exp = body.substring(body.length - 2).toInt()
        return sign * ("0." + body.substring(0, body.length - 2)).toDouble() * 10.0.pow(exp)
    }

    private fun parseTle(l1: String, l2: String): Case {
        require(l1.startsWith("1 ") && l2.startsWith("2 ")) { "malformed TLE pair: $l1 / $l2" }
        val satnum = l1.substring(2, 7).trim().toInt()
        check(l2.substring(2, 7).trim().toInt() == satnum) { "satnum mismatch between TLE lines for $satnum" }
        val yy = l1.substring(18, 20).toInt()
        val year = if (yy < 57) 2000 + yy else 1900 + yy
        val dayOfYear = l1.substring(20, 32).trim().toDouble()
        // Locale.ROOT: default-locale digits (e.g. fa/ar) would break Instant.parse.
        val epoch = Instant.parse(fmt("%04d-01-01T00:00:00Z", year)).plusNanos(Math.round((dayOfYear - 1.0) * 86_400e9))
        val nRadPerMin = l2.substring(52, 63).trim().toDouble() * 2.0 * Math.PI / 1440.0
        return Case(
            satnum,
            Sgp4Sdp4Input(
                noradCatId = satnum.toLong(),
                objectName = "SGP4-VER $satnum",
                objectId = l1.substring(9, 17).trim(),
                classification = l1.substring(7, 8),
                epochUtc = epoch,
                epochRaw = l1.substring(18, 32).trim(),
                meanMotionRadPerMin = nRadPerMin,
                eccentricity = ("0." + l2.substring(26, 33).trim()).toDouble(),
                inclinationRad = Math.toRadians(l2.substring(8, 16).trim().toDouble()),
                raanRad = Math.toRadians(l2.substring(17, 25).trim().toDouble()),
                argOfPericenterRad = Math.toRadians(l2.substring(34, 42).trim().toDouble()),
                meanAnomalyRad = Math.toRadians(l2.substring(43, 51).trim().toDouble()),
                bstar = implied(l1.substring(53, 61)),
                meanMotionDotRevPerDay2 = l1.substring(33, 43).trim().toDouble(),
                meanMotionDdotRevPerDay3 = implied(l1.substring(44, 52)),
                ephemerisType = l1.substring(62, 63).trim().ifEmpty { "0" }.toInt(), // blank column = 0 by TLE convention
                elementSetNo = l1.substring(64, 68).trim().toInt(),
                revAtEpoch = l2.substring(63, 68).trim().toLong(),
            ),
        )
    }

    private fun parseCases(): List<Case> {
        val ls = lines("SGP4-VER.TLE").filter { it.isNotBlank() && !it.startsWith("#") }
        check(ls.size % 2 == 0) { "odd number of TLE lines" }
        return ls.chunked(2).map { (l1, l2) -> parseTle(l1, l2) }
    }

    private fun parseRefs(): List<Pair<Int, List<Ref>>> {
        val out = ArrayList<Pair<Int, MutableList<Ref>>>()
        for ((idx, raw) in lines("tcppver.out").withIndex()) {
            if (raw.isBlank()) continue
            val tok = raw.trim().split(Regex("\\s+"))
            if (tok.size == 2 && tok[1] == "xx") { out += tok[0].toInt() to ArrayList(); continue }
            check(out.isNotEmpty() && tok.size >= 7) { "unparseable reference line ${idx + 1}: $raw" }
            val v = tok.take(7).map { it.toDouble() }
            out.last().second += Ref(v[0], doubleArrayOf(v[1], v[2], v[3]), doubleArrayOf(v[4], v[5], v[6]))
        }
        return out
    }

    private class Evaluation(val rows: List<Row>, val notOk: List<Triple<Int, Double, BackendResult>>)

    private fun evaluate(): Evaluation {
        val cases = parseCases()
        val refs = parseRefs()
        assertEquals("every TLE needs one reference block", cases.size, refs.size)
        val backend = OrbitCoreSgp4Sdp4Backend()
        val rows = ArrayList<Row>()
        val notOk = ArrayList<Triple<Int, Double, BackendResult>>()
        for ((case, ref) in cases.zip(refs)) {
            assertEquals("reference block order must follow the TLE file", case.satnum, ref.first)
            assertTrue("sat ${case.satnum} has no reference rows", ref.second.isNotEmpty())
            for (r in ref.second) {
                val res = backend.propagate(case.input, r.tsinceMin)
                if (res !is BackendResult.Ok) { notOk += Triple(case.satnum, r.tsinceMin, res); continue }
                val s = res.state
                val dPos = sqrt((s.xKm - r.pos[0]).pow(2) + (s.yKm - r.pos[1]).pow(2) + (s.zKm - r.pos[2]).pow(2))
                val dVel = sqrt((s.vxKmS - r.vel[0]).pow(2) + (s.vyKmS - r.vel[1]).pow(2) + (s.vzKmS - r.vel[2]).pow(2))
                val speed = sqrt(r.vel[0].pow(2) + r.vel[1].pow(2) + r.vel[2].pow(2))
                val radius = sqrt(r.pos[0].pow(2) + r.pos[1].pow(2) + r.pos[2].pow(2))
                rows += Row(
                    case.satnum, r.tsinceMin, dPos, dVel,
                    tolPosKm = REF_FLOOR_POS_KM + speed * ADAPTER_TIME_QUANTUM_S,
                    tolVelKmS = REF_FLOOR_VEL_KMS + MU_KM3_S2 / (radius * radius) * ADAPTER_TIME_QUANTUM_S,
                )
            }
        }
        return Evaluation(rows, notOk)
    }

    @Test
    fun `reference files are byte-identical to the recorded provenance hashes`() {
        val prov = String(resourceBytes("PROVENANCE.md"), Charsets.UTF_8).lines()
        for (name in listOf("SGP4-VER.TLE", "tcppver.out")) {
            val recorded = prov.single { it.startsWith("sha256 ") && it.endsWith(" $name") }.split(" ")[1]
            val actual = MessageDigest.getInstance("SHA-256").digest(resourceBytes(name)).joinToString("") { fmt("%02x", it) }
            assertEquals("$name was modified; raw reference data is immutable", recorded, actual)
        }
    }

    @Test
    fun `bridge matches Vallado reference within the adapter time bound except the pinned cases`() {
        val ev = evaluate()
        val checked = ev.rows.filter { it.satnum != PINNED_DEVIATION_SAT }
        println(fmt(
            "[VALLADO] compared rows=%d sats=%d worst|dPos|=%.3e km worst|dVel|=%.3e km/s (bound per row: %.0e km + |v|*%.1e s)",
            checked.size, checked.map { it.satnum }.toSet().size, checked.maxOf { it.dPosKm }, checked.maxOf { it.dVelKmS },
            REF_FLOOR_POS_KM, ADAPTER_TIME_QUANTUM_S,
        ))
        val unexpected = ev.notOk.filter { it.first != EXPECTED_ERROR_SAT }
        assertTrue("backend returned errors: $unexpected", unexpected.isEmpty())
        val outside = checked.filterNot { it.inside }
        assertTrue(
            "rows outside bound (first 15 of ${outside.size}):\n" + outside.take(15).joinToString("\n") {
                fmt("sat %d t=%.4f dPos=%.3e (tol %.3e) dVel=%.3e (tol %.3e)", it.satnum, it.tsinceMin, it.dPosKm, it.tolPosKm, it.dVelKmS, it.tolVelKmS)
            },
            outside.isEmpty(),
        )
    }

    /**
     * Canary, not an endorsement: OrbitCore 0.1.0 deviates ~0.96 km on this low-inclination deep-space case once the
     * secular node goes negative (floored Kotlin `mod` instead of C `fmod`, see receipt). The test fails when the
     * deviation disappears so that the pin and the receipt get removed, and when it grows.
     */
    @Test
    fun `known OrbitCore deviation on sat 23599 is pinned`() {
        val rows = evaluate().rows.filter { it.satnum == PINNED_DEVIATION_SAT }
        assertTrue("no rows for sat $PINNED_DEVIATION_SAT", rows.isNotEmpty())
        assertTrue("rows up to t=400 min must still agree with the reference", rows.filter { it.tsinceMin <= 400.0 }.all { it.inside })
        val worstLate = rows.filter { it.tsinceMin >= 420.0 }.maxOf { it.dPosKm }
        assertTrue(
            fmt("KNOWN DEVIATION CHANGED: worst |dPos| for t>=420 min is %.3e km, pinned range is 0.5..1.5 km. If it fell to ~1e-6 km OrbitCore was fixed: delete this pin and update the SGP4 receipt.", worstLate),
            worstLate in 0.5..1.5,
        )
    }

    @Test
    fun `sat 33334 is an error case and its reference row is a stale copy of the previous block`() {
        val cases = parseCases()
        val refs = parseRefs()
        val idx = cases.indexOfFirst { it.satnum == EXPECTED_ERROR_SAT }
        assertTrue("sat $EXPECTED_ERROR_SAT not found", idx > 0)
        val ref = refs[idx].second.single()
        val previous = refs[idx - 1].second.last()
        assertArrayEquals(previous.pos, ref.pos, 0.0)
        assertArrayEquals(previous.vel, ref.vel, 0.0)
        val res = OrbitCoreSgp4Sdp4Backend().propagate(cases[idx].input, ref.tsinceMin)
        assertTrue("SGP4 must reject sat $EXPECTED_ERROR_SAT, got $res", res is BackendResult.Error)
    }

    private companion object {
        const val PINNED_DEVIATION_SAT = 23599
        const val EXPECTED_ERROR_SAT = 33334
        // The reference prints 1e-8 km / 1e-9 km/s; independent python-sgp4 2.27 agrees with it to <= 1.2e-7 km.
        const val REF_FLOOR_POS_KM = 1e-6
        const val REF_FLOOR_VEL_KMS = 1e-8
        // Adapter resolution: target time is rounded to 1 ms (<= 0.5 ms) and the epoch is truncated to 1 ms (< 1 ms).
        const val ADAPTER_TIME_QUANTUM_S = 1.5e-3
        const val MU_KM3_S2 = 398600.8 // WGS-72
    }
}
