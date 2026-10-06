package horizon.observatory.resilience

import horizon.observatory.domain.model.ObservationType
import horizon.observatory.domain.model.RawObservation
import org.json.JSONObject

class ResilienceRuntime {
    private val interferenceEngine = InterferenceEvidenceEngine()
    private val receptionOptimization = ReceptionOptimizationEngine(ReceptionOptimizationPolicy(enabled = true))
    private val stateMachine = NavigationContinuityStateMachine(transitionDebounceSamples = 2)
    private val deadReckoning = DeadReckoningBridge()
    private val previousMeasurements = mutableMapOf<String, PreviousMeasurement>()
    private var baselineCn0: Double? = null
    private var baselineSatellites: Int? = null
    private var lastGnssEvidenceNs: Long? = null
    private var hadGnssEvidence = false
    private var latestTrusted: TrustedPvt? = null
    private var lastInterferenceState: InterferenceState? = null
    private var lastInterferenceAssessment: InterferenceAssessment? = null
    private var imuSeenNs: Long? = null
    private var cellSeenNs: Long? = null
    private var lastComputedGnssAgeMs: Long? = null
    private var lastTrustDecisions: List<TrustDecisionRecord> = emptyList()
    private var interferenceStateChanged = false

    val navigationState: NavigationState
        get() = stateMachine.state

    val debugLastGnssEvidenceNs: Long?
        get() = lastGnssEvidenceNs

    val debugLastComputedGnssAgeMs: Long?
        get() = lastComputedGnssAgeMs

    fun accept(observation: RawObservation): ResilienceRuntimeUpdate? {
        interferenceStateChanged = false
        val payload = runCatching { JSONObject(observation.payloadJson) }.getOrNull()
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
                lastGnssEvidenceNs = observation.monotonicTimestampNs
                hadGnssEvidence = true
                val previousState = lastInterferenceState
                val assessment = interferenceEngine.assess(
                    InterferenceSnapshot(
                        cn0MedianDbHz = cn0,
                        baselineCn0MedianDbHz = baselineCn0,
                        trackedSatelliteCount = tracked,
                        usedInFixSatelliteCount = used,
                        baselineTrackedSatelliteCount = baselineSatellites,
                        pvtDegraded = latestTrusted?.horizontalUncertaintyM?.let { it > 50.0 }
                    )
                )
                lastInterferenceAssessment = assessment
                lastInterferenceState = assessment.state
                interferenceStateChanged = previousState != assessment.state
            }
            ObservationType.GNSS_RAW_MEASUREMENT -> {
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
            ObservationType.GNSS_FIX -> {
                val lat = payload?.optDouble("latitude", Double.NaN)?.takeUnless(Double::isNaN)
                val lon = payload?.optDouble("longitude", Double.NaN)?.takeUnless(Double::isNaN)
                if (lat != null && lon != null) {
                    val accuracy = payload.optDouble("accuracyMeters", Double.NaN).takeUnless(Double::isNaN) ?: 9999.0
                    val altitude = payload.optDouble("altitude", 0.0)
                    val speed = payload.optDouble("speedMetersPerSecond", Double.NaN).takeUnless(Double::isNaN)
                    val heading = payload.optDouble("bearingDegrees", Double.NaN).takeUnless(Double::isNaN)
                    latestTrusted = TrustedPvt(lat, lon, altitude, speed, heading, observation.monotonicTimestampNs ?: 0L, accuracy, accuracy)
                    lastGnssEvidenceNs = observation.monotonicTimestampNs
                    hadGnssEvidence = true
                }
            }
            ObservationType.SENSOR_ACCEL, ObservationType.SENSOR_GYRO,
            ObservationType.SENSOR_ROTATION, ObservationType.SENSOR_GRAVITY -> imuSeenNs = observation.monotonicTimestampNs
            ObservationType.CELLULAR_INFO -> cellSeenNs = observation.monotonicTimestampNs
            else -> Unit
        }

        val now = observation.monotonicTimestampNs ?: return null
        val gnssAgeMs = lastGnssEvidenceNs?.let { ((now - it).coerceAtLeast(0L)) / 1_000_000L }
        lastComputedGnssAgeMs = gnssAgeMs
        val imuFresh = imuSeenNs?.let { now - it <= 2_000_000_000L } == true
        val cellFresh = cellSeenNs?.let { now - it <= 5_000_000_000L } == true
        val lost = hadGnssEvidence && (gnssAgeMs ?: 0L) > 5_000L
        val healthy = !lost && latestTrusted != null && latestTrusted!!.horizontalUncertaintyM <= 50.0
        val partial = !lost && hadGnssEvidence && latestTrusted == null
        val degraded = !lost && hadGnssEvidence && !healthy && !partial
        val transition = stateMachine.update(
            NavigationEvidence(
                healthyGnss = healthy,
                degradedGnss = degraded,
                partialGnss = partial,
                gnssLost = lost,
                validImu = imuFresh,
                validCell = cellFresh,
                multiSourceAgreement = imuFresh && cellFresh && !lost,
                recoveryGnss = stateMachine.state != NavigationState.FULL_GNSS && !lost && healthy
            )
        )
        val interferenceChanged = interferenceStateChanged
        val meaningfulTrust = lastTrustDecisions.any { it.decision != TrustDecision.KEEP }
        val meaningfulInterference = observation.type == ObservationType.GNSS_STATUS && interferenceChanged
        if (transition == null && !meaningfulTrust && !meaningfulInterference) return null
        val update = ResilienceRuntimeUpdate(
            navigationState = stateMachine.state,
            interferenceState = lastInterferenceState ?: InterferenceState.UNKNOWN,
            interferenceAssessment = lastInterferenceAssessment,
            trustDecisions = lastTrustDecisions,
            transition = transition,
            estimate = if (transition?.to == NavigationState.INERTIAL_BRIDGING && latestTrusted != null) {
                val elapsedSeconds = ((now - latestTrusted!!.timestampMonotonicNs).coerceAtLeast(0L)) / 1_000_000_000.0
                deadReckoning.estimate(BridgeInput(latestTrusted!!, elapsedSeconds, imuAvailable = imuFresh, cellAvailable = cellFresh))
            } else null,
            provenance = "RESILIENCE_RUNTIME_DERIVED"
        )
        lastTrustDecisions = emptyList()
        return update
    }

    companion object {
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
    val provenance: String
)
