package horizon.observatory.export

import horizon.observatory.analysis.AnalysisSnapshot
import horizon.observatory.astronomy.ObserverLocation
import horizon.observatory.astronomy.OrbitDataSource
import horizon.observatory.astronomy.identity.GeometricIdentityAssistant
import horizon.observatory.astronomy.identity.GeometricIdentityMatcher
import horizon.observatory.astronomy.identity.GnssSatelliteId
import horizon.observatory.astronomy.identity.IdentityHintResult
import horizon.observatory.astronomy.identity.ObservedDirection
import horizon.observatory.astronomy.propagation.OrbitPropagationEngine

/** Builds the HINT-ONLY identity suggestions for the report. Every missing prerequisite yields a reason, never a guess. */
suspend fun buildIdentityHints(
    orbits: OrbitDataSource,
    engine: OrbitPropagationEngine,
    analysis: AnalysisSnapshot,
    statusEpochUtcMs: Long?,
    latDeg: Double?,
    lonDeg: Double?,
    altM: Double?
): IdentityHintResult {
    fun unavailable(reason: String) = IdentityHintResult(
        emptyList(), 0, 0, statusEpochUtcMs ?: 0L,
        GeometricIdentityMatcher.DEFAULT_MAX_SEPARATION_DEG, GeometricIdentityMatcher.DEFAULT_MIN_MARGIN_DEG, reason
    )
    if (statusEpochUtcMs == null) return unavailable("No GNSS status epoch in the session.")
    if (latDeg == null || lonDeg == null || !latDeg.isFinite() || !lonDeg.isFinite()) {
        return unavailable("No receiver position (GNSS fix) available.")
    }
    val observed = analysis.gnssDomain.latestSatelliteEvidence.mapNotNull { e ->
        val az = e.azimuthDegrees
        val el = e.elevationDegrees
        if (az != null && el != null && az.isFinite() && el.isFinite()) {
            ObservedDirection(GnssSatelliteId(e.constellationType, e.svid), az, el)
        } else null
    }
    return try {
        GeometricIdentityAssistant.suggest(
            orbits, engine, observed,
            ObserverLocation(latDeg, lonDeg, (altM?.takeIf { it.isFinite() } ?: 0.0) / 1000.0),
            statusEpochUtcMs
        )
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        unavailable("Hint generation failed: ${e.javaClass.simpleName}: ${e.message}")
    }
}
