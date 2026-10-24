package horizon.observatory.resilience

import horizon.observatory.domain.model.ObservationType
import horizon.observatory.domain.model.RawObservation
import org.json.JSONObject

/**
 * Named thresholds for last-valid-PVT freshness and recovery gating.
 * PROVISIONAL until calibrated on recorded traces. All durations are in the elapsed-realtime domain
 * (SystemClock.elapsedRealtimeNanos()), which is only comparable within one boot.
 */
data class PvtFreshnessPolicy(
    val staleAfterMs: Long = 5_000L,
    val healthyHorizontalUncertaintyM: Double = 50.0,
    val recoveryMinConsecutiveFixes: Int = 3,
    val recoveryMinDwellMs: Long = 2_000L,
    val maxFutureSkewMs: Long = 1_000L,
    val imuFreshMs: Long = 2_000L,
    val estimatePeriodMs: Long = 1_000L
) {
    init {
        require(staleAfterMs >= 0L) { "staleAfterMs must be non-negative" }
        require(healthyHorizontalUncertaintyM.isFinite() && healthyHorizontalUncertaintyM >= 0.0) {
            "healthyHorizontalUncertaintyM must be finite and non-negative"
        }
        require(recoveryMinConsecutiveFixes >= 1) { "recoveryMinConsecutiveFixes must be at least one" }
        require(recoveryMinDwellMs >= 0L) { "recoveryMinDwellMs must be non-negative" }
        require(maxFutureSkewMs >= 0L) { "maxFutureSkewMs must be non-negative" }
        require(imuFreshMs >= 0L) { "imuFreshMs must be non-negative" }
        require(estimatePeriodMs >= 1L) { "estimatePeriodMs must be positive" }
        val maxDurationMs = Long.MAX_VALUE / 1_000_000L
        require(staleAfterMs <= maxDurationMs && recoveryMinDwellMs <= maxDurationMs &&
            maxFutureSkewMs <= maxDurationMs && imuFreshMs <= maxDurationMs && estimatePeriodMs <= maxDurationMs) {
            "duration policy overflows nanosecond conversion"
        }
    }
}

/**
 * Derived-evidence runtime. Raw observations are never modified.
 *
 * Clocks are kept separate: GNSS status time, GNSS raw-measurement time and last-valid-PVT time.
 * Only a new, validated position solution moves the last-valid-PVT clock; status or measurement
 * callbacks can never make an old coordinate look current. "Now" is the arrival (ingestion) time and
 * never moves backwards, so out-of-order or old-timestamped events cannot rejuvenate anything.
 */
