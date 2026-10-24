package horizon.observatory.resilience

import horizon.observatory.domain.model.ObservationType
import horizon.observatory.domain.model.RawObservation
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

/**
 * Seeded, deterministic property tests. Random hostile streams (out-of-order, duplicate, missing
 * timestamps, invalid coordinates/accuracy/speed/bearing, malformed payloads, long gaps) must never
 * crash the runtime, never invent a fusion/cell state, never emit an invalid estimate, and must replay
 * identically. Synthetic data only: this is NOT real-device evidence.
 */
class ResilienceInvariantFuzzTest {
    private fun pick(r: Random, vararg v: Double?): Double? = v[r.nextInt(v.size)]
    private fun garbage(r: Random): String =
        listOf("", "not json", "[]", "{", "{\"latitude\":\"x\"}", "null", "{\"satellites\":42}")[r.nextInt(7)]

    private fun fuzzFix(r: Random): String {
        if (r.nextInt(15) == 0) return garbage(r)
        val lat = when (r.nextInt(12)) { 0 -> 90.0; 1 -> -90.0; 2 -> 91.0; 3 -> 0.0; else -> -90.0 + r.nextDouble() * 180.0 }
        val lon = when (r.nextInt(12)) { 0 -> 180.0; 1 -> -180.0; 2 -> 181.0; else -> -180.0 + r.nextDouble() * 360.0 }
        return fixJson(
            lat, lon,
            acc = pick(r, null, 0.0, 3.0, 30.0, 500.0, -1.0, 1e308),
            speed = pick(r, null, 0.0, 0.3, 5.0, 30.0, -2.0),
            bearing = pick(r, null, 0.0, 90.0, 359.9, 360.0, -5.0, 720.0),
            vertical = pick(r, null, 2.0, -1.0),
            alt = pick(r, null, 100.0)
        )
    }

    private fun generate(seed: Long): List<RawObservation> {
        val r = Random(seed)
        val out = ArrayList<RawObservation>()
        var t = 1 * S
        var lastSource = t
        repeat(250) {
            t += when (r.nextInt(10)) {
                0 -> (10 + r.nextInt(25)) * S
                1, 2 -> r.nextInt(3000) * 1_000_000L
                else -> (100 + r.nextInt(1400)) * 1_000_000L
            }
            val source: Long? = when (r.nextInt(20)) {
                0 -> null
                1 -> lastSource
                2 -> (lastSource - r.nextInt(5000) * 1_000_000L).coerceAtLeast(1L)
                else -> t
            }
            if (source == t) lastSource = t
            out += when (r.nextInt(100)) {
                in 0..34 -> rawObs(ObservationType.GNSS_FIX, fuzzFix(r), source, t)
                in 35..59 -> rawObs(ObservationType.GNSS_STATUS, if (r.nextInt(8) == 0) garbage(r) else STATUS_JSON, source, t)
                in 60..84 -> rawObs(ObservationType.SENSOR_ACCEL, "{}", source, t)
                in 85..92 -> rawObs(ObservationType.CELLULAR_INFO, "{}", source, t)
                in 93..96 -> rawObs(ObservationType.SENSOR_GYRO, "{}", source, t)
                else -> rawObs(ObservationType.GNSS_RAW_MEASUREMENT, """{"constellationType":1,"svid":${r.nextInt(40)},"cn0DbHz":${r.nextInt(50)}.0}""", source, t)
            }
        }
        return out
    }

    private fun checkEstimate(seed: Long, e: NavigationEstimate, maxIngest: Long) {
        val m = "seed=$seed $e"
        assertTrue(m, e.latitudeDeg.isFinite() && e.latitudeDeg in -90.0..90.0)
        assertTrue(m, e.longitudeDeg.isFinite() && e.longitudeDeg in -180.0..180.0)
        assertTrue(m, e.horizontalUncertaintyM.isFinite() && e.horizontalUncertaintyM >= 0.0 &&
            e.horizontalUncertaintyM <= CoastingPolicy().maxHorizontalUncertaintyM)
        assertEquals(m, "DEAD_RECKONED_FROM_LAST_TRUSTED_PVT", e.provenance)
        assertEquals(m, CoastingPolicy.ESTIMATOR_ID, e.estimator)
        assertTrue(m, e.timestampMonotonicNs in 1L..(maxIngest + 1_000_000L))
        e.bearingDeg?.let { assertTrue(m, it >= 0.0 && it < 360.0) }
        e.speedMps?.let { assertTrue(m, it.isFinite() && it >= 0.0) }
        if (e.altitudeM != null) assertNotNull(m, e.verticalUncertaintyM)
        // exactly what the service publishes: it must never be refused by the contract validator
        val snapshot = ResilientLocationSnapshot(
            e.latitudeDeg, e.longitudeDeg, e.altitudeM, e.horizontalUncertaintyM, e.verticalUncertaintyM,
            e.timestampMonotonicNs, e.navigationState, e.provenance,
            fixKind = ResilientFixKind.SHORT_HORIZON_ESTIMATE, utcTimestampMs = null,
            speedMps = e.speedMps, bearingDeg = e.bearingDeg, estimator = e.estimator
        )
        assertNull(m, ResilientLocationValidator.problem(snapshot))
    }

