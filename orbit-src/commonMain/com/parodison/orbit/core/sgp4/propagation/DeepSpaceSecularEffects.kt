package com.parodison.orbit.core.sgp4.propagation

import com.parodison.orbit.core.sgp4.constants.WGS72Constants.TWO_PI
import com.parodison.orbit.core.sgp4.init.DeepSpaceResonance
import com.parodison.orbit.core.sgp4.init.RPTIM
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Mean elements corrected by deep-space secular effects ("dspace"). */
internal data class DeepSpaceSecularResult(
    val eccentricity: Double,
    val inclination: Double,
    val argPerigee: Double,
    val node: Double,
    val meanAnomaly: Double,
    val meanMotion: Double,
)

/**
 * Applies the lunisolar secular effects and, if applicable, the 12h/24h geopotential
 * resonance integration ("dspace" from the Vallado reference), on every propagation.
 *
 * The reference integrates `atime`/`xli`/`xni` incrementally between successive calls (saved
 * as mutable satellite state) for speed — but that accumulation is purely an optimization:
 * the reference routine itself restarts the integration from scratch (`atime=0, xni=no,
 * xli=xlamo`) whenever the requested instant is farther from `atime` than from the origin, or
 * changes sign. Verified empirically against the reference implementation (the exact same
 * result querying out-of-order instants as restarting from scratch on every call), so this
 * function always restarts from scratch: it gives the same result, without needing mutable
 * state between propagations.
 */
internal fun applyDeepSpaceSecularEffects(
    resonance: DeepSpaceResonance,
    argpo: Double,
    argPerigeeDot: Double,
    t: Double,
    gsto: Double,
    no: Double,
    eccentricity: Double,
    inclination: Double,
    argPerigee: Double,
    node: Double,
    meanAnomaly: Double,
): DeepSpaceSecularResult {
    // tc == t (the reference's "time constant" is the same minutesSinceEpoch on every
    // propagation; it only differs from t=0 at initialization, which doesn't go through this
    // function).
    val theta = (gsto + t * RPTIM).mod(TWO_PI)
    val em = eccentricity + resonance.dedt * t
    val inclm = inclination + resonance.didt * t
    var argpm = argPerigee + resonance.domdt * t
    var nodem = node + resonance.dnodt * t
    var mm = meanAnomaly + resonance.dmdt * t
    var nm = no

    if (resonance.irez != 0) {
        val fasx2 = 0.13130908
        val fasx4 = 2.8843198
        val fasx6 = 0.37448087
        val g22 = 5.7686396
        val g32 = 0.95240898
        val g44 = 1.8014998
        val g52 = 1.0508330
        val g54 = 4.4108898
        val stepp = 720.0
        val stepn = -720.0
        val step2 = 259200.0

        var atime = 0.0
        var xli = resonance.xlamo
        var xni = no
        val delt = if (t > 0.0) stepp else stepn

        var xndt: Double
        var xldot: Double
        var xnddt: Double
        var ft: Double

        while (true) {
            if (resonance.irez != 2) {
                xndt = resonance.del1 * sin(xli - fasx2) +
                    resonance.del2 * sin(2.0 * (xli - fasx4)) +
                    resonance.del3 * sin(3.0 * (xli - fasx6))
                xldot = xni + resonance.xfact
                xnddt = resonance.del1 * cos(xli - fasx2) +
                    2.0 * resonance.del2 * cos(2.0 * (xli - fasx4)) +
                    3.0 * resonance.del3 * cos(3.0 * (xli - fasx6))
                xnddt *= xldot
            } else {
                val xomi = argpo + argPerigeeDot * atime
                val x2omi = xomi + xomi
                val x2li = xli + xli
                xndt = resonance.d2201 * sin(x2omi + xli - g22) + resonance.d2211 * sin(xli - g22) +
                    resonance.d3210 * sin(xomi + xli - g32) + resonance.d3222 * sin(-xomi + xli - g32) +
                    resonance.d4410 * sin(x2omi + x2li - g44) + resonance.d4422 * sin(x2li - g44) +
                    resonance.d5220 * sin(xomi + xli - g52) + resonance.d5232 * sin(-xomi + xli - g52) +
                    resonance.d5421 * sin(xomi + x2li - g54) + resonance.d5433 * sin(-xomi + x2li - g54)
                xldot = xni + resonance.xfact
                xnddt = resonance.d2201 * cos(x2omi + xli - g22) + resonance.d2211 * cos(xli - g22) +
                    resonance.d3210 * cos(xomi + xli - g32) + resonance.d3222 * cos(-xomi + xli - g32) +
                    resonance.d5220 * cos(xomi + xli - g52) + resonance.d5232 * cos(-xomi + xli - g52) +
                    2.0 * (
                        resonance.d4410 * cos(x2omi + x2li - g44) + resonance.d4422 * cos(x2li - g44) +
                            resonance.d5421 * cos(xomi + x2li - g54) + resonance.d5433 * cos(-xomi + x2li - g54)
                        )
                xnddt *= xldot
            }

            if (abs(t - atime) >= stepp) {
                xli += xldot * delt + xndt * step2
                xni += xndt * delt + xnddt * step2
                atime += delt
            } else {
                ft = t - atime
                nm = xni + xndt * ft + xnddt * ft * ft * 0.5
                val xl = xli + xldot * ft + xndt * ft * ft * 0.5
                mm = if (resonance.irez != 1) {
                    xl - 2.0 * nodem + 2.0 * theta
                } else {
                    xl - nodem - argpm + theta
                }
                break
            }
        }
    }

    return DeepSpaceSecularResult(
        eccentricity = em,
        inclination = inclm,
        argPerigee = argpm,
        node = nodem,
        meanAnomaly = mm,
        meanMotion = nm,
    )
}
