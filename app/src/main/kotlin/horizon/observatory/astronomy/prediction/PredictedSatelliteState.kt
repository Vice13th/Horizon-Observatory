package horizon.observatory.astronomy.prediction

import horizon.observatory.astronomy.CatalogStaleness
import horizon.observatory.astronomy.EcefState
import horizon.observatory.astronomy.OrbitFreshness
import horizon.observatory.astronomy.identity.GnssSatelliteId
import horizon.observatory.astronomy.identity.MappingProvenance
import horizon.observatory.astronomy.propagation.PropagationEngineDescriptor
import horizon.observatory.astronomy.propagation.PropagationModelFamily
import horizon.observatory.astronomy.propagation.TemeState
import horizon.observatory.astronomy.time.PropagationTime
import horizon.observatory.astronomy.time.TimeBasis

/** Single-valued on purpose: everything in this package is DERIVED from a public catalog, never RAW. */
enum class PredictionEvidenceClass { DERIVED_PREDICTED }

enum class PredictionStatus {
    PREDICTED,
    UNMATCHED,
    IDENTITY_AMBIGUOUS,
    IDENTITY_UNVERIFIED,
    NO_CATALOG_RECORD,
    OBSERVER_UNAVAILABLE,
    ENGINE_UNAVAILABLE,
    PROPAGATION_FAILED
}

/** Presentation-level state for the prediction layer (the observed layer is a different type). */
enum class PredictionDisplayState { PREDICTED, STALE, UNMATCHED, UNAVAILABLE }

/** Predicted geometry. Present only when status == PREDICTED. Never receiver-measured az/el. */
data class PredictedGeometry(
    val temeState: TemeState,
    val ecef: EcefState,
    val predictedAzimuthDeg: Double,
    val predictedElevationDeg: Double,
    val predictedRangeKm: Double,
    val minutesFromEpoch: Double,
    val modelUsed: PropagationModelFamily
)

data class PredictionProvenance(
    val catalogSource: String?,
    val catalogSourceIdentifier: String?,
    val catalogFormat: String?,
    val engine: PropagationEngineDescriptor?,
    val identityMapping: MappingProvenance?,
    val observerSource: ObserverSource?,
    val timeBasis: TimeBasis
) {
    val evidenceClass: PredictionEvidenceClass get() = PredictionEvidenceClass.DERIVED_PREDICTED
    val frameChain: String get() = FRAME_CHAIN

    companion object {
        const val FRAME_CHAIN: String = "SGP4/SDP4 TEME -> ECEF (GMST rotation only) -> observer ENU -> azimuth/elevation"
    }
}

/**
 * A prediction for one observed GNSS satellite. This is a separate type from SatelliteEvidence:
 * predicted az/el never enter the observed evidence model. A satellite that cannot be safely
 * identified or propagated yields a state with a non-PREDICTED status and NO geometry.
 */
data class PredictedSatelliteState(
    val satelliteId: GnssSatelliteId,
    val status: PredictionStatus,
    val noradCatId: Long?,
    val propagationTime: PropagationTime,
    val freshness: OrbitFreshness?,
    val staleness: CatalogStaleness?,
    val geometry: PredictedGeometry?,
    val provenance: PredictionProvenance,
    val detail: String?
) {
    init {
        require((status == PredictionStatus.PREDICTED) == (geometry != null)) {
            "geometry must be present if and only if status is PREDICTED"
        }
    }

    /** STALE covers every non-CURRENT staleness, including clock inconsistency (see [staleness]). */
    val displayState: PredictionDisplayState
        get() = when (status) {
            PredictionStatus.PREDICTED ->
                if (staleness == null || staleness == CatalogStaleness.CURRENT) PredictionDisplayState.PREDICTED
                else PredictionDisplayState.STALE
            PredictionStatus.UNMATCHED,
            PredictionStatus.IDENTITY_AMBIGUOUS,
            PredictionStatus.IDENTITY_UNVERIFIED -> PredictionDisplayState.UNMATCHED
            PredictionStatus.NO_CATALOG_RECORD,
            PredictionStatus.OBSERVER_UNAVAILABLE,
            PredictionStatus.ENGINE_UNAVAILABLE,
            PredictionStatus.PROPAGATION_FAILED -> PredictionDisplayState.UNAVAILABLE
        }
}
