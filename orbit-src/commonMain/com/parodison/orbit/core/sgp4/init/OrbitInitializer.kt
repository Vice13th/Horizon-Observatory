package com.parodison.orbit.core.sgp4.init

import com.parodison.orbit.core.sgp4.constants.WGS72Constants.DEEP_SPACE_PERIOD_THRESHOLD_MIN
import com.parodison.orbit.core.sgp4.constants.WGS72Constants.EARTH_RADIUS_KM
import com.parodison.orbit.core.sgp4.constants.WGS72Constants.J2
import com.parodison.orbit.core.sgp4.constants.WGS72Constants.TWO_PI
import com.parodison.orbit.core.sgp4.constants.WGS72Constants.X2O3
import com.parodison.orbit.core.sgp4.constants.WGS72Constants.XKE
import com.parodison.orbit.core.sgp4.model.MeanElements
import com.parodison.orbit.core.sgp4.time.greenwichSiderealTime
import com.parodison.orbit.core.sgp4.time.toJulianDate
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Recovers the "original" mean motion and semi-major axis from the TLE's Kozai mean motion,
 * and classifies the resulting orbital regime.
 */
internal object OrbitInitializer {

    fun recoverMeanMotionAndSemiMajorAxis(elements: MeanElements): RecoveredElements {
        val cosio = cos(elements.inclinationRad)
        val cosio2 = cosio * cosio
        val eccsq = elements.eccentricity * elements.eccentricity
        val omeosq = 1.0 - eccsq
        val betao2 = omeosq
        val betao = sqrt(betao2)

        val ak = (XKE / elements.meanMotionRadPerMin).pow(X2O3)
        val d1 = 0.75 * J2 * (3.0 * cosio2 - 1.0) / (betao * betao2)
        var del = d1 / (ak * ak)
        val adel = ak * (1.0 - del * del - del * (1.0 / 3.0 + 134.0 * del * del / 81.0))
        del = d1 / (adel * adel)
        val noUnkozai = elements.meanMotionRadPerMin / (1.0 + del)

        val ao = (XKE / noUnkozai).pow(X2O3)
        val sinio = sin(elements.inclinationRad)
        val po = ao * omeosq
        val con42 = 1.0 - 5.0 * cosio2
        val con41 = -con42 - cosio2 - cosio2
        val posq = po * po
        val rp = ao * (1.0 - elements.eccentricity)
        val perigeeAltitudeKm = (rp - 1.0) * EARTH_RADIUS_KM
        val gsto = greenwichSiderealTime(elements.epoch.toJulianDate())

        return RecoveredElements(
            meanMotionRadPerMin = noUnkozai,
            semiMajorAxisEarthRadii = ao,
            cosInclination = cosio,
            cosInclinationSquared = cosio2,
            sinInclination = sinio,
            oneMinusEccentricitySquared = omeosq,
            beta = betao,
            con41 = con41,
            con42 = con42,
            perigeeRadiusEarthRadii = rp,
            perigeeAltitudeKm = perigeeAltitudeKm,
            semiLatusRectumSquared = posq,
            greenwichSiderealTimeAtEpoch = gsto,
        )
    }

    fun classifyOrbitRegime(recovered: RecoveredElements): OrbitRegime {
        val periodMinutes = TWO_PI / recovered.meanMotionRadPerMin
        return if (periodMinutes >= DEEP_SPACE_PERIOD_THRESHOLD_MIN) {
            OrbitRegime.DEEP_SPACE
        } else {
            OrbitRegime.NEAR_EARTH
        }
    }
}
