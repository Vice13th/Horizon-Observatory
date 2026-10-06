package horizon.observatory.astronomy.propagation

import horizon.observatory.astronomy.OmmRecord
import horizon.observatory.astronomy.time.PropagationTime

/**
 * VERIFIED_AGAINST_REFERENCE_VECTORS may only be set once published reference vectors (near-earth
 * and deep-space) have actually been run against the concrete backend. Nothing in this checkpoint
 * sets it.
 */
enum class EngineVerificationStatus { UNVERIFIED, VERIFIED_AGAINST_REFERENCE_VECTORS }

data class PropagationEngineDescriptor(
    val engineName: String,
    val backendName: String?,
    val backendVersion: String?,
    val verification: EngineVerificationStatus,
    val backendSupportsDeepSpace: Boolean?
)

/**
 * Satellite state in the TEME (True Equator, Mean Equinox) frame: the frame SGP4/SDP4 output is
 * defined in. km and km/s. Distinct from EciState (frame unspecified) on purpose.
 */
data class TemeState(
    val xKm: Double,
    val yKm: Double,
    val zKm: Double,
    val vxKmS: Double,
    val vyKmS: Double,
    val vzKmS: Double,
    val atTime: PropagationTime
)

enum class PropagationFailureReason {
    INVALID_INPUT,
    ENGINE_UNAVAILABLE,
    PROPAGATION_ERROR,
    NON_FINITE_OUTPUT
}

sealed interface PropagationResult {
    val engine: PropagationEngineDescriptor

    data class Success(
        val state: TemeState,
        val modelUsed: PropagationModelFamily,
        /** Signed distance from the element-set epoch. Prediction quality degrades with |value|. */
        val minutesFromEpoch: Double,
        override val engine: PropagationEngineDescriptor
    ) : PropagationResult

    data class Failure(
        val reason: PropagationFailureReason,
        val detail: String,
        override val engine: PropagationEngineDescriptor
    ) : PropagationResult
}

/**
 * Domain boundary for orbit propagation. The output is DERIVED/PREDICTED and is never receiver
 * evidence. An implementation that cannot produce a real SGP4/SDP4 state must return a Failure; it
 * must not substitute a different model and still call the result a prediction.
 *
 * The two-body OrbitPropagator in this package's parent is NOT an implementation of this interface
 * and must not be wired behind it.
 */
interface OrbitPropagationEngine {
    val descriptor: PropagationEngineDescriptor
    fun propagate(orbit: OmmRecord, at: PropagationTime): PropagationResult
}
