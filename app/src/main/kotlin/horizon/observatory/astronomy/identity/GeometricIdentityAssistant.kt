package horizon.observatory.astronomy.identity

import horizon.observatory.astronomy.ObserverLocation
import horizon.observatory.astronomy.OrbitDataSource
import horizon.observatory.astronomy.frames.PredictionFramePipeline
import horizon.observatory.astronomy.propagation.OrbitPropagationEngine
import horizon.observatory.astronomy.propagation.PropagationResult
import horizon.observatory.astronomy.time.PropagationTime
import horizon.observatory.astronomy.time.TimeBasis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class IdentityHintResult(
    val hints: List<IdentityHint>,
    /** Catalog objects predicted above the horizon and used as candidates. */
    val candidateCount: Int,
    val catalogSize: Int,
    val atUtcMs: Long,
    val maxSeparationDeg: Double,
    val minMarginDeg: Double,
    val unavailableReason: String?
)

/**
 * Predicts every stored catalog object for the observer at [atUtcMs] and runs [GeometricIdentityMatcher]
 * against the receiver's observed az/el. Hint generation only: it reads the catalog and engine, writes nothing,
 * and its output never reaches the prediction use case.
 */
object GeometricIdentityAssistant {
    suspend fun suggest(
        orbits: OrbitDataSource,
        engine: OrbitPropagationEngine,
        observed: List<ObservedDirection>,
        observer: ObserverLocation,
        atUtcMs: Long,
        maxSeparationDeg: Double = GeometricIdentityMatcher.DEFAULT_MAX_SEPARATION_DEG,
        minMarginDeg: Double = GeometricIdentityMatcher.DEFAULT_MIN_MARGIN_DEG
    ): IdentityHintResult = withContext(Dispatchers.Default) {
        fun unavailable(reason: String, catalog: Int = 0) =
            IdentityHintResult(emptyList(), 0, catalog, atUtcMs, maxSeparationDeg, minMarginDeg, reason)
        if (observed.isEmpty()) return@withContext unavailable("No observed satellites with finite azimuth/elevation.")
        val catalog = orbits.getAllOrbits()
        if (catalog.isEmpty()) return@withContext unavailable("Orbit catalog is empty (ingest an OMM catalog first).")
        val time = PropagationTime(atUtcMs, TimeBasis.DEVICE_CLOCK_UTC)
        val candidates = ArrayList<CandidateDirection>()
        var failures = 0
        for (o in catalog) {
            when (val r = engine.propagate(o.record, time)) {
                is PropagationResult.Failure -> failures++
                is PropagationResult.Success -> try {
                    val ecef = PredictionFramePipeline.temeToEcef(r.state)
                    val angles = PredictionFramePipeline.enuToAzElRange(PredictionFramePipeline.ecefToEnu(observer, ecef))
                    if (angles.elevationDeg > -5.0) {
                        candidates.add(CandidateDirection(o.record.noradCatId, angles.azimuthDeg, angles.elevationDeg, o.record.objectName))
                    }
                } catch (e: IllegalArgumentException) {
                    failures++
                }
            }
        }
        if (candidates.isEmpty()) {
            return@withContext unavailable("No catalog object could be predicted above the horizon ($failures failures of ${catalog.size}).", catalog.size)
        }
        IdentityHintResult(
            GeometricIdentityMatcher.match(observed, candidates, maxSeparationDeg, minMarginDeg),
            candidates.size, catalog.size, atUtcMs, maxSeparationDeg, minMarginDeg, null
        )
    }
}
