package horizon.observatory.analysis.integrity

import horizon.observatory.storage.entity.ObservationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssIntegrityIndicatorsTest {
    private val f1 = 1_575_420_000.0
    private fun ns(i: Int) = i * 1_000_000_000L
    private fun noise(i: Int, k: Int): Double = (((i * 31 + k * 17) % 11) - 5) * 0.08

    private fun gen(count: Int, sats: Int = 8, cn0: (Int, Int) -> Double, agc: (Int) -> Double? = { null }): List<MeasurementSample> =
        (0 until count).flatMap { i ->
            (0 until sats).map { s -> MeasurementSample(ns(i), 1, s + 1, f1, cn0(i, s), agc(i), null, null, null, null, null) }
        }

    private fun adrTracks(count: Int, sign: Double, jumps: Set<Pair<Int, Int>> = emptySet(), jumpState: Int = 1): List<MeasurementSample> {
        val out = ArrayList<MeasurementSample>()
        for (sv in 1..3) {
            fun rr(i: Int) = -300.0 + 0.5 * i + 10.0 * sv
            var adr = 1000.0
            for (i in 0 until count) {
                if (i > 0) adr += sign * 0.5 * (rr(i - 1) + rr(i)) + noise(i, sv) * 0.1
                val jumped = (sv to i) in jumps
                if (jumped) adr += 5.0
                out.add(MeasurementSample(ns(i), 1, sv, f1, 40.0, null, rr(i), 0.05, adr, if (jumped) jumpState else 1, 0.01))
            }
        }
        return out
    }

    private fun clocks(count: Int, jumpAt: Int? = null, discontinuity: Boolean = false): List<ClockSample> =
        (0 until count).map { i ->
            val drift = 100.0 + 0.01 * i + (if (jumpAt != null && i >= jumpAt) 50.0 else 0.0) + noise(i, 7) * 0.25
            ClockSample(ns(i), null, null, null, drift, null, if (discontinuity && jumpAt != null && i >= jumpAt) 1 else 0, null)
        }

    private fun IntegrityReport.byId(id: String) = indicators.first { it.id == id }
    private fun IndicatorResult.metric(name: String) = metrics.first { it.name == name }.value

    @Test fun stableDataIsWithinBaselineAndNeverNominal() {
        val r = GnssIntegrityIndicators.evaluate(gen(120, cn0 = { i, s -> 35.0 + s + noise(i, s) }), emptyList(), emptyList())
        assertEquals(IndicatorStatus.WITHIN_BASELINE, r.byId("cn0").status)
        assertEquals(IndicatorStatus.UNAVAILABLE, r.byId("agc").status)
        assertTrue(r.byId("agc").reasonCodes.contains("AGC_NOT_EXPOSED"))
        assertTrue(r.byId("clock").reasonCodes.contains("CLOCK_NOT_CAPTURED"))
        assertTrue(r.byId("adr").reasonCodes.contains("ADR_NOT_EXPOSED"))
        assertEquals(IntegrityAssessment.NOT_ESTABLISHED, r.assessment)
        assertFalse(IntegrityAssessment.values().any { it.name == "NOMINAL" })
    }

    @Test fun nothingAtAllIsUnavailable() {
        assertEquals(IntegrityAssessment.UNAVAILABLE, GnssIntegrityIndicators.evaluate(emptyList(), emptyList(), emptyList()).assessment)
    }

    @Test fun commonModeCn0StepIsFlaggedWithOnset() {
        val r = GnssIntegrityIndicators.evaluate(
            gen(140, cn0 = { i, s -> 35.0 + s + noise(i, s) - (if (i >= 80) 12.0 else 0.0) }), emptyList(), emptyList()
        )
        val cn0 = r.byId("cn0")
        assertEquals(IndicatorStatus.DEVIATES, cn0.status)
        assertTrue(cn0.reasonCodes.contains("CN0_COMMON_MODE_SHIFT"))
        val onset = cn0.metric("level_shift_onset")!!
        assertTrue("onset $onset", onset in 80.0..95.0)
        assertEquals(IntegrityAssessment.SUSPECT, r.assessment)
    }

    @Test fun dispersionCollapseIsFlaggedWithoutALevelShift() {
        val r = GnssIntegrityIndicators.evaluate(
            gen(140, cn0 = { i, s -> if (i >= 80) 38.5 + noise(i, s) else 35.0 + s + noise(i, s) }), emptyList(), emptyList()
        )
        val cn0 = r.byId("cn0")
        assertEquals(IndicatorStatus.DEVIATES, cn0.status)
        assertTrue(cn0.reasonCodes.contains("CN0_DISPERSION_SHIFT"))
        assertFalse(cn0.reasonCodes.contains("CN0_COMMON_MODE_SHIFT"))
    }

    @Test fun tooFewSatellitesOrEpochsIsInsufficientHistoryNotNormal() {
        assertEquals(IndicatorStatus.INSUFFICIENT_HISTORY, GnssIntegrityIndicators.evaluate(gen(120, sats = 2, cn0 = { _, _ -> 40.0 }), emptyList(), emptyList()).byId("cn0").status)
        assertEquals(IndicatorStatus.INSUFFICIENT_HISTORY, GnssIntegrityIndicators.evaluate(gen(4, cn0 = { _, _ -> 40.0 }), emptyList(), emptyList()).byId("cn0").status)
    }

    @Test fun agcStepWithConcurrentCn0DropIsFlaggedAndSourceIsLabelled() {
        val r = GnssIntegrityIndicators.evaluate(
            gen(140, cn0 = { i, s -> 35.0 + s + noise(i, s) - (if (i >= 80) 10.0 else 0.0) },
                agc = { i -> (if (i >= 80) 12.0 else 20.0) + noise(i, 99) * 0.25 }),
            emptyList(), emptyList()
        )
        val agc = r.byId("agc")
        assertEquals(IndicatorStatus.DEVIATES, agc.status)
        assertTrue(agc.reasonCodes.contains("AGC_SOURCE_MEASUREMENT_LEVEL_DEPRECATED"))
        assertTrue(agc.reasonCodes.contains("AGC_LEVEL_STEP"))
        assertTrue(agc.reasonCodes.contains("AGC_AND_CN0_SHIFT_CONCURRENT"))
    }

    @Test fun eventLevelAgcIsPreferredWhenAvailable() {
        val events = (0 until 140).map { i -> AgcEventSample(ns(i), (if (i >= 80) 12.0 else 20.0) + noise(i, 99) * 0.25, 1, f1) }
        val r = GnssIntegrityIndicators.evaluate(gen(140, cn0 = { i, s -> 35.0 + s + noise(i, s) }), emptyList(), events)
        assertTrue(r.byId("agc").reasonCodes.contains("AGC_SOURCE_EVENT_LEVEL"))
        assertEquals(IndicatorStatus.DEVIATES, r.byId("agc").status)
    }

    @Test fun eventLevelAgcDoesNotPoolDistinctCarrierBands() {
        val secondBand = 1_176_450_000.0
        val events = (0 until 140).flatMap { i ->
            val firstLevel = 20.0 + noise(i, 99) * 0.25
            val secondLevel = (if (i >= 80) 12.0 else 20.0) + noise(i, 98) * 0.25
            listOf(
                AgcEventSample(ns(i), firstLevel, 1, f1),
                AgcEventSample(ns(i), secondLevel, 1, secondBand)
            )
        }
        val r = GnssIntegrityIndicators.evaluate(gen(140, cn0 = { i, s -> 35.0 + s + noise(i, s) }), emptyList(), events)
        val agc = r.byId("agc")
        assertEquals(IndicatorStatus.DEVIATES, agc.status)
        assertTrue(agc.reasonCodes.contains("AGC_SOURCE_EVENT_LEVEL"))
        assertTrue(agc.reasonCodes.contains("AGC_GROUP_SHIFT"))
        assertTrue(agc.summary.contains("group series=2"))
        assertTrue(agc.summary.contains("C1/B1176MHz shifted"))
        assertNotNull(agc.metric("agc_group_C1_B1176MHz_max_abs_z"))
        assertNotNull(agc.metric("agc_group_C1_B1575MHz_max_abs_z"))
    }

    @Test fun clockDiscontinuitySegmentsTheSeriesInsteadOfCreatingFalseDriftOutliers() {
        val r = GnssIntegrityIndicators.evaluate(emptyList(), clocks(120, jumpAt = 60, discontinuity = true), emptyList())
        val clock = r.byId("clock")
        assertEquals(IndicatorStatus.DEVIATES, clock.status)
        assertTrue(clock.reasonCodes.contains("CLOCK_DISCONTINUITY_OBSERVED"))
        assertFalse(clock.reasonCodes.contains("DRIFT_ACCELERATION_OUTLIER"))
        assertEquals(0.0, clock.metric("drift_accel_outliers")!!, 0.0)
    }

    @Test fun driftJumpInsideASegmentIsFlagged() {
        val clock = GnssIntegrityIndicators.evaluate(emptyList(), clocks(120, jumpAt = 60, discontinuity = false), emptyList()).byId("clock")
        assertEquals(IndicatorStatus.DEVIATES, clock.status)
        assertTrue(clock.reasonCodes.contains("DRIFT_ACCELERATION_OUTLIER"))
        assertFalse(clock.reasonCodes.contains("CLOCK_DISCONTINUITY_OBSERVED"))
    }

    @Test fun cleanClockIsWithinBaseline() {
        assertEquals(IndicatorStatus.WITHIN_BASELINE, GnssIntegrityIndicators.evaluate(emptyList(), clocks(120), emptyList()).byId("clock").status)
    }

    @Test fun adrConsistencyEstimatesTheSignConventionAndStaysQuietOnConsistentData() {
        for (sign in listOf(-1.0, 1.0)) {
            val adr = GnssIntegrityIndicators.evaluate(adrTracks(120, sign), emptyList(), emptyList()).byId("adr")
            assertEquals(IndicatorStatus.WITHIN_BASELINE, adr.status)
            assertEquals(sign, adr.metric("sign_convention")!!, 0.0)
        }
    }

    @Test fun unflaggedAdrJumpsAreInconsistencyButFlaggedResetsAreExcluded() {
        val jumps = setOf(2 to 60, 2 to 90)
        val bad = GnssIntegrityIndicators.evaluate(adrTracks(120, -1.0, jumps), emptyList(), emptyList()).byId("adr")
        assertEquals(IndicatorStatus.DEVIATES, bad.status)
        assertTrue(bad.reasonCodes.contains("ADR_RANGE_RATE_INCONSISTENT"))
        assertTrue(bad.metric("outlier_intervals")!! >= 2.0)

        val flagged = GnssIntegrityIndicators.evaluate(adrTracks(120, -1.0, jumps, jumpState = 3), emptyList(), emptyList()).byId("adr")
        assertEquals(IndicatorStatus.WITHIN_BASELINE, flagged.status)
        assertTrue(flagged.reasonCodes.contains("ADR_RESET_OR_CYCLE_SLIP_FLAGS_PRESENT"))
        assertEquals(2.0, flagged.metric("adr_reset_flags")!!, 0.0)
    }

    @Test fun adapterParsesRawAndClockRowsAndKeepsMissingFieldsNull() {
        fun row(seq: Long, type: String, json: String) = ObservationEntity(
            sessionId = "S", sequenceNumber = seq, timestampUtcMs = seq, type = type, provider = "T", rawPayloadJson = json, source = "GNSS"
        )
        val rows = listOf(
            row(1, "GNSS_RAW_MEASUREMENT", """{"constellationType":1,"svid":5,"cn0DbHz":41.5,"carrierFrequencyHz":1575420000.0,"automaticGainControlLevelDb":null,"pseudorangeRateMetersPerSecond":-120.5,"pseudorangeRateUncertaintyMetersPerSecond":0.1,"accumulatedDeltaRangeMeters":10.0,"accumulatedDeltaRangeState":1,"accumulatedDeltaRangeUncertaintyMeters":0.01,"eventElapsedRealtimeNanos":5000000000}"""),
            row(2, "GNSS_CLOCK", """{"eventElapsedRealtimeNanos":5000000000,"timeNanos":123,"driftNanosPerSecond":88.5,"hardwareClockDiscontinuityCount":2,"biasNanos":null,"eventExtras":{"fullTracking":false,"agcApi":"EVENT_LEVEL","agcEvents":[{"levelDb":17.5,"constellationType":1,"carrierFrequencyHz":1575420000.0}]}}"""),
            row(3, "GNSS_RAW_MEASUREMENT", "not json"),
            row(4, "GNSS_STATUS", "{}")
        )
        val inputs = IntegrityInputs.fromObservations(rows)
        assertEquals(1, inputs.measurements.size)
        val m = inputs.measurements[0]
        assertNull(m.agcDb)
        assertEquals(41.5, m.cn0DbHz!!, 0.0)
        assertEquals(5_000_000_000L, m.eventNs)
        assertEquals(1, inputs.clocks.size)
        val c = inputs.clocks[0]
        assertNull(c.biasNs)
        assertEquals(88.5, c.driftNsPerS!!, 0.0)
        assertEquals(2, c.discontinuityCount)
        assertEquals(false, c.fullTracking)
        assertEquals(1, inputs.agc.size)
        assertEquals(17.5, inputs.agc[0].levelDb, 0.0)
        assertNotNull(inputs.agc[0].carrierFrequencyHz)
        assertEquals(1, inputs.agcDiagnostics.eventsSeen)
        assertEquals(0, inputs.agcDiagnostics.emptyArrayEvents)
        assertEquals(1, inputs.agcDiagnostics.validControlCount)
    }

    @Test fun parserSeparatesMissingEmptyMalformedAndInvalidAgcEntries() {
        fun row(seq: Long, json: String) = ObservationEntity(
            sessionId = "S", sequenceNumber = seq, timestampUtcMs = seq, type = "GNSS_CLOCK", provider = "T", rawPayloadJson = json, source = "GNSS"
        )
        val rows = listOf(
            row(1, """{"eventElapsedRealtimeNanos":1000000000,"eventExtras":{}}"""),
            row(2, """{"eventElapsedRealtimeNanos":2000000000,"eventExtras":{"agcEvents":[]}}"""),
            row(3, """{"eventElapsedRealtimeNanos":3000000000,"eventExtras":{"agcEvents":[null,{"levelDb":"bad"},{"levelDb":17.0,"carrierFrequencyHz":1575420000}]}}""")
        )
        val inputs = IntegrityInputs.fromObservations(rows)
        assertEquals(3, inputs.agcDiagnostics.eventsSeen)
        assertEquals(1, inputs.agcDiagnostics.unsupportedOrMissingArrayEvents)
        assertEquals(1, inputs.agcDiagnostics.emptyArrayEvents)
        assertEquals(1, inputs.agcDiagnostics.malformedEntryCount)
        assertEquals(1, inputs.agcDiagnostics.invalidLevelCount)
        assertEquals(1, inputs.agcDiagnostics.validControlCount)
        val result = GnssIntegrityIndicators.evaluate(inputs).byId("agc")
        assertEquals(1.0, result.metric("event_values_valid")!!, 0.0)
        assertEquals(1.0, result.metric("event_values_invalid_level")!!, 0.0)
        assertEquals(1.0, result.metric("event_values_malformed_entry")!!, 0.0)
        assertEquals(1.0, result.metric("events_empty_agc_array")!!, 0.0)
        assertEquals(1.0, result.metric("events_missing_agc_array")!!, 0.0)
    }
}