class ResilienceRuntime(
    private val pvtPolicy: PvtFreshnessPolicy = PvtFreshnessPolicy(),
    coastingPolicy: CoastingPolicy = CoastingPolicy(),
    consistencyPolicy: ConsistencyPolicy = ConsistencyPolicy()
) {
    private val interferenceEngine = InterferenceEvidenceEngine()
    private val receptionOptimization = ReceptionOptimizationEngine(ReceptionOptimizationPolicy(enabled = true))
    private val stateMachine = NavigationContinuityStateMachine(transitionDebounceSamples = 2)
    private val deadReckoning = DeadReckoningBridge(coastingPolicy)
    private val consistency = PositionConsistencyChecker(consistencyPolicy)
    private val previousMeasurements = mutableMapOf<String, PreviousMeasurement>()
    private val externalEvidence = mutableMapOf<String, PositionEvidence>()
    private var baselineCn0: Double? = null
    private var baselineSatellites: Int? = null

    private var lastGnssStatusNs: Long? = null
    private var lastGnssMeasurementNs: Long? = null
    private var lastValidPvtNs: Long? = null
    private var lastEvaluationNs: Long? = null
    private var hadGnssEvidence = false
    private var latestTrusted: TrustedPvt? = null
    private var consecutiveValidPvt = 0
    private var pvtStreakStartNs: Long? = null
    private var rejectedPvtCount = 0
    private var outOfOrderPvtCount = 0
    private var staleOnArrivalPvtCount = 0

    private var lastInterferenceState: InterferenceState? = null
    private var lastInterferenceAssessment: InterferenceAssessment? = null
    private var imuSeenNs: Long? = null
    private var cellSeenNs: Long? = null
    private var lastComputedPvtAgeMs: Long? = null
    private var lastTrustDecisions: List<TrustDecisionRecord> = emptyList()
    private var interferenceStateChanged = false
    private var lastEstimateNs: Long? = null
    private var lastReportedCoastingRejection: CoastingRejection? = null
    private var lastConsistency: ConsistencyDecision? = null

    val navigationState: NavigationState
        get() = stateMachine.state

    val debugLastValidPvtNs: Long? get() = lastValidPvtNs
    val debugLastGnssStatusNs: Long? get() = lastGnssStatusNs
    val debugLastGnssMeasurementNs: Long? get() = lastGnssMeasurementNs
    val debugLastPvtAgeMs: Long? get() = lastComputedPvtAgeMs
    val debugRejectedPvtCount: Int get() = rejectedPvtCount
    val debugOutOfOrderPvtCount: Int get() = outOfOrderPvtCount
    val debugStaleOnArrivalPvtCount: Int get() = staleOnArrivalPvtCount
    val debugLastConsistency: ConsistencyDecision? get() = lastConsistency
    /** Cellular-info callbacks are tracked for diagnostics only; they carry no coordinates. */
    val debugLastCellularInfoNs: Long? get() = cellSeenNs

    /**
     * Supplies an independent position-relevant estimate (for example a second receiver). It is only
     * stored; it takes effect, together with the consistency decision, at the next [accept].
     * Rejects invalid items and items older than the one already held for the same source.
     */
    fun acceptPositionEvidence(evidence: PositionEvidence): Boolean {
        if (evidence.sourceId.isBlank() || evidence.independenceGroup.isBlank()) return false
        if (!evidence.latitudeDeg.isFinite() || evidence.latitudeDeg < -90.0 || evidence.latitudeDeg > 90.0) return false
        if (!evidence.longitudeDeg.isFinite() || evidence.longitudeDeg < -180.0 || evidence.longitudeDeg > 180.0) return false
        if (!evidence.horizontalUncertaintyM.isFinite() || evidence.horizontalUncertaintyM <= 0.0) return false
        if (evidence.timestampMonotonicNs <= 0L) return false
        val existing = externalEvidence[evidence.sourceId]
        if (existing != null && evidence.timestampMonotonicNs <= existing.timestampMonotonicNs) return false
        externalEvidence[evidence.sourceId] = evidence
        return true
    }

    fun accept(observation: RawObservation): ResilienceRuntimeUpdate? {
        interferenceStateChanged = false
        val payload = runCatching { JSONObject(observation.payloadJson) }.getOrNull()
        val sourceNs = observation.monotonicTimestampNs
        val arrivalNs = observation.ingestionMonotonicTimestampNs ?: sourceNs
        val now: Long? = arrivalNs?.let { maxOf(it, lastEvaluationNs ?: it) }
        when (observation.type) {
            ObservationType.GNSS_STATUS -> {
                val tracked = payload?.optInt("satelliteCount", -1)?.takeIf { it >= 0 }
                val satellites = payload?.optJSONArray("satellites")
                val used = satellites?.let { a -> (0 until a.length()).count { a.optJSONObject(it)?.optBoolean("usedInFix", false) == true } }
                val cn0 = satellites?.let { a ->
                    val values = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optDouble("cn0DbHz", Double.NaN)?.takeUnless(Double::isNaN) }.sorted()
                    values.getOrNull(values.size / 2)
                }
                if (baselineCn0 == null && cn0 != null) baselineCn0 = cn0
                if (baselineSatellites == null && tracked != null) baselineSatellites = tracked
                latest(lastGnssStatusNs, sourceNs ?: now)?.let { lastGnssStatusNs = it }
                hadGnssEvidence = true
                val previousState = lastInterferenceState
                val assessment = interferenceEngine.assess(
                    InterferenceSnapshot(
                        cn0MedianDbHz = cn0,
                        baselineCn0MedianDbHz = baselineCn0,
                        trackedSatelliteCount = tracked,
                        usedInFixSatelliteCount = used,
                        baselineTrackedSatelliteCount = baselineSatellites,
                        pvtDegraded = latestTrusted?.horizontalUncertaintyM?.let { it > pvtPolicy.healthyHorizontalUncertaintyM }
                    )
                )
                lastInterferenceAssessment = assessment
                lastInterferenceState = assessment.state
                interferenceStateChanged = previousState != assessment.state
            }
            ObservationType.GNSS_RAW_MEASUREMENT -> {
                latest(lastGnssMeasurementNs, sourceNs ?: now)?.let { lastGnssMeasurementNs = it }
                val constellation = payload?.optInt("constellationType", -1)?.takeIf { it >= 0 }
                val svid = payload?.optInt("svid", -1)?.takeIf { it >= 0 }
                val frequencyHz = payload?.optDouble("carrierFrequencyHz", Double.NaN)?.takeUnless(Double::isNaN)
                val key = "${constellation ?: -1}:${svid ?: -1}:${frequencyHz ?: 0.0}"
                val cn0 = payload?.optDouble("cn0DbHz", Double.NaN)?.takeUnless(Double::isNaN)
                val pseudorangeRate = payload?.optDouble("pseudorangeRateMetersPerSecond", Double.NaN)?.takeUnless(Double::isNaN)
                val adr = payload?.optDouble("accumulatedDeltaRangeMeters", Double.NaN)?.takeUnless(Double::isNaN)
                val previous = previousMeasurements[key]
                val deltaNs = previous?.timestampNs?.let { (observation.monotonicTimestampNs ?: it) - it }
                val continuity = deltaNs?.let { (1.0 - (it.coerceAtLeast(0L) / 5_000_000_000.0)).coerceIn(0.0, 1.0) }
                val signalStability = cn0?.let { (it / 50.0).coerceIn(0.0, 1.0) }
                val dopplerConsistency = if (previous?.pseudorangeRate != null && pseudorangeRate != null)
                    (1.0 - kotlin.math.abs(pseudorangeRate - previous.pseudorangeRate) / 100.0).coerceIn(0.0, 1.0) else null
                val ageMs = deltaNs?.let { it.coerceAtLeast(0L) / 1_000_000L }
                val candidate = MeasurementCandidate(
                    id = key,
                    signalStability = signalStability,
                    temporalContinuity = continuity,
                    freshness = ageMs?.let { (1.0 - it.toDouble() / 5_000.0).coerceIn(0.0, 1.0) },
                    ageMs = ageMs,
                    dopplerConsistency = dopplerConsistency
                )
                val trust = receptionOptimization.rank(listOf(candidate), nowMonotonicMs = observation.monotonicTimestampNs?.div(1_000_000L))
                previousMeasurements[key] = PreviousMeasurement(observation.monotonicTimestampNs ?: 0L, pseudorangeRate, adr)
                lastTrustDecisions = trust
            }
            ObservationType.GNSS_FIX -> if (now != null) acceptFix(payload, sourceNs, now) else rejectedPvtCount++
            ObservationType.SENSOR_ACCEL, ObservationType.SENSOR_GYRO,
            ObservationType.SENSOR_ROTATION, ObservationType.SENSOR_GRAVITY -> latest(imuSeenNs, sourceNs ?: now)?.let { imuSeenNs = it }
            ObservationType.CELLULAR_INFO -> latest(cellSeenNs, sourceNs ?: now)?.let { cellSeenNs = it }
            else -> Unit
        }

        if (now == null) return null
        lastEvaluationNs = now
        val pvtNs = lastValidPvtNs
        val pvtAgeMs = pvtNs?.let { (now - it).coerceAtLeast(0L) / 1_000_000L }
        val statusAgeMs = lastGnssStatusNs?.let { (now - it).coerceAtLeast(0L) / 1_000_000L }
        lastComputedPvtAgeMs = pvtAgeMs
        val staleMs = pvtPolicy.staleAfterMs
        // Position currency comes from the last valid PVT alone. Only before any PVT has ever been
        // seen can status callbacks stand in as the (partial) GNSS evidence.
        val lost = if (pvtNs != null) (pvtAgeMs ?: 0L) > staleMs
        else hadGnssEvidence && (statusAgeMs ?: Long.MAX_VALUE) > staleMs
        val trusted = latestTrusted
        val healthy = !lost && trusted != null && trusted.horizontalUncertaintyM <= pvtPolicy.healthyHorizontalUncertaintyM
        val partial = !lost && hadGnssEvidence && trusted == null
        val degraded = !lost && hadGnssEvidence && !healthy && !partial
        val imuFresh = imuSeenNs?.let { now - it <= ms(pvtPolicy.imuFreshMs) } == true
        val recoveryReady = !lost && healthy && pvtNs != null &&
            consecutiveValidPvt >= pvtPolicy.recoveryMinConsecutiveFixes &&
            pvtStreakStartNs?.let { pvtNs - it >= ms(pvtPolicy.recoveryMinDwellMs) } == true

        // Coasting is only offered while the coordinate is not current and only if the projection
        // itself is supportable; an IMU callback alone is not a position.
        var coasting: CoastingResult? = null
        if (lost && trusted != null) {
            val elapsedSeconds = (now - trusted.timestampMonotonicNs).coerceAtLeast(0L) / 1_000_000_000.0
            coasting = deadReckoning.estimate(BridgeInput(trusted, elapsedSeconds, imuAvailable = imuFresh))
        }
        val coastingViable = coasting is CoastingResult.Estimate

        val decision = evaluateAgreement(now)
        val transition = stateMachine.update(
            NavigationEvidence(
                healthyGnss = healthy,
                degradedGnss = degraded,
                partialGnss = partial,
                gnssLost = lost,
                validImu = imuFresh && (!lost || coastingViable),
                // No cellular coordinate source exists: cellular information is not a position.
                validCell = false,
                multiSourceAgreement = decision is ConsistencyDecision.Agree,
                recoveryGnss = stateMachine.state != NavigationState.FULL_GNSS && recoveryReady
            )
        )

        var estimate: NavigationEstimate? = null
        var rejectionToReport: CoastingRejection? = null
        val coastingActive = lost && stateMachine.state == NavigationState.INERTIAL_BRIDGING
        if (coastingActive && coasting is CoastingResult.Estimate) {
            val last = lastEstimateNs
            val due = transition?.to == NavigationState.INERTIAL_BRIDGING || last == null || now - last >= ms(pvtPolicy.estimatePeriodMs)
            if (due) {
                estimate = coasting.estimate
                lastEstimateNs = now
            }
            lastReportedCoastingRejection = null
        } else if (lost && imuFresh && coasting is CoastingResult.Rejected) {
            if (coasting.reason != lastReportedCoastingRejection) {
                rejectionToReport = coasting.reason
                lastReportedCoastingRejection = coasting.reason
            }
        }
        if (!coastingActive) lastEstimateNs = null
        if (!lost) lastReportedCoastingRejection = null

        val interferenceChanged = interferenceStateChanged
        val meaningfulTrust = lastTrustDecisions.any { it.decision != TrustDecision.KEEP }
        val meaningfulInterference = observation.type == ObservationType.GNSS_STATUS && interferenceChanged
        if (transition == null && !meaningfulTrust && !meaningfulInterference && estimate == null && rejectionToReport == null) return null
        val update = ResilienceRuntimeUpdate(
            navigationState = stateMachine.state,
            interferenceState = lastInterferenceState ?: InterferenceState.UNKNOWN,
            interferenceAssessment = lastInterferenceAssessment,
            trustDecisions = lastTrustDecisions,
            transition = transition,
            estimate = estimate,
            provenance = "RESILIENCE_RUNTIME_DERIVED",
            coastingRejection = rejectionToReport
        )
        lastTrustDecisions = emptyList()
        return update
    }

    private fun acceptFix(payload: JSONObject?, sourceNs: Long?, now: Long) {
        val latitude = payload?.finiteOrNull("latitude")
        val longitude = payload?.finiteOrNull("longitude")
        val accuracy = payload?.finiteOrNull("accuracyMeters")
        if (payload == null || sourceNs == null || latitude == null || longitude == null || accuracy == null ||
            latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0 ||
            accuracy < 0.0 || sourceNs <= 0L
        ) {
            rejectedPvtCount++
            return
        }
        if (sourceNs - now > ms(pvtPolicy.maxFutureSkewMs)) { rejectedPvtCount++; return }
        val previous = lastValidPvtNs
        // Duplicate or out-of-order solutions can never replace newer evidence or move the clock back.
        if (previous != null && sourceNs <= previous) { outOfOrderPvtCount++; return }
        // A position that is already stale on arrival (old source timestamp) is not a current PVT.
        if (now - sourceNs > ms(pvtPolicy.staleAfterMs)) { staleOnArrivalPvtCount++; return }
        latestTrusted = TrustedPvt(
            latitudeDeg = latitude,
            longitudeDeg = longitude,
            altitudeM = payload.finiteOrNull("altitude"),
            speedMps = payload.finiteOrNull("speedMetersPerSecond")?.takeIf { it >= 0.0 },
            headingDeg = payload.finiteOrNull("bearingDegrees")?.takeIf { it >= 0.0 && it < 360.0 },
            timestampMonotonicNs = sourceNs,
            horizontalUncertaintyM = accuracy,
            verticalUncertaintyM = payload.finiteOrNull("verticalAccuracyMeters")?.takeIf { it >= 0.0 }
        )
        lastValidPvtNs = sourceNs
        if (previous != null && sourceNs - previous <= ms(pvtPolicy.staleAfterMs)) {
            consecutiveValidPvt++
        } else {
            consecutiveValidPvt = 1
            pvtStreakStartNs = sourceNs
        }
        hadGnssEvidence = true
    }

    private fun evaluateAgreement(now: Long): ConsistencyDecision {
        val evidence = ArrayList<PositionEvidence>(externalEvidence.values)
        val trusted = latestTrusted
        val pvtNs = lastValidPvtNs
        if (trusted != null && pvtNs != null) {
            evidence += PositionEvidence(
                sourceId = GNSS_SOURCE_ID,
                independenceGroup = GNSS_INDEPENDENCE_GROUP,
                latitudeDeg = trusted.latitudeDeg,
                longitudeDeg = trusted.longitudeDeg,
                horizontalUncertaintyM = trusted.horizontalUncertaintyM,
                timestampMonotonicNs = pvtNs
            )
        }
        return consistency.evaluate(evidence, now).also { lastConsistency = it }
    }

    private fun latest(current: Long?, candidate: Long?): Long? = when {
        candidate == null -> null
        current == null -> candidate
        else -> maxOf(current, candidate)
    }

    private fun ms(value: Long): Long = value * 1_000_000L

    private fun JSONObject.finiteOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val number = opt(key) as? Number ?: return null
        return number.toDouble().takeIf { it.isFinite() }
    }

    companion object {
        const val GNSS_SOURCE_ID = "GNSS_PVT"
        const val GNSS_INDEPENDENCE_GROUP = "GNSS"

        fun replay(observations: List<RawObservation>): List<ResilienceRuntimeUpdate> {
            val runtime = ResilienceRuntime()
            return observations.mapNotNull(runtime::accept)
        }
    }
}

private data class PreviousMeasurement(
    val timestampNs: Long,
    val pseudorangeRate: Double?,
    val adr: Double?
)

data class ResilienceRuntimeUpdate(
    val navigationState: NavigationState,
    val interferenceState: InterferenceState,
    val interferenceAssessment: InterferenceAssessment? = null,
    val trustDecisions: List<TrustDecisionRecord> = emptyList(),
    val transition: NavigationTransition?,
    val estimate: NavigationEstimate?,
    val provenance: String,
    /** Set once per distinct reason when a coasting estimate was refused while GNSS is not current. */
    val coastingRejection: CoastingRejection? = null
)
