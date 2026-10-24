package horizon.observatory.resilience

import org.junit.Assert.*
import org.junit.Test

/** G6 (pure part): provider row contract, exercised through an independent sample client. */
class ResilientLocationContractRowTest {
    /** Minimal independent consumer: only reads the documented columns, never the internals. */
    private class SampleClient(private val row: Map<String, Any?>) {
        val version get() = row["contractVersion"] as Int
        val status get() = row["status"] as String
        val hasCurrentPosition get() = status == "FRESH" && row["latitudeDeg"] != null && row["longitudeDeg"] != null
        val isEstimate get() = row["fixKind"] == "SHORT_HORIZON_ESTIMATE"
        val ageMs get() = row["ageMs"] as Long?
        val accuracyM get() = row["horizontalUncertaintyM"] as Double?
        val unavailable get() = status == "NO_SNAPSHOT" || status == "CORRUPT"
    }

    private val now = 1_000 * S
    private fun observed(ts: Long = now - 2 * S, alt: Double? = null, vert: Double? = null) =
        ResilientLocationSnapshot(50.0, 8.0, alt, 4.0, vert, ts, NavigationState.FULL_GNSS, "GNSS_OBSERVED", ResilientFixKind.OBSERVED_GNSS, 1_700_000_000_000L, 3.0, 90.0)
    private fun estimate(ts: Long = now - 2 * S, hu: Double = 40.0) =
        ResilientLocationSnapshot(50.0, 8.0, null, hu, null, ts, NavigationState.INERTIAL_BRIDGING, "DEAD_RECKONED_FROM_LAST_TRUSTED_PVT", ResilientFixKind.SHORT_HORIZON_ESTIMATE, null, 10.0, 0.0, CoastingPolicy.ESTIMATOR_ID)
    private fun row(d: SnapshotDecodeResult) = ResilientLocationContract.row(d, now)