    @Test fun runtimeInvariantsHoldOverRandomHostileStreams() {
        val visited = mutableSetOf<NavigationState>()
        val rejections = mutableSetOf<CoastingRejection>()
        var estimates = 0
        for (seed in 0L until 400L) {
            val stream = generate(seed)
            val runtime = ResilienceRuntime()
            val updates = ArrayList<ResilienceRuntimeUpdate>()
            var maxIngest = 0L
            var lastValidPvt = Long.MIN_VALUE
            for (o in stream) {
                maxIngest = maxOf(maxIngest, o.ingestionMonotonicTimestampNs ?: 0L)
                val u = try { runtime.accept(o) } catch (t: Throwable) {
                    throw AssertionError("seed=$seed threw on ${o.type} ${o.payloadJson}", t)
                }
                val s = runtime.navigationState
                visited += s
                assertNotEquals("seed=$seed no external evidence => never fusion", NavigationState.MULTI_SOURCE_FUSION, s)
                assertNotEquals("seed=$seed cell info is not a position", NavigationState.CELL_AIDED, s)
                val v = runtime.debugLastValidPvtNs
                if (v != null) { assertTrue("seed=$seed last valid PVT time went backwards", v >= lastValidPvt); lastValidPvt = v }
                if (u != null) {
                    updates += u
                    u.coastingRejection?.let { rejections += it }
                    u.estimate?.let { e ->
                        estimates++
                        checkEstimate(seed, e, maxIngest)
                        assertEquals("seed=$seed estimates exist only while bridging", NavigationState.INERTIAL_BRIDGING, s)
                    }
                }
            }
            assertEquals("seed=$seed replay must be deterministic", updates, ResilienceRuntime.replay(stream))
        }
        // Guard against vacuous properties: the generator must actually reach the interesting states.
        assertTrue("estimates emitted: $estimates", estimates > 100)
        assertTrue("states visited: $visited", visited.containsAll(listOf(
            NavigationState.FULL_GNSS, NavigationState.GNSS_DEGRADED, NavigationState.GNSS_LOST,
            NavigationState.INERTIAL_BRIDGING, NavigationState.RECOVERY)))
        assertTrue("coasting rejections seen: $rejections", rejections.size >= 2)
    }

    // ---- codec / contract properties -------------------------------------------------------------

    private fun pickD(r: Random, lo: Double, hi: Double, vararg special: Double): Double =
        if (r.nextInt(6) == 0) special[r.nextInt(special.size)] else lo + r.nextDouble() * (hi - lo)

    private fun randomSnapshot(r: Random): ResilientLocationSnapshot {
        fun opt(f: () -> Double): Double? = if (r.nextInt(3) == 0) null else f()
        return ResilientLocationSnapshot(
            latitudeDeg = pickD(r, -90.0, 90.0, 90.0, -90.0, 91.0, -91.0, Double.NaN, Double.POSITIVE_INFINITY),
            longitudeDeg = pickD(r, -180.0, 180.0, 180.0, -180.0, 181.0, Double.NaN),
            altitudeM = opt { pickD(r, -500.0, 9000.0, Double.NaN) },
            horizontalUncertaintyM = pickD(r, 0.0, 500.0, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 1e12),
            verticalUncertaintyM = opt { pickD(r, 0.0, 100.0, -1.0, Double.NaN) },
            timestampMonotonicNs = listOf(1L, 0L, -5L, Long.MAX_VALUE, 1L + (r.nextLong() ushr 2))[r.nextInt(5)],
            navigationState = NavigationState.values()[r.nextInt(NavigationState.values().size)],
            provenance = listOf("GNSS_OBSERVED", "", "DEAD_RECKONED_FROM_LAST_TRUSTED_PVT")[r.nextInt(3)],
            fixKind = listOf(null, ResilientFixKind.OBSERVED_GNSS, ResilientFixKind.SHORT_HORIZON_ESTIMATE)[r.nextInt(3)],
            utcTimestampMs = listOf<Long?>(null, 1_700_000_000_000L, -1L)[r.nextInt(3)],
            speedMps = opt { pickD(r, 0.0, 60.0, -1.0, Double.NaN) },
            bearingDeg = opt { pickD(r, 0.0, 359.9, 360.0, -1.0) },
            estimator = listOf<String?>(null, "E")[r.nextInt(2)]
        )
    }

