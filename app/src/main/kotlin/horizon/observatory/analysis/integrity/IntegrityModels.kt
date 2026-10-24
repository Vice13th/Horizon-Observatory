package horizon.observatory.analysis.integrity

/** One raw GNSS measurement. A null field means "not exposed / flag unset" — never zero. */
data class MeasurementSample(
    val eventNs: Long,
    val constellationType: Int,
    val svid: Int,
    val carrierFrequencyHz: Double?,
    val cn0DbHz: Double?,
    /** Measurement-level AGC (deprecated since API 33); null when not exposed. */
    val agcDb: Double?,
    val rangeRateMps: Double?,
    val rangeRateUncMps: Double?,
    val adrM: Double?,
    val adrState: Int?,
    val adrUncM: Double?
)

/** Receiver clock fields captured once per measurements event (GNSS_CLOCK). */
data class ClockSample(
    val eventNs: Long,
    val timeNanos: Long?,
    val biasNs: Double?,
    val biasUncNs: Double?,
    val driftNsPerS: Double?,
    val driftUncNsPerS: Double?,
    val discontinuityCount: Int?,
    val fullTracking: Boolean?
)

/** Event-level AGC (newer Android API); one entry per reported AGC value. */
data class AgcEventSample(val eventNs: Long, val levelDb: Double, val constellationType: Int?, val carrierFrequencyHz: Double?)

/** Explicit event-level AGC parsing quality for one GNSS_CLOCK/measurements event. */
data class AgcParseDiagnostics(
    val eventsSeen: Int = 0,
    val unsupportedOrMissingArrayEvents: Int = 0,
    val emptyArrayEvents: Int = 0,
    val malformedEntryCount: Int = 0,
    val invalidLevelCount: Int = 0,
    val validControlCount: Int = 0
)

/** UNAVAILABLE = the data is not exposed (that is not evidence either way). */
enum class IndicatorStatus { UNAVAILABLE, INSUFFICIENT_HISTORY, WITHIN_BASELINE, DEVIATES }

/** Deliberately has no NOMINAL: these diagnostics cannot establish integrity, only fail to contradict it. */
enum class IntegrityAssessment { UNAVAILABLE, NOT_ESTABLISHED, SUSPECT }

data class IndicatorMetric(val name: String, val value: Double?, val unit: String, val sampleCount: Int, val windowSeconds: Double?)

data class IndicatorResult(
    val id: String,
    val title: String,
    val status: IndicatorStatus,
    val reasonCodes: List<String>,
    val metrics: List<IndicatorMetric>,
    val summary: String,
    val confounders: List<String>
)

/**
 * Detector parameters. Defaults are EXPERIMENTAL: self-baselined against the same session, uncalibrated for any
 * device or environment, no false-alarm rate established. They are parameters on purpose (never universal constants).
 */
data class IntegrityProfile(
    val id: String = "uncalibrated-default-1",
    val minSatellitesPerEpoch: Int = 4,
    val minSatellitesPerGroupEpoch: Int = 3,
    val minBaselineEpochs: Int = 30,
    val maxBaselineEpochs: Int = 120,
    val recentEpochs: Int = 10,
    val scanStride: Int = 5,
    val zGate: Double = 6.0,
    val cn0LevelFloorDb: Double = 0.5,
    val cn0DispersionFloorDb: Double = 0.25,
    val agcFloorDb: Double = 0.25,
    val maxGapSeconds: Double = 5.0,
    val minClockSegmentSamples: Int = 30,
    val driftAccelFloorNsPerS2: Double = 0.05,
    val driftOutlierCountGate: Int = 1,
    val adrMinIntervals: Int = 20,
    val adrSigmaFloorM: Double = 0.02,
    val adrOutlierZ: Double = 6.0,
    val adrOutlierIntervalsGate: Int = 2
)

data class IntegrityReport(
    val assessment: IntegrityAssessment,
    val reasonCodes: List<String>,
    val indicators: List<IndicatorResult>,
    val profileId: String,
    val epochCount: Int,
    val timeSpanSeconds: Double?,
    val notes: List<String>
)
