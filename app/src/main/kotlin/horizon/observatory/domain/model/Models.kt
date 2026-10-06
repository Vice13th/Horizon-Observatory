package horizon.observatory.domain.model

enum class ObservationType {
    GNSS_FIX,
    GNSS_RAW_MEASUREMENT,
    GNSS_STATUS,
    GNSS_NAVIGATION_MESSAGE,
    GNSS_ANTENNA_INFO,
    CELLULAR_INFO,
    SENSOR_ACCEL,
    SENSOR_GYRO,
    SENSOR_MAGNETIC,
    SENSOR_LIGHT,
    SENSOR_PROXIMITY,
    SENSOR_GRAVITY,
    SENSOR_LINEAR_ACCEL,
    SENSOR_ROTATION,
    SENSOR_PRESSURE,
    SENSOR_STEP,
    SENSOR_GENERIC,
    SYSTEM_EVENT,
    ERROR_EVENT,
    /** GNSS assistance/context evidence -- NOT a GNSS observation. See domain.assistance
     *  package. Deliberately a peer of GNSS_*, not nested under it, so it can never be
     *  mistaken for or merged into raw GNSS evidence by any code that filters on "GNSS_". */
    ASSISTANCE_EVENT
}

enum class SourceStatus {
    SUPPORTED,
    AVAILABLE,
    UNAVAILABLE,
    UNSUPPORTED,
    PERMISSION_DENIED,
    TEMPORARILY_UNAVAILABLE,
    API_ERROR,
    HARDWARE_ERROR,
    UNKNOWN
}

enum class CapabilityState {
    SUPPORTED,
    AVAILABLE_NOW,
    NOT_SUPPORTED,
    NOT_OBSERVED,
    UNAVAILABLE,
    PERMISSION_DENIED,
    UNVERIFIED,
    UNKNOWN
}

enum class EvidenceStatus {
    VERIFIED,
    OBSERVED,
    MEASURED,
    SUPPORTED,
    USER_ASSERTED,
    MODEL_GENERATED,
    UNKNOWN,
    MISSING,
    CONFLICT,
    STALE,
    UNVERIFIED,
    NOT_SUPPORTED,
    NOT_OBSERVED,
    UNAVAILABLE,
    PERMISSION_DENIED,
    FAILED,
    DERIVED
}

enum class ObservationProvenance {
    RAW_ACQUISITION,
    NORMALIZED,
    DERIVED,
    MODEL
}

enum class TimestampDomain {
    ANDROID_ELAPSED_REALTIME_NANOS,
    SOURCE_TIMESTAMP,
    WALL_CLOCK_UTC_MILLIS,
    UNKNOWN
}

data class RawObservation(
    val type: ObservationType,
    val utcTimestampMs: Long,
    val provider: String,
    val payloadJson: String,
    /** Source/measurement monotonic timestamp when the platform provides one. */
    val monotonicTimestampNs: Long? = null,
    /** Monotonic timestamp assigned by HORIZON at serialized ingress, used for integrity ordering. */
    val ingestionMonotonicTimestampNs: Long? = null,
    val sequenceNumber: Long = 0L,
    val technology: String = "UNKNOWN",
    val capabilityState: CapabilityState = CapabilityState.UNKNOWN,
    val evidenceStatus: EvidenceStatus = EvidenceStatus.OBSERVED,
    val provenance: ObservationProvenance = ObservationProvenance.RAW_ACQUISITION,
    val timestampDomain: TimestampDomain = TimestampDomain.UNKNOWN,
    val timestampUncertaintyNs: Long? = null
)

fun SourceStatus.toCapabilityState(): CapabilityState = when (this) {
    SourceStatus.SUPPORTED -> CapabilityState.SUPPORTED
    SourceStatus.AVAILABLE -> CapabilityState.AVAILABLE_NOW
    SourceStatus.UNAVAILABLE -> CapabilityState.UNAVAILABLE
    SourceStatus.UNSUPPORTED -> CapabilityState.NOT_SUPPORTED
    SourceStatus.PERMISSION_DENIED -> CapabilityState.PERMISSION_DENIED
    SourceStatus.TEMPORARILY_UNAVAILABLE,
    SourceStatus.API_ERROR,
    SourceStatus.HARDWARE_ERROR,
    SourceStatus.UNKNOWN -> CapabilityState.UNKNOWN
}