    @Test fun columns_keepTheVersion1NamesFirst_andHaveNoDuplicates() {
        assertEquals(listOf("latitudeDeg", "longitudeDeg", "altitudeM", "horizontalUncertaintyM", "verticalUncertaintyM", "timestampMonotonicNs", "navigationState", "provenance"),
            ResilientLocationContract.COLUMNS.take(8))
        assertEquals(ResilientLocationContract.COLUMNS.size, ResilientLocationContract.COLUMNS.toSet().size)
        assertEquals(2, ResilientLocationContract.VERSION)
    }
    @Test fun emptyState_isExplicit_notAFakeCoordinate() {
        val c = SampleClient(row(SnapshotDecodeResult.Absent))
        assertEquals("NO_SNAPSHOT", c.status); assertTrue(c.unavailable); assertFalse(c.hasCurrentPosition); assertNull(c.ageMs)
        assertNull(row(SnapshotDecodeResult.Absent)["latitudeDeg"])
    }
    @Test fun corruptedPayload_isReportedAsCorrupt_withReason_andNoPosition() {
        val r = row(SnapshotDecodeResult.Corrupt("MALFORMED_JSON"))
        assertEquals("CORRUPT", r["status"]); assertEquals("MALFORMED_JSON", r["statusReason"]); assertNull(r["latitudeDeg"])
        assertTrue(SampleClient(r).unavailable)
    }
    @Test fun freshObservedFix_exposesKindAgeAccuracyAndNullableFields() {
        val c = SampleClient(row(SnapshotDecodeResult.Valid(observed())))
        assertTrue(c.hasCurrentPosition); assertFalse(c.isEstimate); assertEquals(2_000L, c.ageMs); assertEquals(4.0, c.accuracyM!!, 0.0); assertEquals(2, c.version)
        val r = row(SnapshotDecodeResult.Valid(observed()))
        assertNull(r["altitudeM"]); assertNull(r["verticalUncertaintyM"])            // null stays null, never 0
        assertEquals("GNSS_OBSERVED", r["provenance"]); assertEquals("OBSERVED_GNSS", r["fixKind"]); assertEquals(3.0, r["speedMps"]); assertEquals(90.0, r["bearingDeg"])
        assertEquals(1_700_000_000_000L, r["utcTimestampMs"])
    }
    @Test fun freshEstimate_isClearlyLabelledAsAnEstimate_withEstimatorAndProvenance() {
        val r = row(SnapshotDecodeResult.Valid(estimate()))
        val c = SampleClient(r)
        assertTrue(c.hasCurrentPosition); assertTrue(c.isEstimate); assertEquals(40.0, c.accuracyM!!, 0.0)
        assertEquals(CoastingPolicy.ESTIMATOR_ID, r["estimator"]); assertEquals("DEAD_RECKONED_FROM_LAST_TRUSTED_PVT", r["provenance"]); assertEquals("INERTIAL_BRIDGING", r["navigationState"])
    }
    @Test fun staleSnapshot_withholdsPosition_butKeepsAgeKindAndReason() {
        val r = ResilientLocationContract.row(SnapshotDecodeResult.Valid(observed(ts = now - 6 * S)), now)
        val c = SampleClient(r)
        assertEquals("STALE", c.status); assertFalse(c.hasCurrentPosition); assertEquals(6_000L, c.ageMs); assertEquals("MAX_AGE_EXCEEDED", r["statusReason"])
        assertNull(r["latitudeDeg"]); assertNull(r["horizontalUncertaintyM"]); assertEquals("OBSERVED_GNSS", r["fixKind"])
    }
    @Test fun estimateOlderThanItsOwnCeiling_orMoreUncertainThanAllowed_isStale() {
        assertEquals("STALE", row(SnapshotDecodeResult.Valid(estimate(ts = now - 21 * S)))["status"])
        assertEquals("FRESH", row(SnapshotDecodeResult.Valid(estimate(ts = now - 19 * S)))["status"])
        val tooUncertain = row(SnapshotDecodeResult.Valid(estimate(hu = 151.0)))
        assertEquals("STALE", tooUncertain["status"]); assertEquals("UNCERTAINTY_CEILING_EXCEEDED", tooUncertain["statusReason"])
    }
    @Test fun snapshotFromAnotherBootClockDomain_isNeverCurrent() {
        val r = ResilientLocationContract.row(SnapshotDecodeResult.Valid(observed(ts = now + 3_600 * S)), now)
        assertEquals("STALE", r["status"]); assertEquals("CLOCK_DOMAIN_MISMATCH", r["statusReason"]); assertNull(r["latitudeDeg"]); assertNull(r["ageMs"])
    }
    @Test fun smallClockSkew_isTolerated_andAgeIsClampedToZero() {
        val r = ResilientLocationContract.row(SnapshotDecodeResult.Valid(observed(ts = now + 500_000_000L)), now)
        assertEquals("FRESH", r["status"]); assertEquals(0L, r["ageMs"])
    }

    @Test fun freshnessPolicy_rejectsInvalidAndOverflowingThresholds() {
        assertThrows(IllegalArgumentException::class.java) { SnapshotFreshnessPolicy(maxObservedAgeMs = -1L) }
        assertThrows(IllegalArgumentException::class.java) { SnapshotFreshnessPolicy(maxEstimateAgeMs = -1L) }
        assertThrows(IllegalArgumentException::class.java) { SnapshotFreshnessPolicy(maxEstimateUncertaintyM = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { SnapshotFreshnessPolicy(maxFutureSkewMs = -1L) }
        assertThrows(IllegalArgumentException::class.java) { SnapshotFreshnessPolicy(maxObservedAgeMs = Long.MAX_VALUE) }
    }

    @Test fun freshness_handlesNegativeClockAndLongOverflowWithoutCallingItFresh() {
        val sample = observed(ts = Long.MAX_VALUE - 5)
        assertEquals("INVALID_MONOTONIC_CLOCK", SnapshotFreshness.evaluate(sample, -1L).reason)
        assertEquals("CLOCK_DOMAIN_MISMATCH", SnapshotFreshness.evaluate(sample, 1L).reason)
    }

    @Test fun everyRowKey_isADeclaredColumn() {
        val keys = row(SnapshotDecodeResult.Valid(observed())).keys
        assertTrue(ResilientLocationContract.COLUMNS.toSet().containsAll(keys))
    }
}
