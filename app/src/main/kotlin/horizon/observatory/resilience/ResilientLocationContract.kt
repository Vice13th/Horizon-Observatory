package horizon.observatory.resilience

/** Freshness thresholds for a published snapshot. Provisional: not calibrated against recorded traces. */
data class SnapshotFreshnessPolicy(
    val maxObservedAgeMs: Long = 5_000L,
    val maxEstimateAgeMs: Long = 20_000L,
    val maxEstimateUncertaintyM: Double = 150.0,
    val maxFutureSkewMs: Long = 1_000L
) {
    init {
        require(maxObservedAgeMs >= 0L) { "maxObservedAgeMs must be non-negative" }
        require(maxEstimateAgeMs >= 0L) { "maxEstimateAgeMs must be non-negative" }
        require(maxEstimateUncertaintyM.isFinite() && maxEstimateUncertaintyM >= 0.0) { "maxEstimateUncertaintyM must be finite and non-negative" }
        require(maxFutureSkewMs >= 0L) { "maxFutureSkewMs must be non-negative" }
        require(maxObservedAgeMs <= Long.MAX_VALUE / 1_000_000L) { "maxObservedAgeMs overflows nanosecond conversion" }
        require(maxEstimateAgeMs <= Long.MAX_VALUE / 1_000_000L) { "maxEstimateAgeMs overflows nanosecond conversion" }
        require(maxFutureSkewMs <= Long.MAX_VALUE / 1_000_000L) { "maxFutureSkewMs overflows nanosecond conversion" }
    }
}

enum class SnapshotStatus { FRESH, STALE, NO_SNAPSHOT, CORRUPT }

data class FreshnessVerdict(val status: SnapshotStatus, val ageMs: Long?, val reason: String?)

/** Age is derived from the elapsed-realtime domain only; it is meaningful within a single boot. */
object SnapshotFreshness {
    fun evaluate(
        decoded: SnapshotDecodeResult,
        nowElapsedNs: Long,
        policy: SnapshotFreshnessPolicy = SnapshotFreshnessPolicy()
    ): FreshnessVerdict = when (decoded) {
        SnapshotDecodeResult.Absent -> FreshnessVerdict(SnapshotStatus.NO_SNAPSHOT, null, null)
        is SnapshotDecodeResult.Corrupt -> FreshnessVerdict(SnapshotStatus.CORRUPT, null, decoded.reason)
        is SnapshotDecodeResult.Valid -> evaluate(decoded.snapshot, nowElapsedNs, policy)
    }

    fun evaluate(
        snapshot: ResilientLocationSnapshot,
        nowElapsedNs: Long,
        policy: SnapshotFreshnessPolicy = SnapshotFreshnessPolicy()
    ): FreshnessVerdict {
        if (nowElapsedNs < 0L || snapshot.timestampMonotonicNs <= 0L) {
            return FreshnessVerdict(SnapshotStatus.STALE, null, "INVALID_MONOTONIC_CLOCK")
        }
        // Avoid signed subtraction overflow on malformed or cross-boot timestamps.
        val deltaNs = if (nowElapsedNs >= snapshot.timestampMonotonicNs) {
            nowElapsedNs - snapshot.timestampMonotonicNs
        } else {
            val aheadNs = snapshot.timestampMonotonicNs - nowElapsedNs
            if (aheadNs < 0L || aheadNs > policy.maxFutureSkewMs * 1_000_000L) {
                return FreshnessVerdict(SnapshotStatus.STALE, null, "CLOCK_DOMAIN_MISMATCH")
            }
            0L
        }
        val ageMs = deltaNs / 1_000_000L
        val isEstimate = snapshot.fixKind == ResilientFixKind.SHORT_HORIZON_ESTIMATE
        val maxAgeMs = if (isEstimate) policy.maxEstimateAgeMs else policy.maxObservedAgeMs
        if (ageMs > maxAgeMs) return FreshnessVerdict(SnapshotStatus.STALE, ageMs, "MAX_AGE_EXCEEDED")
        if (isEstimate && snapshot.horizontalUncertaintyM > policy.maxEstimateUncertaintyM) {
            return FreshnessVerdict(SnapshotStatus.STALE, ageMs, "UNCERTAINTY_CEILING_EXCEEDED")
        }
        return FreshnessVerdict(SnapshotStatus.FRESH, ageMs, null)
    }
}

/**
 * Horizon Resilient Location API, contract version 2 (explicit Horizon-specific API; NOT an Android
 * system location provider).
 *
 * One row per query. `status` is FRESH / STALE / NO_SNAPSHOT / CORRUPT. Position, uncertainty, altitude,
 * speed and bearing are returned ONLY when status is FRESH, so a client that reads latitude/longitude
 * without checking `status` can never receive a stale coordinate as current. Every optional value is a
 * SQL NULL when unknown. `timestampMonotonicNs` is SystemClock.elapsedRealtimeNanos() domain
 * (same-boot only); `ageMs` is computed by the provider at query time.
 */
object ResilientLocationContract {
    const val VERSION = 2

    /** Version-1 column names first and unchanged; version-2 additions after them. */
    val COLUMNS: Array<String> = arrayOf(
        "latitudeDeg", "longitudeDeg", "altitudeM", "horizontalUncertaintyM", "verticalUncertaintyM",
        "timestampMonotonicNs", "navigationState", "provenance",
        "contractVersion", "status", "statusReason", "fixKind", "ageMs", "utcTimestampMs",
        "speedMps", "bearingDeg", "estimator"
    )

    fun row(
        decoded: SnapshotDecodeResult,
        nowElapsedNs: Long,
        policy: SnapshotFreshnessPolicy = SnapshotFreshnessPolicy()
    ): Map<String, Any?> {
        val verdict = SnapshotFreshness.evaluate(decoded, nowElapsedNs, policy)
        val row = HashMap<String, Any?>()
        row["contractVersion"] = VERSION
        row["status"] = verdict.status.name
        row["statusReason"] = verdict.reason
        row["ageMs"] = verdict.ageMs
        val snapshot = (decoded as? SnapshotDecodeResult.Valid)?.snapshot ?: return row
        row["timestampMonotonicNs"] = snapshot.timestampMonotonicNs
        row["utcTimestampMs"] = snapshot.utcTimestampMs
        row["navigationState"] = snapshot.navigationState.name
        row["provenance"] = snapshot.provenance
        row["fixKind"] = snapshot.fixKind?.name
        row["estimator"] = snapshot.estimator
        if (verdict.status == SnapshotStatus.FRESH) {
            row["latitudeDeg"] = snapshot.latitudeDeg
            row["longitudeDeg"] = snapshot.longitudeDeg
            row["altitudeM"] = snapshot.altitudeM
            row["horizontalUncertaintyM"] = snapshot.horizontalUncertaintyM
            row["verticalUncertaintyM"] = snapshot.verticalUncertaintyM
            row["speedMps"] = snapshot.speedMps
            row["bearingDeg"] = snapshot.bearingDeg
        }
        return row
    }
}
