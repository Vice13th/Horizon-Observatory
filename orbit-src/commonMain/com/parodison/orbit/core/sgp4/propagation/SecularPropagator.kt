package com.parodison.orbit.core.sgp4.propagation

import com.parodison.orbit.core.sgp4.constants.WGS72Constants.TWO_PI
import com.parodison.orbit.core.sgp4.constants.WGS72Constants.X2O3
import com.parodison.orbit.core.sgp4.constants.WGS72Constants.XKE
import com.parodison.orbit.core.sgp4.init.RecoveredElements
import com.parodison.orbit.core.sgp4.init.SecularDragCoefficients
import com.parodison.orbit.core.sgp4.init.SecularGravityCoefficients
import com.parodison.orbit.core.sgp4.model.MeanElements
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Argument of perigee, mean anomaly and node already corrected for atmospheric drag (and its
 * effect on `tempa`/`tempe`/`templ`), prior to any deep-space correction. It's the same
 * computation for near-earth and deep-space satellites (the Vallado reference doesn't
 * distinguish the regime at this step) — deep-space inserts its own lunisolar secular and
 * resonance corrections right after this point, before finalization.
 */
internal data class DragAdjustedMeanElements(
    val argPerigee: Double,
    val meanAnomaly: Double,
    val node: Double,
    val tempa: Double,
    val tempe: Double,
    val templ: Double,
)

/** Applies atmospheric drag + Earth oblateness (J2/J4) to the mean elements at instant t. */
internal object SecularPropagator {

    fun computeDragAdjustedElements(
        minutesSinceEpoch: Double,
        elements: MeanElements,
        gravity: SecularGravityCoefficients,
        drag: SecularDragCoefficients,
    ): DragAdjustedMeanElements {
        val t = minutesSinceEpoch
        val t2 = t * t

        val xmdf = elements.meanAnomalyRad + gravity.meanAnomalyDot * t
        val argpdf = elements.argPerigeeRad + gravity.argPerigeeDot * t
        val nodedf = elements.raanRad + gravity.nodeDot * t

        var argpm = argpdf
        var mm = xmdf
        val nodem = nodedf + drag.nodecf * t2
        var tempa = 1.0 - drag.cc1 * t
        var tempe = elements.bstar * drag.cc4 * t
        var templ = drag.t2cof * t2

        if (!drag.isSimplifiedDrag) {
            val delomg = drag.omgcof * t
            val delmtemp = 1.0 + drag.eta * cos(xmdf)
            val delm = drag.xmcof * (delmtemp.pow(3) - drag.delmo)
            val temp = delomg + delm
            mm = xmdf + temp
            argpm = argpdf - temp
            val t3 = t2 * t
            val t4 = t3 * t
            tempa -= drag.d2 * t2 + drag.d3 * t3 + drag.d4 * t4
            tempe += elements.bstar * drag.cc5 * (sin(mm) - drag.sinmao)
            templ += drag.t3cof * t3 + t4 * (drag.t4cof + t * drag.t5cof)
        }

        return DragAdjustedMeanElements(
            argPerigee = argpm,
            meanAnomaly = mm,
            node = nodem,
            tempa = tempa,
            tempe = tempe,
            templ = templ,
        )
    }

    /**
     * Last stage, common to both regimes: recovers semi-major axis/mean motion from `tempa`,
     * applies the drag eccentricity correction (`tempe`), and normalizes everything to
     * [0, 2π). [eccentricity]/[meanMotion]/[argPerigee]/[node]/[meanAnomaly] must already
     * include any deep-space correction (if applicable).
     */
    fun finalizeSecularEffects(
        dragAdjusted: DragAdjustedMeanElements,
        originalMeanMotion: Double,
        eccentricity: Double,
        meanMotion: Double,
        inclination: Double,
        argPerigee: Double,
        node: Double,
        meanAnomaly: Double,
    ): SecularEffects {
        require(originalMeanMotion > 0.0) { "Movimiento medio inválido durante la propagación" }
        check(meanMotion > 0.0) { "Movimiento medio no positivo durante la propagación: $meanMotion" }

        val am = (XKE / meanMotion).pow(X2O3) * dragAdjusted.tempa * dragAdjusted.tempa
        val nm = XKE / am.pow(1.5)
        var em = eccentricity - dragAdjusted.tempe

        check(em < 1.0 && em >= -0.001) { "Excentricidad fuera de rango durante la propagación: $em" }
        if (em < 1.0e-6) em = 1.0e-6

        val mm = meanAnomaly + originalMeanMotion * dragAdjusted.templ
        val xlm = mm + argPerigee + node

        val nodemMod = node.mod(TWO_PI)
        val argpmMod = argPerigee.mod(TWO_PI)
        val xlmMod = xlm.mod(TWO_PI)
        val mmMod = (xlmMod - argpmMod - nodemMod).mod(TWO_PI)

        return SecularEffects(
            semiMajorAxis = am,
            meanMotion = nm,
            eccentricity = em,
            inclination = inclination,
            meanAnomaly = mmMod,
            argPerigee = argpmMod,
            node = nodemMod,
        )
    }

    /** Near-earth path: no deep-space corrections. */
    fun computeSecularEffects(
        minutesSinceEpoch: Double,
        elements: MeanElements,
        recovered: RecoveredElements,
        gravity: SecularGravityCoefficients,
        drag: SecularDragCoefficients,
    ): SecularEffects {
        val dragAdjusted = computeDragAdjustedElements(minutesSinceEpoch, elements, gravity, drag)
        return finalizeSecularEffects(
            dragAdjusted = dragAdjusted,
            originalMeanMotion = recovered.meanMotionRadPerMin,
            eccentricity = elements.eccentricity,
            meanMotion = recovered.meanMotionRadPerMin,
            inclination = elements.inclinationRad,
            argPerigee = dragAdjusted.argPerigee,
            node = dragAdjusted.node,
            meanAnomaly = dragAdjusted.meanAnomaly,
        )
    }
}
