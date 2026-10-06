package horizon.observatory.astronomy.prediction

import horizon.observatory.astronomy.CatalogStaleness
import horizon.observatory.astronomy.OrbitDataSource
import horizon.observatory.astronomy.StalenessPolicy
import horizon.observatory.astronomy.classify
import horizon.observatory.astronomy.frames.PredictionFramePipeline
import horizon.observatory.astronomy.identity.GnssSatelliteId
import horizon.observatory.astronomy.identity.SatelliteIdentityMapping
import horizon.observatory.astronomy.identity.SatelliteIdentityResolver
import horizon.observatory.astronomy.propagation.OrbitPropagationEngine
import horizon.observatory.astronomy.propagation.PropagationFailureReason
import horizon.observatory.astronomy.propagation.PropagationResult
import horizon.observatory.astronomy.time.DeviceClockUtc
import horizon.observatory.astronomy.time.PropagationTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Request to predict geometry for satellites the RECEIVER has observed. [observedSatellites] comes
 * from the observed layer (constellation + SVID only). [observer] may be null when no position is
 * available, in which case no az/el is produced.
 */
data class PredictionRequest(
    val observedSatellites: List<GnssSatelliteId>,
    val observer: ObserverPosition?,
    val at: PropagationTime
)

/** Local counters for diagnostics. No telemetry, nothing leaves the device. */
data class PredictionDiagnostics(
    val requested: Int,
    val predicted: Int,
    val stale: Int,
    val unmatched: Int,
    val ambiguous: Int,
    val identityUnverified: Int,
    val noCatalogRecord: Int,
    val observerUnavailable: Int,
    val engineUnavailable: Int,
    val propagationFailures: Int
)

data class PredictionBatch(
    val states: List<PredictedSatelliteState>,
    val diagnostics: PredictionDiagnostics,
    /** Device-clock time at which staleness/data age were evaluated. */
    val evaluatedAtDeviceClockUtcMs: Long
)

/**
 * observed satellite id -> identity mapping -> catalog record -> propagation -> frame chain ->
 * predicted az/el. Every stage may stop the chain; a stopped chain yields a state with a
 * non-PREDICTED status and no geometry. Nothing is guessed, defaulted or substituted.
 *
 * Runs on [computeDispatcher] (default: Dispatchers.Default) so propagation never runs inside
 * Compose recomposition or on the main thread.
 */
