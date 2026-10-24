package horizon.observatory.analysis.integrity

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Diagnostic INDICATORS computed from observed raw GNSS evidence. They are not detectors with an established
 * false-alarm rate and never name an attacker or claim integrity: the strongest positive outcome is NOT_ESTABLISHED.
 * Principles (from the supplied R&D specifications): robust temporal features relative to the session's own baseline,
 * segmentation at clock discontinuities (no differentiation across resets), per constellation/band grouping,
 * missing data is "unavailable" (never zero), every metric carries its sample count and window, thresholds are
 * parameters ([IntegrityProfile]) not universal constants, and benign confounders are listed next to each indicator.
 */
object GnssIntegrityIndicators {
    private const val ADR_VALID = 1
    private const val ADR_RESET = 2
    private const val ADR_CYCLE_SLIP = 4
    private const val ADR_HALF_CYCLE_RESOLVED = 8
    private const val ADR_HALF_CYCLE_REPORTED = 16

    val NOTES = listOf(
        "Indicators share causes (environment, receiver mode, motion) and are NOT independent evidence.",
        "Baselines are the session's own: an anomaly present from the start of the session is invisible to these tests.",
        "No attack attribution is made; a deviation is a measured departure from the session baseline, nothing more.",
        "Default thresholds are uncalibrated (no false-alarm rate established); see IntegrityProfile.",
        "These diagnostics cannot establish integrity; the best outcome is NOT_ESTABLISHED."
    )

    fun evaluate(inputs: IntegrityInputs, profile: IntegrityProfile = IntegrityProfile()): IntegrityReport =
        evaluateInternal(inputs.measurements, inputs.clocks, inputs.agc, inputs.agcDiagnostics, profile)

    fun evaluate(
        measurements: List<MeasurementSample>,
        clocks: List<ClockSample>,
        agcEvents: List<AgcEventSample>,
        profile: IntegrityProfile = IntegrityProfile()
    ): IntegrityReport = evaluateInternal(measurements, clocks, agcEvents, AgcParseDiagnostics(validControlCount = agcEvents.size), profile)

    private fun evaluateInternal(
        measurements: List<MeasurementSample>,
        clocks: List<ClockSample>,
        agcEvents: List<AgcEventSample>,
        agcDiagnostics: AgcParseDiagnostics,
        profile: IntegrityProfile
    ): IntegrityReport {
        val sortedClocks = clocks.sortedBy { it.eventNs }
        val cuts = continuityCuts(sortedClocks)
        val byEpoch = measurements.groupBy { it.eventNs }.toSortedMap()
        val allNs = byEpoch.keys.map { it } + sortedClocks.map { it.eventNs } + agcEvents.map { it.eventNs }
        val firstNs = allNs.minOrNull() ?: 0L
        val span = if (allNs.isEmpty()) null else (allNs.maxOrNull()!! - firstNs) / 1e9
        val epochTimes = byEpoch.keys.map { (it - firstNs) / 1e9 }
        val epochDt = RobustStats.median(epochTimes.zipWithNext { a, b -> b - a }.filter { it > 0 }) ?: 1.0

        val (cn0, cn0Scan) = cn0Indicator(byEpoch, cuts, firstNs, profile)
        val baseAgc = agcIndicator(byEpoch, agcEvents, cuts, firstNs, profile, cn0Scan, epochDt)
        val agc = baseAgc.copy(
            metrics = baseAgc.metrics + listOf(
                IndicatorMetric("event_values_valid", agcDiagnostics.validControlCount.toDouble(), "controls", agcDiagnostics.validControlCount, null),
                IndicatorMetric("event_values_invalid_level", agcDiagnostics.invalidLevelCount.toDouble(), "controls rejected", agcDiagnostics.invalidLevelCount, null),
                IndicatorMetric("event_values_malformed_entry", agcDiagnostics.malformedEntryCount.toDouble(), "entries rejected", agcDiagnostics.malformedEntryCount, null),
                IndicatorMetric("events_empty_agc_array", agcDiagnostics.emptyArrayEvents.toDouble(), "events", agcDiagnostics.emptyArrayEvents, null),
                IndicatorMetric("events_missing_agc_array", agcDiagnostics.unsupportedOrMissingArrayEvents.toDouble(), "events", agcDiagnostics.unsupportedOrMissingArrayEvents, null)
            ),
            summary = baseAgc.summary + "; valid=${agcDiagnostics.validControlCount}, invalid level=${agcDiagnostics.invalidLevelCount}, malformed=${agcDiagnostics.malformedEntryCount}, empty array events=${agcDiagnostics.emptyArrayEvents}, missing array events=${agcDiagnostics.unsupportedOrMissingArrayEvents}"
        )
        val clock = clockIndicator(sortedClocks, profile)
        val adr = adrIndicator(measurements, cuts, profile)
        val indicators = listOf(cn0, agc, clock, adr)

        val assessment = when {
            indicators.all { it.status == IndicatorStatus.UNAVAILABLE } -> IntegrityAssessment.UNAVAILABLE
            indicators.any { it.status == IndicatorStatus.DEVIATES } -> IntegrityAssessment.SUSPECT
            else -> IntegrityAssessment.NOT_ESTABLISHED
        }
        val reasons = indicators.filter { it.status == IndicatorStatus.DEVIATES }.flatMap { it.reasonCodes }.distinct()
        return IntegrityReport(assessment, reasons, indicators, profile.id, byEpoch.size, span, NOTES)
    }

