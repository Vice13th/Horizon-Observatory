package com.parodison.orbit.core.sgp4

import com.parodison.orbit.core.sgp4.constants.WGS72Constants.J3OJ2
import com.parodison.orbit.core.sgp4.constants.WGS72Constants.TWO_PI
import com.parodison.orbit.core.sgp4.constants.WGS72Constants.XKE
import com.parodison.orbit.core.sgp4.init.DeepSpaceInitializer
import com.parodison.orbit.core.sgp4.init.DeepSpaceState
import com.parodison.orbit.core.sgp4.init.OrbitInitializer
import com.parodison.orbit.core.sgp4.init.OrbitRegime
import com.parodison.orbit.core.sgp4.init.SecularCoefficientsCalculator
import com.parodison.orbit.core.sgp4.model.MeanElements
import com.parodison.orbit.core.sgp4.model.PositionVelocity
import com.parodison.orbit.core.sgp4.propagation.KeplerSolver
import com.parodison.orbit.core.sgp4.propagation.SecularEffects
import com.parodison.orbit.core.sgp4.propagation.SecularPropagator
import com.parodison.orbit.core.sgp4.propagation.applyDeepSpacePeriodics
import com.parodison.orbit.core.sgp4.propagation.applyDeepSpaceSecularEffects
import com.parodison.orbit.core.sgp4.propagation.applyLongPeriodPeriodics
import com.parodison.orbit.core.sgp4.propagation.computeOrientationVectors
import com.parodison.orbit.core.sgp4.propagation.computePositionAndVelocity
import com.parodison.orbit.core.sgp4.propagation.computeShortPeriodPeriodics
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.DurationUnit
import kotlin.time.Instant

/**
 * SGP4/SDP4 propagator following the Vallado/Crawford/Hujsak/Kelso (2006) revision of
 * Spacetrack Report #3 (WGS-72). Supports both near-earth orbits (SGP4, period < 225 min) and
 * deep-space orbits (SDP4, period >= 225 min: GEO, MEO, HEO) — the deep-space model adds
 * lunisolar perturbations and 12h/24h geopotential resonance.
 *
 * The parameters must already be converted to the algorithm's internal units (radians,
 * rad/min); see [com.parodison.orbit.core.sgp4.model.MeanElements].
 */