class SatellitePredictionUseCase(
    private val orbits: OrbitDataSource,
    private val identity: SatelliteIdentityResolver,
    private val engine: OrbitPropagationEngine,
    private val clock: DeviceClockUtc,
    private val stalenessPolicy: StalenessPolicy,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend fun predict(request: PredictionRequest): PredictionBatch =
        withContext(computeDispatcher) {
            val evaluatedAt = clock.nowUtcMillis()
            val states = request.observedSatellites.distinct().map { predictOne(it, request, evaluatedAt) }
            PredictionBatch(states, diagnosticsOf(request.observedSatellites.distinct().size, states), evaluatedAt)
        }

    private suspend fun predictOne(id: GnssSatelliteId, request: PredictionRequest, evaluatedAt: Long): PredictedSatelliteState {
        val at = request.at
        val baseProvenance = PredictionProvenance(
            catalogSource = null,
            catalogSourceIdentifier = null,
            catalogFormat = null,
            engine = null,
            identityMapping = null,
            observerSource = request.observer?.source,
            timeBasis = at.basis
        )

        val mapping = identity.resolve(id, at)
        val matched: SatelliteIdentityMapping.Matched = when (mapping) {
            is SatelliteIdentityMapping.Matched -> mapping
            is SatelliteIdentityMapping.Unmatched ->
                return stopped(id, PredictionStatus.UNMATCHED, null, at, baseProvenance, mapping.reason)
            is SatelliteIdentityMapping.Ambiguous ->
                return stopped(
                    id, PredictionStatus.IDENTITY_AMBIGUOUS, null, at, baseProvenance,
                    "candidates: ${mapping.candidateNoradCatIds}"
                )
            is SatelliteIdentityMapping.Unverified ->
                return stopped(
                    id, PredictionStatus.IDENTITY_UNVERIFIED, null, at,
                    baseProvenance.copy(identityMapping = mapping.provenance),
                    "unverified candidate NORAD ${mapping.candidateNoradCatId} was not used"
                )
        }

        val identityProvenance = baseProvenance.copy(identityMapping = matched.provenance)
        val orbit = orbits.getOrbit(matched.noradCatId)
            ?: return stopped(
                id, PredictionStatus.NO_CATALOG_RECORD, matched.noradCatId, at, identityProvenance,
                "no catalog record stored for NORAD ${matched.noradCatId}"
            )

        val freshness = orbit.freshness(evaluatedAt)
        val staleness: CatalogStaleness = freshness.classify(stalenessPolicy)
        val provenance = identityProvenance.copy(
            catalogSource = orbit.provenance.source,
            catalogSourceIdentifier = orbit.provenance.sourceIdentifier,
            catalogFormat = orbit.provenance.format
        )

        val observer = request.observer
            ?: return stopped(
                id, PredictionStatus.OBSERVER_UNAVAILABLE, matched.noradCatId, at, provenance,
                "no observer position available", freshness, staleness
            )

        return when (val propagated = engine.propagate(orbit.record, at)) {
            is PropagationResult.Failure -> {
                val status = if (propagated.reason == PropagationFailureReason.ENGINE_UNAVAILABLE) {
                    PredictionStatus.ENGINE_UNAVAILABLE
                } else {
                    PredictionStatus.PROPAGATION_FAILED
                }
                stopped(
                    id, status, matched.noradCatId, at, provenance.copy(engine = propagated.engine),
                    "${propagated.reason}: ${propagated.detail}", freshness, staleness
                )
            }
            is PropagationResult.Success -> {
                val withEngine = provenance.copy(engine = propagated.engine)
                try {
                    val ecef = PredictionFramePipeline.temeToEcef(propagated.state)
                    val enu = PredictionFramePipeline.ecefToEnu(observer.location, ecef)
                    val angles = PredictionFramePipeline.enuToAzElRange(enu)
                    PredictedSatelliteState(
                        satelliteId = id,
                        status = PredictionStatus.PREDICTED,
                        noradCatId = matched.noradCatId,
                        propagationTime = at,
                        freshness = freshness,
                        staleness = staleness,
                        geometry = PredictedGeometry(
                            temeState = propagated.state,
                            ecef = ecef,
                            predictedAzimuthDeg = angles.azimuthDeg,
                            predictedElevationDeg = angles.elevationDeg,
                            predictedRangeKm = angles.rangeKm,
                            minutesFromEpoch = propagated.minutesFromEpoch,
                            modelUsed = propagated.modelUsed
                        ),
                        provenance = withEngine,
                        detail = null
                    )
                } catch (e: IllegalArgumentException) {
                    stopped(
                        id, PredictionStatus.PROPAGATION_FAILED, matched.noradCatId, at, withEngine,
                        "frame transformation failed: ${e.message}", freshness, staleness
                    )
                }
            }
        }
    }

    private fun stopped(
        id: GnssSatelliteId,
        status: PredictionStatus,
        noradCatId: Long?,
        at: PropagationTime,
        provenance: PredictionProvenance,
        detail: String,
        freshness: horizon.observatory.astronomy.OrbitFreshness? = null,
        staleness: CatalogStaleness? = null
    ): PredictedSatelliteState =
        PredictedSatelliteState(
            satelliteId = id,
            status = status,
            noradCatId = noradCatId,
            propagationTime = at,
            freshness = freshness,
            staleness = staleness,
            geometry = null,
            provenance = provenance,
            detail = detail
        )

    private fun diagnosticsOf(requested: Int, states: List<PredictedSatelliteState>): PredictionDiagnostics =
        PredictionDiagnostics(
            requested = requested,
            predicted = states.count { it.status == PredictionStatus.PREDICTED },
            stale = states.count { it.displayState == PredictionDisplayState.STALE },
            unmatched = states.count { it.status == PredictionStatus.UNMATCHED },
            ambiguous = states.count { it.status == PredictionStatus.IDENTITY_AMBIGUOUS },
            identityUnverified = states.count { it.status == PredictionStatus.IDENTITY_UNVERIFIED },
            noCatalogRecord = states.count { it.status == PredictionStatus.NO_CATALOG_RECORD },
            observerUnavailable = states.count { it.status == PredictionStatus.OBSERVER_UNAVAILABLE },
            engineUnavailable = states.count { it.status == PredictionStatus.ENGINE_UNAVAILABLE },
            propagationFailures = states.count { it.status == PredictionStatus.PROPAGATION_FAILED }
        )
}
