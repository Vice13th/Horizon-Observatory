package com.parodison.orbit.core.sgp4.init

import com.parodison.orbit.core.sgp4.constants.WGS72Constants.EARTH_RADIUS_KM
import com.parodison.orbit.core.sgp4.constants.WGS72Constants.J2
import com.parodison.orbit.core.sgp4.constants.WGS72Constants.J3OJ2
import com.parodison.orbit.core.sgp4.constants.WGS72Constants.J4
import com.parodison.orbit.core.sgp4.constants.WGS72Constants.X2O3
import com.parodison.orbit.core.sgp4.model.MeanElements
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Computes, once per satellite, the secular gravity rates (J2/J4) and the atmospheric drag
 * (bstar) coefficients used on every propagation.
 */
internal object SecularCoefficientsCalculator {

    fun computeSecularGravityCoefficients(
        recovered: RecoveredElements,
    ): SecularGravityCoefficients {
        val cosio2 = recovered.cosInclinationSquared
        val cosio4 = cosio2 * cosio2
        val rteosq = recovered.beta // sqrt(1 - e^2)
        val pinvsq = 1.0 / recovered.semiLatusRectumSquared

        val temp1 = 1.5 * J2 * pinvsq * recovered.meanMotionRadPerMin
        val temp2 = 0.5 * temp1 * J2 * pinvsq
        val temp3 = -0.46875 * J4 * pinvsq * pinvsq * recovered.meanMotionRadPerMin

        val meanAnomalyDot = recovered.meanMotionRadPerMin +
            0.5 * temp1 * rteosq * recovered.con41 +
            0.0625 * temp2 * rteosq * (13.0 - 78.0 * cosio2 + 137.0 * cosio4)

        val argPerigeeDot = -0.5 * temp1 * recovered.con42 +
            0.0625 * temp2 * (7.0 - 114.0 * cosio2 + 395.0 * cosio4) +
            temp3 * (3.0 - 36.0 * cosio2 + 49.0 * cosio4)

        val xhdot1 = -temp1 * recovered.cosInclination
        val nodeDot = xhdot1 + (0.5 * temp2 * (4.0 - 19.0 * cosio2) +
            2.0 * temp3 * (3.0 - 7.0 * cosio2)) * recovered.cosInclination

        return SecularGravityCoefficients(
            meanAnomalyDot = meanAnomalyDot,
            argPerigeeDot = argPerigeeDot,
            nodeDot = nodeDot,
        )
    }