    // ---------- helpers ----------

    private fun band(hz: Double?): Int = GnssCarrierBand.bucketMHz(hz)

    /** Epoch times (ns) at which a clock discontinuity or a full-tracking transition starts a new continuity segment. */
    private fun continuityCuts(sorted: List<ClockSample>): LongArray {
        val cuts = ArrayList<Long>()
        var lastCount: Int? = null
        var lastFull: Boolean? = null
        for (c in sorted) {
            var cut = false
            val cnt = c.discontinuityCount
            if (cnt != null) {
                if (lastCount != null && cnt != lastCount) cut = true
                lastCount = cnt
            }
            val full = c.fullTracking
            if (full != null) {
                if (lastFull != null && full != lastFull) cut = true
                lastFull = full
            }
            if (cut) cuts.add(c.eventNs)
        }
        return cuts.distinct().sorted().toLongArray()
    }

    private fun segmentIndex(cuts: LongArray, ns: Long): Int {
        val i = cuts.binarySearch(ns)
        return if (i >= 0) i + 1 else -(i + 1)
    }

    private class Series(val ns: LongArray, val values: DoubleArray)

    private fun scanSeries(series: Series, cuts: LongArray, firstNs: Long, floor: Double, p: IntegrityProfile): ShiftScan {
        if (series.ns.isEmpty()) return ShiftScan.EMPTY
        var result = ShiftScan.EMPTY
        var start = 0
        fun flush(endExclusive: Int) {
            val n = endExclusive - start
            if (n >= p.minBaselineEpochs + p.recentEpochs) {
                val secs = DoubleArray(n) { (series.ns[start + it] - firstNs) / 1e9 }
                val vals = series.values.copyOfRange(start, endExclusive)
                result = result.merge(
                    scanRobustShift(vals, secs, p.recentEpochs, p.minBaselineEpochs, p.maxBaselineEpochs, floor, p.zGate, p.scanStride)
                )
            }
            start = endExclusive
        }
        for (i in 1 until series.ns.size) {
            val gap = (series.ns[i] - series.ns[i - 1]) / 1e9
            if (gap > p.maxGapSeconds || segmentIndex(cuts, series.ns[i]) != segmentIndex(cuts, series.ns[i - 1])) flush(i)
        }
        flush(series.ns.size)
        return result
    }

