package horizon.observatory.astronomy

import horizon.observatory.astronomy.time.CatalogEpochUtcMs
import horizon.observatory.astronomy.time.RetrievalTimeUtcMs
import horizon.observatory.astronomy.time.TimeBasis
import java.time.Instant

/**
 * Authoritative orbital record as published by the catalog (CelesTrak-style OMM/JSON). Field values
 * are kept in the SOURCE units so nothing needed by an SGP4/SDP4 implementation is discarded:
 *
 *  - angles: degrees; mean motion: revolutions per day (Kozai mean motion as published in
 *    TLE-derived OMM); BSTAR: 1/Earth-radii; MEAN_MOTION_DOT: rev/day^2; MEAN_MOTION_DDOT: rev/day^3.
 *  - [epochRaw] is the EPOCH text exactly as received (microsecond precision preserved);
 *    [epochUtc] is that text parsed as UTC.
 *
 * A record is NOT a propagation result and NOT receiver evidence. The semi-major axis available via
 * [derivedSemiMajorAxisKmTwoBody] is a DERIVED convenience for display only; it is never an SGP4
 * input and must not replace the source elements.
 */
data class OmmRecord(
    val noradCatId: Long,
    val objectName: String?,
    val objectId: String?,
    val classification: String?,
    val epochRaw: String,
    val epochUtc: Instant,
    val meanMotionRevPerDay: Double,
    val eccentricity: Double,
    val inclinationDeg: Double,
    val raanDeg: Double,
    val argOfPericenterDeg: Double,
    val meanAnomalyDeg: Double,
    val ephemerisType: Int?,
    val elementSetNo: Int?,
    val revAtEpoch: Long?,
    val bstar: Double,
    val meanMotionDot: Double?,
    val meanMotionDdot: Double?
) {
    /** DERIVED two-body semi-major axis from mean motion (km). Display convenience only. */
    val derivedSemiMajorAxisKmTwoBody: Double
        get() {
            val nRadS = meanMotionRevPerDay * 2.0 * Math.PI / 86400.0
            return Math.cbrt(GM_EARTH_KM3_S2 / (nRadS * nRadS))
        }

    val displayName: String get() = objectName ?: "NORAD $noradCatId"

    companion object {
        const val GM_EARTH_KM3_S2: Double = 398600.4418
    }
}

/** Where and when a catalog payload came from. Never rewritten by reading from storage. */
data class OrbitProvenance(
    val source: String,
    val sourceIdentifier: String?,
    val format: String,
    val retrievedAt: RetrievalTimeUtcMs
)

/**
 * A stored record together with its provenance. This is the type the orbit data source returns:
 * catalog epoch, retrieval time, source and identity all survive the Entity -> Domain boundary.
 * Data age is time-dependent, so it is computed on demand against an explicit device-clock reading.
 */
data class CatalogedOrbit(
    val record: OmmRecord,
    val provenance: OrbitProvenance
) {
    val catalogEpoch: CatalogEpochUtcMs get() = CatalogEpochUtcMs(record.epochUtc.toEpochMilli())

    /** Milliseconds since this device retrieved the payload. Negative means clock inconsistency. */
    fun dataAgeMs(evaluatedAtUtcMs: Long): Long = evaluatedAtUtcMs - provenance.retrievedAt.value

    /** Milliseconds since the element set epoch. Negative means clock inconsistency. */
    fun epochAgeMs(evaluatedAtUtcMs: Long): Long = evaluatedAtUtcMs - catalogEpoch.value

    fun freshness(evaluatedAtUtcMs: Long): OrbitFreshness =
        OrbitFreshness(
            catalogEpoch = catalogEpoch,
            retrievedAt = provenance.retrievedAt,
            evaluatedAtUtcMs = evaluatedAtUtcMs,
            evaluationBasis = TimeBasis.DEVICE_CLOCK_UTC,
            dataAgeMs = dataAgeMs(evaluatedAtUtcMs),
            epochAgeMs = epochAgeMs(evaluatedAtUtcMs)
        )
}

/** Freshness snapshot. Ages are raw (never clamped) so clock problems stay visible. */
data class OrbitFreshness(
    val catalogEpoch: CatalogEpochUtcMs,
    val retrievedAt: RetrievalTimeUtcMs,
    val evaluatedAtUtcMs: Long,
    val evaluationBasis: TimeBasis,
    val dataAgeMs: Long,
    val epochAgeMs: Long
)

/**
 * Caller-defined staleness thresholds. These are POLICY values, not physical accuracy limits: a
 * catalog that is "CURRENT" under a policy is not thereby "accurate".
 */
data class StalenessPolicy(val maxDataAgeMs: Long, val maxEpochAgeMs: Long) {
    init {
        require(maxDataAgeMs > 0L) { "maxDataAgeMs must be positive" }
        require(maxEpochAgeMs > 0L) { "maxEpochAgeMs must be positive" }
    }
}

enum class CatalogStaleness {
    CURRENT,
    STALE_RETRIEVAL,
    STALE_EPOCH,
    STALE_RETRIEVAL_AND_EPOCH,

    /** Device clock disagrees with the catalog (negative age). Age cannot be trusted. */
    CLOCK_INCONSISTENT
}

fun OrbitFreshness.classify(policy: StalenessPolicy): CatalogStaleness {
    if (dataAgeMs < 0L || epochAgeMs < 0L) return CatalogStaleness.CLOCK_INCONSISTENT
    val retrievalStale = dataAgeMs > policy.maxDataAgeMs
    val epochStale = epochAgeMs > policy.maxEpochAgeMs
    return when {
        retrievalStale && epochStale -> CatalogStaleness.STALE_RETRIEVAL_AND_EPOCH
        retrievalStale -> CatalogStaleness.STALE_RETRIEVAL
        epochStale -> CatalogStaleness.STALE_EPOCH
        else -> CatalogStaleness.CURRENT
    }
}