    fun computeSecularDragCoefficients(
        elements: MeanElements,
        recovered: RecoveredElements,
        isDeepSpace: Boolean,
    ): SecularDragCoefficients {
        val ao = recovered.semiMajorAxisEarthRadii
        val rp = recovered.perigeeRadiusEarthRadii
        // The reference forces simplified drag for ANY deep-space satellite, regardless of
        // perigee altitude (not just for low perigees).
        val isSimplifiedDrag = isDeepSpace || rp < (220.0 / EARTH_RADIUS_KM + 1.0)

        var sfour = 78.0 / EARTH_RADIUS_KM + 1.0
        var qzms24 = ((120.0 - 78.0) / EARTH_RADIUS_KM).pow(4)
        val perigeeKm = recovered.perigeeAltitudeKm

        // For low perigees, s and qoms2t are adjusted (more real drag near the surface).
        if (perigeeKm < 156.0) {
            sfour = if (perigeeKm < 98.0) 20.0 else perigeeKm - 78.0
            val qzms24temp = (120.0 - sfour) / EARTH_RADIUS_KM
            qzms24 = qzms24temp.pow(4)
            sfour = sfour / EARTH_RADIUS_KM + 1.0
        }

        val pinvsq = 1.0 / recovered.semiLatusRectumSquared
        val tsi = 1.0 / (ao - sfour)
        val eta = ao * elements.eccentricity * tsi
        val etasq = eta * eta
        val eeta = elements.eccentricity * eta
        val psisq = abs(1.0 - etasq)
        val coef = qzms24 * tsi.pow(4)
        val coef1 = coef / psisq.pow(3.5)

        val cc2 = coef1 * recovered.meanMotionRadPerMin * (ao * (1.0 + 1.5 * etasq + eeta *
            (4.0 + etasq)) + 0.375 * J2 * tsi / psisq * recovered.con41 *
            (8.0 + 3.0 * etasq * (8.0 + etasq)))
        val cc1 = elements.bstar * cc2

        var cc3 = 0.0
        if (elements.eccentricity > 1.0e-4) {
            cc3 = -2.0 * coef * tsi * J3OJ2 * recovered.meanMotionRadPerMin *
                recovered.sinInclination / elements.eccentricity
        }

        val x1mth2 = 1.0 - recovered.cosInclinationSquared
        val cc4 = 2.0 * recovered.meanMotionRadPerMin * coef1 * ao * recovered.oneMinusEccentricitySquared *
            (eta * (2.0 + 0.5 * etasq) + elements.eccentricity *
                (0.5 + 2.0 * etasq) - J2 * tsi / (ao * psisq) *
                (-3.0 * recovered.con41 * (1.0 - 2.0 * eeta + etasq * (1.5 - 0.5 * eeta)) +
                    0.75 * x1mth2 * (2.0 * etasq - eeta * (1.0 + etasq)) * cos(2.0 * elements.argPerigeeRad)))

        val cc5 = 2.0 * coef1 * ao * recovered.oneMinusEccentricitySquared *
            (1.0 + 2.75 * (etasq + eeta) + eeta * etasq)

        val temp1 = 1.5 * J2 * pinvsq * recovered.meanMotionRadPerMin
        val xhdot1 = -temp1 * recovered.cosInclination
        val nodecf = 3.5 * recovered.oneMinusEccentricitySquared * xhdot1 * cc1

        val t2cof = 1.5 * cc1

        val temp4 = 1.5e-12
        val xlcof = if (abs(recovered.cosInclination + 1.0) > 1.5e-12) {
            -0.25 * J3OJ2 * recovered.sinInclination * (3.0 + 5.0 * recovered.cosInclination) /
                (1.0 + recovered.cosInclination)
        } else {
            -0.25 * J3OJ2 * recovered.sinInclination * (3.0 + 5.0 * recovered.cosInclination) / temp4
        }
        val aycof = -0.5 * J3OJ2 * recovered.sinInclination

        val delmo = (1.0 + eta * cos(elements.meanAnomalyRad)).pow(3)
        val sinmao = sin(elements.meanAnomalyRad)
        val x7thm1 = 7.0 * recovered.cosInclinationSquared - 1.0
        val omgcof = elements.bstar * cc3 * cos(elements.argPerigeeRad)
        val xmcof = if (elements.eccentricity > 1.0e-4) -X2O3 * coef * elements.bstar / eeta else 0.0

        var d2 = 0.0
        var d3 = 0.0
        var d4 = 0.0
        var t3cof = 0.0
        var t4cof = 0.0
        var t5cof = 0.0

        if (!isSimplifiedDrag) {
            val cc1sq = cc1 * cc1
            d2 = 4.0 * ao * tsi * cc1sq
            val temp = d2 * tsi * cc1 / 3.0
            d3 = (17.0 * ao + sfour) * temp
            d4 = 0.5 * temp * ao * tsi * (221.0 * ao + 31.0 * sfour) * cc1
            t3cof = d2 + 2.0 * cc1sq
            t4cof = 0.25 * (3.0 * d3 + cc1 * (12.0 * d2 + 10.0 * cc1sq))
            t5cof = 0.2 * (3.0 * d4 + 12.0 * cc1 * d3 + 6.0 * d2 * d2 + 15.0 * cc1sq * (2.0 * d2 + cc1sq))
        }

        return SecularDragCoefficients(
            isSimplifiedDrag = isSimplifiedDrag,
            eta = eta,
            cc1 = cc1,
            cc4 = cc4,
            cc5 = cc5,
            d2 = d2,
            d3 = d3,
            d4 = d4,
            t2cof = t2cof,
            t3cof = t3cof,
            t4cof = t4cof,
            t5cof = t5cof,
            omgcof = omgcof,
            xmcof = xmcof,
            nodecf = nodecf,
            delmo = delmo,
            sinmao = sinmao,
            x7thm1 = x7thm1,
            x1mth2 = x1mth2,
            xlcof = xlcof,
            aycof = aycof,
        )
    }
}