    private class Acc {
        val ns = ArrayList<Long>()
        val level = ArrayList<Double>()
        val disp = ArrayList<Double>()
    }

    private fun fmt(v: Double?, digits: Int = 1): String = v?.let { String.format(java.util.Locale.US, "%.${digits}f", it) } ?: "n/a"

    // ---------- C/N0 ----------

    private val CN0_CONFOUNDERS = listOf(
        "sky view / obstruction change", "device motion or antenna orientation", "body shadowing", "urban multipath",
        "receiver mode / duty-cycling change", "power saving", "satellite set change"
    )

    private fun cn0Indicator(
        byEpoch: Map<Long, List<MeasurementSample>>,
        cuts: LongArray,
        firstNs: Long,
        p: IntegrityProfile
    ): Pair<IndicatorResult, ShiftScan> {
        val groups = LinkedHashMap<String, Acc>()
        var anyCn0 = false
        for ((ns, list) in byEpoch) {
            val valid = list.filter { val c = it.cn0DbHz; c != null && c.isFinite() && c > 0.0 }
            if (valid.isNotEmpty()) anyCn0 = true
            fun add(key: String, vals: List<Double>, minN: Int) {
                if (vals.size < minN) return
                val acc = groups.getOrPut(key) { Acc() }
                val m = RobustStats.median(vals)!!
                acc.ns.add(ns); acc.level.add(m); acc.disp.add(RobustStats.madSigma(vals, m)!!)
            }
            add("ALL", valid.map { it.cn0DbHz!! }, p.minSatellitesPerEpoch)
            valid.groupBy { it.constellationType to band(it.carrierFrequencyHz) }
                .forEach { (k, v) -> add("C${k.first}/B${k.second}MHz", v.map { it.cn0DbHz!! }, p.minSatellitesPerGroupEpoch) }
        }
        var worstLevel = ShiftScan.EMPTY
        var worstLevelKey = "-"
        var worstDisp = ShiftScan.EMPTY
        var worstDispKey = "-"
        var windows = 0
        var levelExceed = 0
        var dispExceed = 0
        var earliestLevelExceed: Double? = null
        for ((key, acc) in groups) {
            val ns = acc.ns.toLongArray()
            val lv = scanSeries(Series(ns, acc.level.toDoubleArray()), cuts, firstNs, p.cn0LevelFloorDb, p)
            val dv = scanSeries(Series(ns, acc.disp.toDoubleArray()), cuts, firstNs, p.cn0DispersionFloorDb, p)
            windows += lv.windows
            levelExceed += lv.exceeding
            lv.firstExceedSeconds?.let { f -> earliestLevelExceed = earliestLevelExceed?.let { e -> minOf(e, f) } ?: f }
            dispExceed += dv.exceeding
            if (lv.maxAbsZ > worstLevel.maxAbsZ) { worstLevel = lv; worstLevelKey = key }
            if (dv.maxAbsZ > worstDisp.maxAbsZ) { worstDisp = dv; worstDispKey = key }
        }
        val epochs = groups["ALL"]?.ns?.size ?: 0
        val status: IndicatorStatus
        val reasons = ArrayList<String>()
        when {
            !anyCn0 -> { status = IndicatorStatus.UNAVAILABLE; reasons.add("CN0_NOT_EXPOSED") }
            windows == 0 -> { status = IndicatorStatus.INSUFFICIENT_HISTORY; reasons.add("CN0_HISTORY_TOO_SHORT") }
            levelExceed > 0 || dispExceed > 0 -> {
                status = IndicatorStatus.DEVIATES
                if (levelExceed > 0) reasons.add("CN0_COMMON_MODE_SHIFT")
                if (dispExceed > 0) reasons.add("CN0_DISPERSION_SHIFT")
            }
            else -> status = IndicatorStatus.WITHIN_BASELINE
        }
        val summary = when (status) {
            IndicatorStatus.UNAVAILABLE -> "C/N0 not exposed (not evidence either way)."
            IndicatorStatus.INSUFFICIENT_HISTORY -> "Not enough continuous epochs (need ≥ ${p.minBaselineEpochs + p.recentEpochs} in one segment)."
            else -> "Max level shift |z| = ${fmt(worstLevel.maxAbsZ)} (${worstLevelKey}), max dispersion shift |z| = ${fmt(worstDisp.maxAbsZ)} (${worstDispKey}), gate ${fmt(p.zGate)}."
        }
        val window = p.recentEpochs.toDouble()
        val result = IndicatorResult(
            "cn0", "C/N0 level and cross-satellite dispersion", status, reasons,
            listOf(
                IndicatorMetric("epochs_all", epochs.toDouble(), "epochs", epochs, null),
                IndicatorMetric("scan_windows", windows.toDouble(), "windows", windows, window),
                IndicatorMetric("level_shift_max_abs_z", worstLevel.maxAbsZ.takeIf { worstLevel.windows > 0 }, "robust z", worstLevel.windows, window),
                IndicatorMetric("level_shift_at", worstLevel.atSeconds, "s from start", worstLevel.windows, window),
                IndicatorMetric("level_shift_onset", earliestLevelExceed, "s from start (first window over gate)", worstLevel.windows, window),
                IndicatorMetric("dispersion_shift_max_abs_z", worstDisp.maxAbsZ.takeIf { worstDisp.windows > 0 }, "robust z", worstDisp.windows, window),
                IndicatorMetric("dispersion_shift_at", worstDisp.atSeconds, "s from start", worstDisp.windows, window)
            ),
            summary, CN0_CONFOUNDERS
        )
        return result to ShiftScan(windows, worstLevel.maxAbsZ, worstLevel.maxZ, worstLevel.atSeconds, levelExceed, earliestLevelExceed)
    }

