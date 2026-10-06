package horizon.observatory.astronomy.propagation

import horizon.observatory.astronomy.OmmRecord
import horizon.observatory.astronomy.time.PropagationTime
import java.time.Duration
import java.time.Instant

/**
 * [OrbitPropagationEngine] over an [Sgp4Sdp4Backend]. Responsibilities kept here: input validation,
 * OMM -> backend unit conversion, minutes-since-epoch computation, error mapping, finite-output
 * check. Responsibilities deliberately NOT here: the SGP4/SDP4 mathematics and the near-earth /
 * deep-space selection (backend).
 *
 * [verification] defaults to UNVERIFIED and can only be raised by wiring code that has evidence.
 */
class Sgp4Sdp4PropagationEngine(
    private val backend: Sgp4Sdp4Backend,
    verification: EngineVerificationStatus = EngineVerificationStatus.UNVERIFIED
) : OrbitPropagationEngine {

    override val descriptor: PropagationEngineDescriptor = PropagationEngineDescriptor(
        engineName = "Sgp4Sdp4PropagationEngine",
        backendName = backend.name,
        backendVersion = backend.version,
        verification = verification,
        backendSupportsDeepSpace = backend.supportsDeepSpace
    )

    override fun propagate(orbit: OmmRecord, at: PropagationTime): PropagationResult {
        val invalid = validate(orbit)
        if (invalid != null) {
            return PropagationResult.Failure(PropagationFailureReason.INVALID_INPUT, invalid, descriptor)
        }
        val minutes = minutesSinceEpoch(orbit.epochUtc, at)
        if (!minutes.isFinite()) {
            return PropagationResult.Failure(PropagationFailureReason.INVALID_INPUT, "non-finite time offset", descriptor)
        }
        return when (val result = backend.propagate(toBackendInput(orbit), minutes)) {
            is BackendResult.Unavailable ->
                PropagationResult.Failure(PropagationFailureReason.ENGINE_UNAVAILABLE, result.reason, descriptor)
            is BackendResult.Error ->
                PropagationResult.Failure(
                    PropagationFailureReason.PROPAGATION_ERROR,
                    "backend error code=${result.code}: ${result.message}",
                    descriptor
                )
            is BackendResult.Ok -> {
                val s = result.state
                val finite = s.xKm.isFinite() && s.yKm.isFinite() && s.zKm.isFinite() &&
                    s.vxKmS.isFinite() && s.vyKmS.isFinite() && s.vzKmS.isFinite()
                if (!finite) {
                    PropagationResult.Failure(
                        PropagationFailureReason.NON_FINITE_OUTPUT,
                        "backend returned a non-finite state",
                        descriptor
                    )
                } else {
                    PropagationResult.Success(
                        state = TemeState(s.xKm, s.yKm, s.zKm, s.vxKmS, s.vyKmS, s.vzKmS, at),
                        modelUsed = result.modelUsed,
                        minutesFromEpoch = minutes,
                        engine = descriptor
                    )
                }
            }
        }
    }

    companion object {
        private const val MINUTES_PER_DAY = 1440.0

        fun toBackendInput(r: OmmRecord): Sgp4Sdp4Input =
            Sgp4Sdp4Input(
                noradCatId = r.noradCatId,
                objectName = r.objectName,
                objectId = r.objectId,
                classification = r.classification,
                epochUtc = r.epochUtc,
                epochRaw = r.epochRaw,
                meanMotionRadPerMin = r.meanMotionRevPerDay * 2.0 * Math.PI / MINUTES_PER_DAY,
                eccentricity = r.eccentricity,
                inclinationRad = Math.toRadians(r.inclinationDeg),
                raanRad = Math.toRadians(r.raanDeg),
                argOfPericenterRad = Math.toRadians(r.argOfPericenterDeg),
                meanAnomalyRad = Math.toRadians(r.meanAnomalyDeg),
                bstar = r.bstar,
                meanMotionDotRevPerDay2 = r.meanMotionDot,
                meanMotionDdotRevPerDay3 = r.meanMotionDdot,
                ephemerisType = r.ephemerisType,
                elementSetNo = r.elementSetNo,
                revAtEpoch = r.revAtEpoch
            )

        /** Signed minutes from element epoch to the propagation time (both Unix-time UTC). */
        fun minutesSinceEpoch(epochUtc: Instant, at: PropagationTime): Double {
            val d = Duration.between(epochUtc, Instant.ofEpochMilli(at.utcMillis))
            return (d.seconds.toDouble() + d.nano.toDouble() / 1.0e9) / 60.0
        }

        /** Returns a reason string when the record cannot be a valid propagation input, else null. */
        fun validate(r: OmmRecord): String? {
            val finite = listOf(
                r.meanMotionRevPerDay, r.eccentricity, r.inclinationDeg, r.raanDeg,
                r.argOfPericenterDeg, r.meanAnomalyDeg, r.bstar
            ).all { it.isFinite() }
            return when {
                !finite -> "record contains a non-finite orbital value"
                r.meanMotionRevPerDay <= 0.0 -> "mean motion must be > 0"
                r.eccentricity < 0.0 || r.eccentricity >= 1.0 -> "eccentricity must satisfy 0 <= e < 1"
                r.inclinationDeg < 0.0 || r.inclinationDeg > 180.0 -> "inclination must be within [0, 180] degrees"
                else -> null
            }
        }
    }
}
