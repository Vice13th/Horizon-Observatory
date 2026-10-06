package horizon.observatory.resilience

enum class CapabilityAvailability { AVAILABLE, UNAVAILABLE, UNKNOWN }

enum class CapabilityKey {
    RAW_GNSS, CN0, AGC, PSEUDORANGE, RECEIVED_SV_TIME, DOPPLER,
    ADR, CARRIER_PHASE, NAVIGATION_MESSAGES, MULTI_CONSTELLATION,
    MULTI_FREQUENCY, IMU, ORIENTATION, CELLULAR, LAST_TRUSTED_PVT,
    MEASUREMENT_TIMESTAMPS, MEASUREMENT_AGE
}

data class CapabilityEvidence(
    val key: CapabilityKey,
    val availability: CapabilityAvailability,
    val provenance: String,
    val observedAtMonotonicNs: Long?,
    val detail: String? = null
)

data class CapabilityContract(private val values: Map<CapabilityKey, CapabilityEvidence>) {
    operator fun get(key: CapabilityKey): CapabilityEvidence = values[key]
        ?: CapabilityEvidence(key, CapabilityAvailability.UNKNOWN, "UNOBSERVED", null)

    fun asMap(): Map<CapabilityKey, CapabilityEvidence> = values.toMap()

    companion object {
        fun from(values: Iterable<CapabilityEvidence>): CapabilityContract =
            CapabilityContract(values.associateBy { it.key })
    }
}

enum class InterferenceState { NORMAL, DEGRADED, JAM_LIKELY, SPOOF_LIKELY, UNKNOWN, RECOVERY }

data class InterferenceSnapshot(
    val cn0MedianDbHz: Double? = null,
    val baselineCn0MedianDbHz: Double? = null,
    val trackedSatelliteCount: Int? = null,
    val usedInFixSatelliteCount: Int? = null,
    val baselineTrackedSatelliteCount: Int? = null,
    val constellationCount: Int? = null,
    val baselineConstellationCount: Int? = null,
    val bandCount: Int? = null,
    val baselineBandCount: Int? = null,
    val dopplerResidualRms: Double? = null,
    val adrResidualRms: Double? = null,
    val pvtDegraded: Boolean? = null,
    val measurementContinuityLost: Boolean? = null,
    val crossSourceDisagreement: Boolean? = null,
    val ageMs: Long? = null
)

data class EvidenceSignal(val name: String, val value: String, val weight: Double)

data class InterferenceAssessment(
    val state: InterferenceState,
    val score: Double,
    val evidence: List<EvidenceSignal>,
    val provenance: String = "RESILIENCE_INTERFERENCE_DERIVED_HYPOTHESIS"
)

data class MeasurementCandidate(
    val id: String,
    val signalStability: Double? = null,
    val temporalContinuity: Double? = null,
    val freshness: Double? = null,
    val ageMs: Long? = null,
    val dopplerConsistency: Double? = null,
    val adrConsistency: Double? = null,
    val geometry: Double? = null,
    val residualConsistency: Double? = null,
    val sourceAgreement: Double? = null
)

enum class TrustDecision { KEEP, DOWN_WEIGHT, REJECT }

data class TrustDecisionRecord(
    val id: String,
    val score: Double,
    val decision: TrustDecision,
    val reasons: List<String>,
    val rank: Int
)

data class NavigationEvidence(
    val healthyGnss: Boolean = false,
    val degradedGnss: Boolean = false,
    val partialGnss: Boolean = false,
    val gnssLost: Boolean = false,
    val validImu: Boolean = false,
    val validCell: Boolean = false,
    val multiSourceAgreement: Boolean = false,
    val recoveryGnss: Boolean = false
)

enum class NavigationState {
    FULL_GNSS, GNSS_DEGRADED, PARTIAL_GNSS, GNSS_LOST,
    INERTIAL_BRIDGING, CELL_AIDED, MULTI_SOURCE_FUSION, RECOVERY
}

data class NavigationTransition(
    val from: NavigationState,
    val to: NavigationState,
    val evidence: NavigationEvidence,
    val provenance: String = "RESILIENCE_STATE_MACHINE_DERIVED"
)

data class TrustedPvt(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val altitudeM: Double?,
    val speedMps: Double?,
    val headingDeg: Double?,
    val timestampMonotonicNs: Long,
    val horizontalUncertaintyM: Double,
    val verticalUncertaintyM: Double?
)

data class BridgeInput(
    val lastTrusted: TrustedPvt,
    val elapsedSeconds: Double,
    val accelerationMps2: Double? = null,
    val velocityMps: Double? = lastTrusted.speedMps,
    val headingDeg: Double? = lastTrusted.headingDeg,
    val imuAvailable: Boolean = false,
    val cellAvailable: Boolean = false
)

data class NavigationEstimate(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val altitudeM: Double?,
    val timestampMonotonicNs: Long,
    val horizontalUncertaintyM: Double,
    val verticalUncertaintyM: Double?,
    val provenance: String,
    val navigationState: NavigationState
)