    // ---------- AGC ----------

    private val AGC_CONFOUNDERS = listOf(
        "receiver gain-control behaviour differs per chipset (sign/units must be confirmed per device)",
        "environmental power change (sky view, nearby emitters)", "receiver mode change", "device motion"
    )

    private fun agcIndicator(
        byEpoch: Map<Long, List<MeasurementSample>>,
        agcEvents: List<AgcEventSample>,
        cuts: LongArray,
        firstNs: Long,
        p: IntegrityProfile,
        cn0Scan: ShiftScan,
        epochDtS: Double
    ): IndicatorResult {
        fun toSeries(m: Map<Long, List<Double>>): Series? {
            val keys = m.keys.sorted()
            val vals = keys.mapNotNull { k -> RobustStats.median(m[k]!!) }
            return if (keys.isEmpty()) null else Series(keys.toLongArray(), vals.toDoubleArray())
        }
        val eventSeriesByGroup = agcEvents
            .filter { it.levelDb.isFinite() }
            .groupBy { event ->
                val constellation = event.constellationType?.toString() ?: "UNKNOWN"
                val frequencyHz = event.carrierFrequencyHz?.takeIf { it.isFinite() && it > 0.0 }
                val carrier = frequencyHz?.let { (it / 1.0e6).roundToInt().toString() + "MHz" } ?: "UNKNOWN_BAND"
                "C$constellation/B$carrier"
            }
            .mapNotNull { (key, samples) ->
                val series = toSeries(samples.groupBy({ it.eventNs }, { it.levelDb })) ?: return@mapNotNull null
                key to series
            }
            .sortedBy { it.first }
        val eventSeries = toSeries(agcEvents.filter { it.levelDb.isFinite() }.groupBy({ it.eventNs }, { it.levelDb }))
        val measSeries = toSeries(
            byEpoch.mapValues { (_, l) -> l.mapNotNull { it.agcDb }.filter { it.isFinite() } }.filterValues { it.isNotEmpty() }
        )
        val need = p.minBaselineEpochs + p.recentEpochs
        val chosenEventGroups = eventSeriesByGroup.filter { it.second.ns.size >= need }
        val chosenLegacy = measSeries?.takeIf { it.ns.size >= need }
        val anyData = eventSeriesByGroup.isNotEmpty() || measSeries != null
        val reasons = ArrayList<String>()
        if (chosenEventGroups.isEmpty() && chosenLegacy == null) {
            val status = if (anyData) IndicatorStatus.INSUFFICIENT_HISTORY else IndicatorStatus.UNAVAILABLE
            reasons.add(if (anyData) "AGC_HISTORY_TOO_SHORT" else "AGC_NOT_EXPOSED")
            return IndicatorResult(
                "agc", "Automatic gain control level", status, reasons, emptyList(),
                if (anyData) "AGC exposed but too few continuous epochs." else "AGC not exposed by this device/API (not evidence either way).",
                AGC_CONFOUNDERS
            )
        }
        val scans = if (chosenEventGroups.isNotEmpty()) {
            chosenEventGroups.map { (key, series) -> key to scanSeries(series, cuts, firstNs, p.agcFloorDb, p) }
        } else {
            listOf("MEASUREMENT_LEVEL_DEPRECATED" to scanSeries(chosenLegacy!!, cuts, firstNs, p.agcFloorDb, p))
        }
        val scan = scans.map { it.second }.fold(ShiftScan.EMPTY) { acc, value -> acc.merge(value) }
        reasons.add(if (chosenEventGroups.isNotEmpty()) "AGC_SOURCE_EVENT_LEVEL" else "AGC_SOURCE_MEASUREMENT_LEVEL_DEPRECATED")
        val status: IndicatorStatus
        if (scan.windows == 0) {
            status = IndicatorStatus.INSUFFICIENT_HISTORY
            reasons.add("AGC_HISTORY_TOO_SHORT")
        } else if (scan.exceeding > 0) {
            status = IndicatorStatus.DEVIATES
            reasons.add("AGC_LEVEL_STEP")
            reasons.addAll(scans.filter { it.second.exceeding > 0 }.map { "AGC_GROUP_SHIFT" })
            val a = scan.firstExceedSeconds
            val c = cn0Scan.firstExceedSeconds
            if (cn0Scan.exceeding > 0 && a != null && c != null && abs(a - c) <= 2.0 * p.recentEpochs * epochDtS) {
                reasons.add("AGC_AND_CN0_SHIFT_CONCURRENT")
            }
        } else {
            status = IndicatorStatus.WITHIN_BASELINE
        }
        return IndicatorResult(
            "agc", "Automatic gain control level", status, reasons,
            buildList {
                add(IndicatorMetric("level_shift_max_abs_z", scan.maxAbsZ.takeIf { scan.windows > 0 }, "robust z", scan.windows, p.recentEpochs.toDouble()))
                add(IndicatorMetric("level_shift_signed_z", scan.maxZ.takeIf { scan.windows > 0 }, "robust z (sign not interpreted)", scan.windows, p.recentEpochs.toDouble()))
                add(IndicatorMetric("level_shift_at", scan.atSeconds, "s from start", scan.windows, null))
                add(IndicatorMetric("level_shift_onset", scan.firstExceedSeconds, "s from start (first window over gate)", scan.windows, null))
                scans.forEach { (key, value) ->
                    val group = key.replace(Regex("[^A-Za-z0-9]"), "_")
                    add(IndicatorMetric("agc_group_${group}_windows_evaluated", value.windows.toDouble(), "windows", value.windows, p.recentEpochs.toDouble()))
                    add(IndicatorMetric("agc_group_${group}_max_abs_z", value.maxAbsZ.takeIf { value.windows > 0 }, "robust z", value.windows, p.recentEpochs.toDouble()))
                    add(IndicatorMetric("agc_group_${group}_onset", value.firstExceedSeconds, "s from start", value.windows, null))
                }
            },
            "Source ${if (chosenEventGroups.isNotEmpty()) "EVENT_LEVEL" else "MEASUREMENT_LEVEL_DEPRECATED"}; group series=${scans.size}; ${scans.filter { it.second.exceeding > 0 }.joinToString { it.first + " shifted" }.ifBlank { "no group-specific shift crossed the gate" }}; max level shift |z| = ${fmt(scan.maxAbsZ)}, gate ${fmt(p.zGate)}. Sign of the step is not interpreted.",
            AGC_CONFOUNDERS
        )
    }