class SGP4Engine(
    epoch: Instant,
    meanMotionRadPerMin: Double,
    eccentricity: Double,
    inclinationRad: Double,
    raanRad: Double,
    argPerigeeRad: Double,
    meanAnomalyRad: Double,
    bstar: Double,
) {
    private val meanElements = MeanElements(
        epoch = epoch,
        meanMotionRadPerMin = meanMotionRadPerMin,
        eccentricity = eccentricity,
        inclinationRad = inclinationRad,
        raanRad = raanRad,
        argPerigeeRad = argPerigeeRad,
        meanAnomalyRad = meanAnomalyRad,
        bstar = bstar,
    )

    private val recoveredElements = OrbitInitializer.recoverMeanMotionAndSemiMajorAxis(meanElements)
    private val orbitRegime = OrbitInitializer.classifyOrbitRegime(recoveredElements)
    private val secularGravity = SecularCoefficientsCalculator.computeSecularGravityCoefficients(recoveredElements)
    private val secularDrag = SecularCoefficientsCalculator.computeSecularDragCoefficients(
        meanElements, recoveredElements, isDeepSpace = orbitRegime == OrbitRegime.DEEP_SPACE,
    )

    private val deepSpaceState: DeepSpaceState? = if (orbitRegime == OrbitRegime.DEEP_SPACE) {
        DeepSpaceInitializer.initialize(epoch, meanElements, recoveredElements, secularGravity)
    } else {
        null
    }

    /**
     * Orbital period (minutes), from the mean motion already corrected for the Kozai effect.
     */
    val periodMinutes: Double = TWO_PI / recoveredElements.meanMotionRadPerMin

    /** Satellite position/velocity (TEME, km and km/s) at instant [at]. */
    fun propagate(at: Instant): PositionVelocity {
        val minutesSinceEpoch = (at - meanElements.epoch).toDouble(DurationUnit.MINUTES)
        val deepSpace = deepSpaceState

        val secularEffects: SecularEffects
        var aycof = secularDrag.aycof
        var xlcof = secularDrag.xlcof
        var con41 = recoveredElements.con41
        var x1mth2 = secularDrag.x1mth2
        var x7thm1 = secularDrag.x7thm1
        var cosInclination = recoveredElements.cosInclination
        var sinInclination = recoveredElements.sinInclination

        if (deepSpace == null) {
            secularEffects = SecularPropagator.computeSecularEffects(
                minutesSinceEpoch, meanElements, recoveredElements, secularGravity, secularDrag,
            )
        } else {
            val dragAdjusted = SecularPropagator.computeDragAdjustedElements(
                minutesSinceEpoch, meanElements, secularGravity, secularDrag,
            )
            val secular = applyDeepSpaceSecularEffects(
                resonance = deepSpace.resonance,
                argpo = meanElements.argPerigeeRad,
                argPerigeeDot = secularGravity.argPerigeeDot,
                t = minutesSinceEpoch,
                gsto = recoveredElements.greenwichSiderealTimeAtEpoch,
                no = recoveredElements.meanMotionRadPerMin,
                eccentricity = meanElements.eccentricity,
                inclination = meanElements.inclinationRad,
                argPerigee = dragAdjusted.argPerigee,
                node = dragAdjusted.node,
                meanAnomaly = dragAdjusted.meanAnomaly,
            )
            val finalized = SecularPropagator.finalizeSecularEffects(
                dragAdjusted = dragAdjusted,
                originalMeanMotion = recoveredElements.meanMotionRadPerMin,
                eccentricity = secular.eccentricity,
                meanMotion = secular.meanMotion,
                inclination = secular.inclination,
                argPerigee = secular.argPerigee,
                node = secular.node,
                meanAnomaly = secular.meanAnomaly,
            )

            val periodics = applyDeepSpacePeriodics(
                common = deepSpace.common,
                t = minutesSinceEpoch,
                eccentricity = finalized.eccentricity,
                inclination = finalized.inclination,
                node = finalized.node,
                argPerigee = finalized.argPerigee,
                meanAnomaly = finalized.meanAnomaly,
            )
            var correctedInclination = periodics.inclination
            var correctedNode = periodics.node
            var correctedArgPerigee = periodics.argPerigee
            if (correctedInclination < 0.0) {
                correctedInclination = -correctedInclination
                correctedNode += kotlin.math.PI
                correctedArgPerigee -= kotlin.math.PI
            }

            secularEffects = finalized.copy(
                eccentricity = periodics.eccentricity,
                inclination = correctedInclination,
                node = correctedNode,
                argPerigee = correctedArgPerigee,
                meanAnomaly = periodics.meanAnomaly,
            )

            sinInclination = sin(correctedInclination)
            cosInclination = cos(correctedInclination)
            aycof = -0.5 * J3OJ2 * sinInclination
            xlcof = if (abs(cosInclination + 1.0) > 1.5e-12) {
                -0.25 * J3OJ2 * sinInclination * (3.0 + 5.0 * cosInclination) / (1.0 + cosInclination)
            } else {
                -0.25 * J3OJ2 * sinInclination * (3.0 + 5.0 * cosInclination) / 1.5e-12
            }
            val cosInclinationSquared = cosInclination * cosInclination
            con41 = 3.0 * cosInclinationSquared - 1.0
            x1mth2 = 1.0 - cosInclinationSquared
            x7thm1 = 7.0 * cosInclinationSquared - 1.0
        }

        val longPeriod = applyLongPeriodPeriodics(secularEffects, aycof, xlcof)
        val eccentricAnomaly = KeplerSolver.solveKeplerEquation(
            longPeriod.meanLongitude, secularEffects.node, longPeriod.axnl, longPeriod.aynl,
        )
        val shortPeriod = computeShortPeriodPeriodics(
            secularEffects, longPeriod, eccentricAnomaly,
            con41, x1mth2, x7thm1, cosInclination, sinInclination,
            secularEffects.inclination, XKE,
        )
        val orientation = computeOrientationVectors(shortPeriod)
        return computePositionAndVelocity(shortPeriod, orientation)
    }
}