    @Test fun codecRoundTripsValidSnapshots_andRefusesInvalidOnes() {
        val r = Random(7)
        var valid = 0
        repeat(5000) {
            val s = randomSnapshot(r)
            val problem = ResilientLocationValidator.problem(s)
            if (problem == null) {
                valid++
                assertEquals("round trip: $s", SnapshotDecodeResult.Valid(s), ResilientLocationCodec.decode(ResilientLocationCodec.encode(s)))
            } else {
                try { ResilientLocationCodec.encode(s); fail("encode accepted $problem: $s") } catch (e: IllegalArgumentException) { }
            }
        }
        assertTrue("generator must exercise the valid path ($valid)", valid > 100)
    }

    private fun mutate(s: String, r: Random, tokens: List<String>): String {
        var x = s
        repeat(1 + r.nextInt(3)) {
            if (x.length < 2) return x
            x = when (r.nextInt(5)) {
                0 -> { val i = r.nextInt(x.length); x.substring(0, i) + (32 + r.nextInt(95)).toChar() + x.substring(i + 1) }
                1 -> { val i = r.nextInt(x.length); x.removeRange(i, minOf(x.length, i + 1 + r.nextInt(8))) }
                2 -> x.substring(0, r.nextInt(x.length))
                3 -> {
                    val numbers = Regex("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?").findAll(x).toList()
                    if (numbers.isEmpty()) x else x.replaceRange(numbers[r.nextInt(numbers.size)].range, tokens[r.nextInt(tokens.size)])
                }
                else -> { val i = r.nextInt(x.length); x.substring(0, i) + tokens[r.nextInt(tokens.size)] + x.substring(i) }
            }
        }
        return x
    }

    @Test fun decodeNeverThrows_andNeverLetsAnInvalidSnapshotThrough() {
        val base = ResilientLocationCodec.encode(
            ResilientLocationSnapshot(50.0, 8.0, 100.0, 4.0, 7.0, 5_000_000_000L, NavigationState.FULL_GNSS, "GNSS_OBSERVED",
                fixKind = ResilientFixKind.OBSERVED_GNSS, utcTimestampMs = 1_700_000_000_000L, speedMps = 3.0, bearingDeg = 90.0)
        )
        val tokens = listOf("NaN", "Infinity", "-1", "1e999", "null", "\"x\"", "true", "[]", "{}", "9223372036854775808", "-0", "0", "91", "181", "1e-400")
        val r = Random(11)
        repeat(20000) {
            val m = mutate(base, r, tokens)
            val result = try { ResilientLocationCodec.decode(m) } catch (t: Throwable) { throw AssertionError("decode threw on: $m", t) }
            if (result is SnapshotDecodeResult.Valid) {
                assertNull("invalid snapshot escaped decode: $m", ResilientLocationValidator.problem(result.snapshot))
            }
        }
        listOf("", " ", "null", "[]", "{", "\u0000", "{\"schemaVersion\":99}", "{\"schemaVersion\":\"2\"}").forEach {
            assertTrue("hostile input must be CORRUPT: '$it'", ResilientLocationCodec.decode(it) is SnapshotDecodeResult.Corrupt)
        }
        assertEquals(SnapshotDecodeResult.Absent, ResilientLocationCodec.decode(null))
    }

    @Test fun contractRowNeverLeaksAPositionUnlessFresh() {
        val r = Random(3)
        val positionKeys = listOf("latitudeDeg", "longitudeDeg", "altitudeM", "horizontalUncertaintyM", "verticalUncertaintyM", "speedMps", "bearingDeg")
        var fresh = 0
        var withheld = 0
        repeat(5000) {
            val s = randomSnapshot(r)
            val decoded: SnapshotDecodeResult = when {
                ResilientLocationValidator.problem(s) == null -> SnapshotDecodeResult.Valid(s)
                r.nextBoolean() -> SnapshotDecodeResult.Absent
                else -> SnapshotDecodeResult.Corrupt("X")
            }
            val now = 1L + (r.nextLong() ushr 2) % (200L * S)
            val row = ResilientLocationContract.row(decoded, now)
            assertTrue(ResilientLocationContract.COLUMNS.toSet().containsAll(row.keys))
            assertEquals(ResilientLocationContract.VERSION, row["contractVersion"])
            val status = row["status"] as String
            if (status != "FRESH") {
                withheld++
                positionKeys.forEach { assertNull("$status leaked $it", row[it]) }
            } else {
                fresh++
                val snapshot = (decoded as SnapshotDecodeResult.Valid).snapshot
                assertEquals(snapshot.latitudeDeg, row["latitudeDeg"])
                assertEquals(snapshot.longitudeDeg, row["longitudeDeg"])
                val max = if (snapshot.fixKind == ResilientFixKind.SHORT_HORIZON_ESTIMATE) 20_000L else 5_000L
                assertTrue("age ${row["ageMs"]}", (row["ageMs"] as Long) in 0L..max)
            }
        }
        assertTrue("both branches must be exercised (fresh=$fresh withheld=$withheld)", fresh > 0 && withheld > 0)
    }
}