    // ---------- receiver clock ----------

    private val CLOCK_CONFOUNDERS = listOf(
        "receiver or app restart", "duty-cycling / power management", "temperature change of the oscillator", "clock steering by the receiver"
    )

    private fun clockIndicator(sorted: List<ClockSample>, p: IntegrityProfile): IndicatorResult {
        if (sorted.isEmpty()) {
            return IndicatorResult(
                "clock", "Receiver clock continuity and drift acceleration", IndicatorStatus.UNAVAILABLE, listOf("CLOCK_NOT_CAPTURED"), emptyList(),
                "No GNSS_CLOCK evidence in this session (recorded by app versions that capture the clock; older sessions have none).",
                CLOCK_CONFOUNDERS
            )
        }
        // discontinuity changes
        var changes = 0
        var last: Int? = null
        for (c in sorted) {
            val cnt = c.discontinuityCount ?: continue
            if (last != null && cnt != last) changes++
            last = cnt
        }
        // drift acceleration inside continuity segments
        val usable = sorted.filter { val d = it.driftNsPerS; d != null && d.isFinite() }
        var segmentsEvaluated = 0
        var samplesEvaluated = 0
        var outliers = 0
        var maxAbsZ = 0.0
        if (usable.size >= 2) {
            var seg = ArrayList<ClockSample>()
            fun flush() {
                if (seg.size >= p.minClockSegmentSamples) {
                    val acc = ArrayList<Double>()
                    for (i in 1 until seg.size) {
                        val dt = (seg[i].eventNs - seg[i - 1].eventNs) / 1e9
                        if (dt > 0.0) acc.add((seg[i].driftNsPerS!! - seg[i - 1].driftNsPerS!!) / dt)
                    }
                    if (acc.size >= p.minClockSegmentSamples - 1) {
                        val center = RobustStats.median(acc)!!
                        val sigma = maxOf(RobustStats.madSigma(acc, center)!!, p.driftAccelFloorNsPerS2)
                        segmentsEvaluated++
                        samplesEvaluated += seg.size
                        for (a in acc) {
                            val z = abs((a - center) / sigma)
                            if (z > maxAbsZ) maxAbsZ = z
                            if (z >= p.zGate) outliers++
                        }
                    }
                }
                seg = ArrayList()
            }
            var prev: ClockSample? = null
            for (c in usable) {
                val pr = prev
                if (pr != null) {
                    val gap = (c.eventNs - pr.eventNs) / 1e9
                    val countChanged = pr.discontinuityCount != null && c.discontinuityCount != null && pr.discontinuityCount != c.discontinuityCount
                    val fullChanged = pr.fullTracking != null && c.fullTracking != null && pr.fullTracking != c.fullTracking
                    if (gap > p.maxGapSeconds || countChanged || fullChanged) flush()
                }
                seg.add(c)
                prev = c
            }
            flush()
        }
        val knownFull = sorted.mapNotNull { it.fullTracking }
        val dutyFraction = if (knownFull.isEmpty()) null else knownFull.count { !it }.toDouble() / knownFull.size
        val reasons = ArrayList<String>()
        val status: IndicatorStatus
        when {
            changes > 0 || outliers >= p.driftOutlierCountGate -> {
                status = IndicatorStatus.DEVIATES
                if (changes > 0) reasons.add("CLOCK_DISCONTINUITY_OBSERVED")
                if (outliers >= p.driftOutlierCountGate) reasons.add("DRIFT_ACCELERATION_OUTLIER")
            }
            usable.isEmpty() -> { status = IndicatorStatus.UNAVAILABLE; reasons.add("CLOCK_DRIFT_NOT_EXPOSED") }
            segmentsEvaluated == 0 -> { status = IndicatorStatus.INSUFFICIENT_HISTORY; reasons.add("CLOCK_HISTORY_TOO_SHORT") }
            else -> status = IndicatorStatus.WITHIN_BASELINE
        }
        return IndicatorResult(
            "clock", "Receiver clock continuity and drift acceleration", status, reasons,
            listOf(
                IndicatorMetric("discontinuity_count_changes", changes.toDouble(), "changes", sorted.size, null),
                IndicatorMetric("drift_segments_evaluated", segmentsEvaluated.toDouble(), "segments", samplesEvaluated, null),
                IndicatorMetric("drift_accel_max_abs_z", maxAbsZ.takeIf { segmentsEvaluated > 0 }, "robust z", samplesEvaluated, null),
                IndicatorMetric("drift_accel_outliers", outliers.toDouble(), "samples", samplesEvaluated, null),
                IndicatorMetric("duty_cycled_fraction", dutyFraction, "fraction", knownFull.size, null)
            ),
            "Discontinuity-count changes: $changes (receiver-reported); drift-acceleration outliers: $outliers over $segmentsEvaluated segment(s), gate ${fmt(p.zGate)}. Series are segmented at discontinuities (never differentiated across a reset).",
            CLOCK_CONFOUNDERS
        )
    }

