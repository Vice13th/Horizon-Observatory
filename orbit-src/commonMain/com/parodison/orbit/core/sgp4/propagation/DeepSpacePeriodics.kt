package com.parodison.orbit.core.sgp4.propagation

import com.parodison.orbit.core.sgp4.constants.WGS72Constants.TWO_PI
import com.parodison.orbit.core.sgp4.init.DeepSpaceCommonTerms
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Mean elements corrected by deep-space lunisolar periodics ("dpper"). */
internal data class DeepSpacePeriodicsResult(
    val eccentricity: Double,
    val inclination: Double,
    val node: Double,
    val argPerigee: Double,
    val meanAnomaly: Double,
)

/**
 * Applies the deep-space short-period lunisolar periodics ("dpper" from the Vallado
 * reference) to the already secularly-corrected mean elements. Called on every propagation
 * (not just at initialization); the reference's `peo/pinco/plo/pgho/pho` are always 0 in the
 * 2006 revision (they're initialized that way in dscom and never modified), so the
 * corresponding subtractions are omitted directly instead of carrying around variables that
 * are always zero.
 */
internal fun applyDeepSpacePeriodics(
    common: DeepSpaceCommonTerms,
    t: Double,
    eccentricity: Double,
    inclination: Double,
    node: Double,
    argPerigee: Double,
    meanAnomaly: Double,
): DeepSpacePeriodicsResult {
    val zns = 1.19459e-5
    val zes = 0.01675
    val znl = 1.5835218e-4
    val zel = 0.05490

    var zm = common.zmos + zns * t
    var zf = zm + 2.0 * zes * sin(zm)
    var sinzf = sin(zf)
    var f2 = 0.5 * sinzf * sinzf - 0.25
    var f3 = -0.5 * sinzf * cos(zf)
    val ses = common.se2 * f2 + common.se3 * f3
    val sis = common.si2 * f2 + common.si3 * f3
    val sls = common.sl2 * f2 + common.sl3 * f3 + common.sl4 * sinzf
    val sghs = common.sgh2 * f2 + common.sgh3 * f3 + common.sgh4 * sinzf
    val shs = common.sh2 * f2 + common.sh3 * f3

    zm = common.zmol + znl * t
    zf = zm + 2.0 * zel * sin(zm)
    sinzf = sin(zf)
    f2 = 0.5 * sinzf * sinzf - 0.25
    f3 = -0.5 * sinzf * cos(zf)
    val sel = common.ee2 * f2 + common.e3 * f3
    val sil = common.xi2 * f2 + common.xi3 * f3
    val sll = common.xl2 * f2 + common.xl3 * f3 + common.xl4 * sinzf
    val sghl = common.xgh2 * f2 + common.xgh3 * f3 + common.xgh4 * sinzf
    val shll = common.xh2 * f2 + common.xh3 * f3

    val pe = ses + sel
    var pinc = sis + sil
    val pl = sls + sll
    var pgh = sghs + sghl
    var ph = shs + shll

    var inclp = inclination + pinc
    var ep = eccentricity + pe
    val sinip = sin(inclp)
    val cosip = cos(inclp)

    var argpp = argPerigee
    var nodep = node
    var mp = meanAnomaly

    if (inclp >= 0.2) {
        ph /= sinip
        pgh -= cosip * ph
        argpp += pgh
        nodep += ph
        mp += pl
    } else {
        val sinop = sin(nodep)
        val cosop = cos(nodep)
        var alfdp = sinip * sinop
        var betdp = sinip * cosop
        val dalf = ph * cosop + pinc * cosip * sinop
        val dbet = -ph * sinop + pinc * cosip * cosop
        alfdp += dalf
        betdp += dbet
        nodep = (if (nodep >= 0.0) nodep.mod(TWO_PI) else -((-nodep).mod(TWO_PI)))
        val xls = mp + argpp + pl + pgh + (cosip - pinc * sinip) * nodep
        val xnoh = nodep
        nodep = atan2(alfdp, betdp)
        if (abs(xnoh - nodep) > PI) {
            nodep = if (nodep < xnoh) nodep + TWO_PI else nodep - TWO_PI
        }
        mp += pl
        argpp = xls - mp - cosip * nodep
    }

    return DeepSpacePeriodicsResult(
        eccentricity = ep,
        inclination = inclp,
        node = nodep,
        argPerigee = argpp,
        meanAnomaly = mp,
    )
}