    // ---------- ADR vs range-rate consistency ----------

    private val ADR_CONFOUNDERS = listOf(
        "obstruction or loss of lock not flagged by the receiver", "receiver tracking-loop changes", "vendor-specific ADR sign/scale conventions",
        "device motion causing tracking-loop transients"
    )

    private class AdrInterval(val key: Triple<Int, Int, Int>, val dAdr: Double, val x: Double)

    private fun adrIndicator(measurements: List<MeasurementSample>, cuts: LongArray, p: IntegrityProfile): IndicatorResult {
        val tracks = measurements
            .filter { val a = it.adrM; val r = it.rangeRateMps; a != null && a.isFinite() && it.adrState != null && r != null && r.isFinite() }
            .groupBy { Triple(it.constellationType, it.svid, band(it.carrierFrequencyHz)) }
        if (tracks.isEmpty()) {
            return IndicatorResult(
                "adr", "Carrier-phase (ADR) vs range-rate consistency", IndicatorStatus.UNAVAILABLE, listOf("ADR_NOT_EXPOSED"), emptyList(),
                "ADR/range-rate not exposed (not evidence either way).", ADR_CONFOUNDERS
            )
        }
        var resets = 0
        var slips = 0
        var halfCycle = 0
        val intervals = ArrayList<AdrInterval>()
        for ((key, list) in tracks) {
            val s = list.sortedBy { it.eventNs }
            for (i in 1 until s.size) {
                val a = s[i - 1]
                val b = s[i]
                val sa = a.adrState!!
                val sb = b.adrState!!
                if (sb and ADR_RESET != 0) resets++
                if (sb and ADR_CYCLE_SLIP != 0) slips++
                val ambiguous = (sa and ADR_HALF_CYCLE_REPORTED != 0 && sa and ADR_HALF_CYCLE_RESOLVED == 0) ||
                    (sb and ADR_HALF_CYCLE_REPORTED != 0 && sb and ADR_HALF_CYCLE_RESOLVED == 0)
                if (ambiguous) halfCycle++
                val dt = (b.eventNs - a.eventNs) / 1e9
                val ok = sa and ADR_VALID != 0 && sb and ADR_VALID != 0 &&
                    sb and (ADR_RESET or ADR_CYCLE_SLIP) == 0 && !ambiguous &&
                    dt > 0.0 && dt <= p.maxGapSeconds && segmentIndex(cuts, a.eventNs) == segmentIndex(cuts, b.eventNs)
                if (ok) intervals.add(AdrInterval(key, b.adrM!! - a.adrM!!, 0.5 * (a.rangeRateMps!! + b.rangeRateMps!!) * dt))
            }
        }
        val flagMetrics = listOf(
            IndicatorMetric("adr_reset_flags", resets.toDouble(), "samples", intervals.size, null),
            IndicatorMetric("adr_cycle_slip_flags", slips.toDouble(), "samples", intervals.size, null),
            IndicatorMetric("adr_half_cycle_ambiguous", halfCycle.toDouble(), "samples", intervals.size, null)
        )
        val info = if (resets + slips > 0) listOf("ADR_RESET_OR_CYCLE_SLIP_FLAGS_PRESENT") else emptyList()
        if (intervals.size < p.adrMinIntervals) {
            return IndicatorResult(
                "adr", "Carrier-phase (ADR) vs range-rate consistency", IndicatorStatus.INSUFFICIENT_HISTORY,
                listOf("ADR_HISTORY_TOO_SHORT") + info, flagMetrics,
                "Only ${intervals.size} valid ADR intervals (need ≥ ${p.adrMinIntervals}); reset/cycle-slip/half-cycle intervals are excluded.", ADR_CONFOUNDERS
            )
        }
        // The sign convention between ADR and range-rate is estimated from the data (reported), not assumed.
        val sign = if (intervals.sumOf { it.dAdr * it.x } >= 0.0) 1.0 else -1.0
        val res = intervals.map { it.dAdr - sign * it.x }
        val center = RobustStats.median(res)!!
        val sigma = maxOf(RobustStats.madSigma(res, center)!!, p.adrSigmaFloorM)
        val outlierKeys = ArrayList<Triple<Int, Int, Int>>()
        for (i in intervals.indices) if (abs((res[i] - center) / sigma) >= p.adrOutlierZ) outlierKeys.add(intervals[i].key)
        val absRes = res.map { abs(it) }
        val reasons = ArrayList<String>(info)
        val status = if (outlierKeys.size >= p.adrOutlierIntervalsGate) {
            reasons.add("ADR_RANGE_RATE_INCONSISTENT"); IndicatorStatus.DEVIATES
        } else IndicatorStatus.WITHIN_BASELINE
        val worstTrack = outlierKeys.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return IndicatorResult(
            "adr", "Carrier-phase (ADR) vs range-rate consistency", status, reasons,
            flagMetrics + listOf(
                IndicatorMetric("valid_intervals", intervals.size.toDouble(), "intervals", intervals.size, null),
                IndicatorMetric("sign_convention", sign, "+1 / -1 (estimated)", intervals.size, null),
                IndicatorMetric("residual_median_abs", RobustStats.median(absRes), "m", intervals.size, null),
                IndicatorMetric("residual_p95_abs", RobustStats.quantile(absRes, 0.95), "m", intervals.size, null),
                IndicatorMetric("outlier_intervals", outlierKeys.size.toDouble(), "intervals", intervals.size, null)
            ),
            "Residual ΔADR − s·∫ṙ dt: median |r| = ${fmt(RobustStats.median(absRes), 3)} m; ${outlierKeys.size} outlier interval(s) (|z| ≥ ${fmt(p.adrOutlierZ)})" +
                (worstTrack?.let { "; most affected track constellation ${it.first} svid ${it.second}" } ?: "") + ". Flagged resets/slips are excluded, not counted as anomalies.",
            ADR_CONFOUNDERS
        )
    }
}
