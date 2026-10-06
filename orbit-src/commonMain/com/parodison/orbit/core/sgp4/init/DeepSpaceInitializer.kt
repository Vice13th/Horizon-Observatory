package com.parodison.orbit.core.sgp4.init

import com.parodison.orbit.core.sgp4.constants.WGS72Constants.TWO_PI
import com.parodison.orbit.core.sgp4.constants.WGS72Constants.XKE
import com.parodison.orbit.core.sgp4.model.MeanElements
import com.parodison.orbit.core.sgp4.time.toJulianDate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Instant

/**
 * Deep-space initialization (SDP4): common lunisolar terms ("dscom") and 12h/24h geopotential
 * resonance ("dsinit"), computed once per satellite.
 *
 * A direct translation of the `dscom`/`dsinit` routines from the Vallado/Crawford/Hujsak/
 * Kelso (2006) revision of Spacetrack Report #3 — deliberately using the same variable names
 * as the reference, so it's possible to compare it line by line against it.
 */
internal object DeepSpaceInitializer {

    fun initialize(
        epoch: Instant,
        elements: MeanElements,
        recovered: RecoveredElements,
        gravity: SecularGravityCoefficients,
    ): DeepSpaceState {
        // The reference's "day": days since 1949-12-31 00h + 18261.5, with tc=0 (dscom only
        // runs once, at epoch; the time advance is applied later, inside dpper/dspace,
        // without recomputing day/zmol/zmos). The +18261.5 is already included in
        // toJulianDate1949Offset() (2415020.0 = 2433281.5 - 18261.5).
        val day = epoch.toJulianDate1949Offset()

        val ep = elements.eccentricity
        val argpp = elements.argPerigeeRad
        val inclp = elements.inclinationRad
        val nodep = elements.raanRad
        val np = recovered.meanMotionRadPerMin

        // -------------------------- constants (dscom) --------------------------
        val zes = 0.01675
        val zel = 0.05490
        val c1ss = 2.9864797e-6
        val c1l = 4.7968065e-7
        val zsinis = 0.39785416
        val zcosis = 0.91744867
        val zcosgs = 0.1945905
        val zsings = -0.98088458

        val snodm = sin(nodep)
        val cnodm = cos(nodep)
        val sinomm = sin(argpp)
        val cosomm = cos(argpp)
        val sinim = sin(inclp)
        val cosim = cos(inclp)
        val emsq = ep * ep
        val betasq = 1.0 - emsq
        val rtemsq = sqrt(betasq)

        val xnodce = (4.5236020 - 9.2422029e-4 * day).mod(TWO_PI)
        val stem = sin(xnodce)
        val ctem = cos(xnodce)
        val zcosil = 0.91375164 - 0.03568096 * ctem
        val zsinil = sqrt(1.0 - zcosil * zcosil)
        val zsinhl = 0.089683511 * stem / zsinil
        val zcoshl = sqrt(1.0 - zsinhl * zsinhl)
        val gam = 5.8351514 + 0.0019443680 * day
        var zx = 0.39785416 * stem / zsinil
        val zy = zcoshl * ctem + 0.91744867 * zsinhl * stem
        zx = atan2(zx, zy)
        zx = gam + zx - xnodce
        val zcosgl = cos(zx)
        val zsingl = sin(zx)

        // ---- solar iteration (lsflg=1) followed by lunar (lsflg=2), as in the reference ----
        var zcosg = zcosgs
        var zsing = zsings
        var zcosi = zcosis
        var zsini = zsinis
        var zcosh = cnodm
        var zsinh = snodm
        var cc = c1ss
        val xnoi = 1.0 / np

        var s1 = 0.0; var s2 = 0.0; var s3 = 0.0; var s4 = 0.0; var s5 = 0.0; var s6 = 0.0; var s7 = 0.0
        var ss1 = 0.0; var ss2 = 0.0; var ss3 = 0.0; var ss4 = 0.0; var ss5 = 0.0; var ss6 = 0.0; var ss7 = 0.0
        var z1 = 0.0; var z2 = 0.0; var z3 = 0.0
        var z11 = 0.0; var z12 = 0.0; var z13 = 0.0
        var z21 = 0.0; var z22 = 0.0; var z23 = 0.0
        var z31 = 0.0; var z32 = 0.0; var z33 = 0.0
        var sz1 = 0.0; var sz2 = 0.0; var sz3 = 0.0
        var sz11 = 0.0; var sz12 = 0.0; var sz13 = 0.0
        var sz21 = 0.0; var sz22 = 0.0; var sz23 = 0.0
        var sz31 = 0.0; var sz32 = 0.0; var sz33 = 0.0

        for (lsflg in 1..2) {
            val a1 = zcosg * zcosh + zsing * zcosi * zsinh
            val a3 = -zsing * zcosh + zcosg * zcosi * zsinh
            val a7 = -zcosg * zsinh + zsing * zcosi * zcosh
            val a8 = zsing * zsini
            val a9 = zsing * zsinh + zcosg * zcosi * zcosh
            val a10 = zcosg * zsini
            val a2 = cosim * a7 + sinim * a8
            val a4 = cosim * a9 + sinim * a10
            val a5 = -sinim * a7 + cosim * a8
            val a6 = -sinim * a9 + cosim * a10

            val x1 = a1 * cosomm + a2 * sinomm
            val x2 = a3 * cosomm + a4 * sinomm
            val x3 = -a1 * sinomm + a2 * cosomm
            val x4 = -a3 * sinomm + a4 * cosomm
            val x5 = a5 * sinomm
            val x6 = a6 * sinomm
            val x7 = a5 * cosomm
            val x8 = a6 * cosomm

            z31 = 12.0 * x1 * x1 - 3.0 * x3 * x3
            z32 = 24.0 * x1 * x2 - 6.0 * x3 * x4
            z33 = 12.0 * x2 * x2 - 3.0 * x4 * x4
            z1 = 3.0 * (a1 * a1 + a2 * a2) + z31 * emsq
            z2 = 6.0 * (a1 * a3 + a2 * a4) + z32 * emsq
            z3 = 3.0 * (a3 * a3 + a4 * a4) + z33 * emsq
            z11 = -6.0 * a1 * a5 + emsq * (-24.0 * x1 * x7 - 6.0 * x3 * x5)
            z12 = -6.0 * (a1 * a6 + a3 * a5) + emsq *
                (-24.0 * (x2 * x7 + x1 * x8) - 6.0 * (x3 * x6 + x4 * x5))
            z13 = -6.0 * a3 * a6 + emsq * (-24.0 * x2 * x8 - 6.0 * x4 * x6)
            z21 = 6.0 * a2 * a5 + emsq * (24.0 * x1 * x5 - 6.0 * x3 * x7)
            z22 = 6.0 * (a4 * a5 + a2 * a6) + emsq *
                (24.0 * (x2 * x5 + x1 * x6) - 6.0 * (x4 * x7 + x3 * x8))
            z23 = 6.0 * a4 * a6 + emsq * (24.0 * x2 * x6 - 6.0 * x4 * x8)
            z1 += z1 + betasq * z31
            z2 += z2 + betasq * z32
            z3 += z3 + betasq * z33
            s3 = cc * xnoi
            s2 = -0.5 * s3 / rtemsq
            s4 = s3 * rtemsq
            s1 = -15.0 * ep * s4
            s5 = x1 * x3 + x2 * x4
            s6 = x2 * x3 + x1 * x4
            s7 = x2 * x4 - x1 * x3

            if (lsflg == 1) {
                ss1 = s1; ss2 = s2; ss3 = s3; ss4 = s4; ss5 = s5; ss6 = s6; ss7 = s7
                sz1 = z1; sz2 = z2; sz3 = z3
                sz11 = z11; sz12 = z12; sz13 = z13
                sz21 = z21; sz22 = z22; sz23 = z23
                sz31 = z31; sz32 = z32; sz33 = z33
                zcosg = zcosgl; zsing = zsingl; zcosi = zcosil; zsini = zsinil
                zcosh = zcoshl * cnodm + zsinhl * snodm
                zsinh = snodm * zcoshl - cnodm * zsinhl
                cc = c1l
            }
        }

        val zmol = (4.7199672 + 0.22997150 * day - gam).mod(TWO_PI)
        val zmos = (6.2565837 + 0.017201977 * day).mod(TWO_PI)

        // ------------------------ solar terms ------------------------
        val se2 = 2.0 * ss1 * ss6
        val se3 = 2.0 * ss1 * ss7
        val si2 = 2.0 * ss2 * sz12
        val si3 = 2.0 * ss2 * (sz13 - sz11)
        val sl2 = -2.0 * ss3 * sz2
        val sl3 = -2.0 * ss3 * (sz3 - sz1)
        val sl4 = -2.0 * ss3 * (-21.0 - 9.0 * emsq) * zes
        val sgh2 = 2.0 * ss4 * sz32
        val sgh3 = 2.0 * ss4 * (sz33 - sz31)
        val sgh4 = -18.0 * ss4 * zes
        val sh2 = -2.0 * ss2 * sz22
        val sh3 = -2.0 * ss2 * (sz23 - sz21)

        // ------------------------ lunar terms -------------------------
        val ee2 = 2.0 * s1 * s6
        val e3 = 2.0 * s1 * s7
        val xi2 = 2.0 * s2 * z12
        val xi3 = 2.0 * s2 * (z13 - z11)
        val xl2 = -2.0 * s3 * z2
        val xl3 = -2.0 * s3 * (z3 - z1)
        val xl4 = -2.0 * s3 * (-21.0 - 9.0 * emsq) * zel
        val xgh2 = 2.0 * s4 * z32
        val xgh3 = 2.0 * s4 * (z33 - z31)
        val xgh4 = -18.0 * s4 * zel
        val xh2 = -2.0 * s2 * z22
        val xh3 = -2.0 * s2 * (z23 - z21)

        val common = DeepSpaceCommonTerms(
            e3 = e3, ee2 = ee2,
            se2 = se2, se3 = se3,
            sgh2 = sgh2, sgh3 = sgh3, sgh4 = sgh4,
            sh2 = sh2, sh3 = sh3,
            si2 = si2, si3 = si3,
            sl2 = sl2, sl3 = sl3, sl4 = sl4,
            xgh2 = xgh2, xgh3 = xgh3, xgh4 = xgh4,
            xh2 = xh2, xh3 = xh3,
            xi2 = xi2, xi3 = xi3,
            xl2 = xl2, xl3 = xl3, xl4 = xl4,
            zmol = zmol, zmos = zmos,
        )

        val resonance = initializeResonance(
            elements = elements,
            recovered = recovered,
            gravity = gravity,
            cosim = cosim,
            sinim = sinim,
            emsq = emsq,
            s1 = s1, s2 = s2, s3 = s3, s4 = s4, s5 = s5,
            ss1 = ss1, ss2 = ss2, ss3 = ss3, ss4 = ss4, ss5 = ss5,
            sz1 = sz1, sz3 = sz3, sz11 = sz11, sz13 = sz13,
            sz21 = sz21, sz23 = sz23, sz31 = sz31, sz33 = sz33,
            z1 = z1, z3 = z3, z11 = z11, z13 = z13,
            z21 = z21, z23 = z23, z31 = z31, z33 = z33,
        )

        return DeepSpaceState(common, resonance)
    }

    @Suppress("LongParameterList")
    private fun initializeResonance(
        elements: MeanElements,
        recovered: RecoveredElements,
        gravity: SecularGravityCoefficients,
        cosim: Double,
        sinim: Double,
        emsq: Double,
        s1: Double, s2: Double, s3: Double, s4: Double, s5: Double,
        ss1: Double, ss2: Double, ss3: Double, ss4: Double, ss5: Double,
        sz1: Double, sz3: Double, sz11: Double, sz13: Double,
        sz21: Double, sz23: Double, sz31: Double, sz33: Double,
        z1: Double, z3: Double, z11: Double, z13: Double,
        z21: Double, z23: Double, z31: Double, z33: Double,
    ): DeepSpaceResonance {
        val q22 = 1.7891679e-6
        val q31 = 2.1460748e-6
        val q33 = 2.2123015e-7
        val root22 = 1.7891679e-6
        val root44 = 7.3636953e-9
        val root54 = 2.1765803e-9
        val root32 = 3.7393792e-7
        val root52 = 1.1428639e-7
        val x2o3 = 2.0 / 3.0
        val znl = 1.5835218e-4
        val zns = 1.19459e-5

        val nm = recovered.meanMotionRadPerMin
        val inclm = elements.inclinationRad

        var irez = 0
        if (nm > 0.0034906585 && nm < 0.0052359877) irez = 1
        if (nm in 8.26e-3..9.24e-3 && elements.eccentricity >= 0.5) irez = 2

        // ------------------------ solar terms -------------------------
        val ses = ss1 * zns * ss5
        var sis = ss2 * zns * (sz11 + sz13)
        val sls = -zns * ss3 * (sz1 + sz3 - 14.0 - 6.0 * emsq)
        val sghs = ss4 * zns * (sz31 + sz33 - 6.0)
        var shs = -zns * ss2 * (sz21 + sz23)
        if (inclm < 5.2359877e-2 || inclm > kotlin.math.PI - 5.2359877e-2) shs = 0.0
        if (sinim != 0.0) shs /= sinim
        val sgs = sghs - cosim * shs

        // ------------------------- lunar terms -------------------------
        val dedt = ses + s1 * znl * s5
        val didt = sis + s2 * znl * (z11 + z13)
        val dmdt = sls - znl * s3 * (z1 + z3 - 14.0 - 6.0 * emsq)
        val sghl = s4 * znl * (z31 + z33 - 6.0)
        var shll = -znl * s2 * (z21 + z23)
        if (inclm < 5.2359877e-2 || inclm > kotlin.math.PI - 5.2359877e-2) shll = 0.0
        var domdt = sgs + sghl
        var dnodt = shs
        if (sinim != 0.0) {
            domdt -= cosim / sinim * shll
            dnodt += shll / sinim
        }

        if (irez == 0) {
            return DeepSpaceResonance(
                irez = 0,
                d2201 = 0.0, d2211 = 0.0, d3210 = 0.0, d3222 = 0.0,
                d4410 = 0.0, d4422 = 0.0, d5220 = 0.0, d5232 = 0.0,
                d5421 = 0.0, d5433 = 0.0,
                dedt = dedt, didt = didt, dmdt = dmdt, dnodt = dnodt, domdt = domdt,
                del1 = 0.0, del2 = 0.0, del3 = 0.0,
                xfact = 0.0, xlamo = 0.0,
            )
        }

        val gsto = recovered.greenwichSiderealTimeAtEpoch
        val theta = gsto.mod(TWO_PI) // tc = 0 at initialization
        val mo = elements.meanAnomalyRad
        val nodeo = elements.raanRad
        val argpo = elements.argPerigeeRad
        val mdot = gravity.meanAnomalyDot
        val nodedot = gravity.nodeDot
        val xpidot = gravity.argPerigeeDot + gravity.nodeDot
        val aonv = (nm / XKE).pow(x2o3)

        var d2201 = 0.0; var d2211 = 0.0; var d3210 = 0.0; var d3222 = 0.0
        var d4410 = 0.0; var d4422 = 0.0; var d5220 = 0.0; var d5232 = 0.0
        var d5421 = 0.0; var d5433 = 0.0
        var del1 = 0.0; var del2 = 0.0; var del3 = 0.0
        var xlamo = 0.0
        var xfact = 0.0

        if (irez == 2) {
            val cosisq = cosim * cosim
            val em = elements.eccentricity
            val eoc = em * emsq
            val g201 = -0.306 - (em - 0.64) * 0.440

            val g211: Double; val g310: Double; val g322: Double; val g410: Double; val g422: Double; val g520: Double
            if (em <= 0.65) {
                g211 = 3.616 - 13.2470 * em + 16.2900 * emsq
                g310 = -19.302 + 117.3900 * em - 228.4190 * emsq + 156.5910 * eoc
                g322 = -18.9068 + 109.7927 * em - 214.6334 * emsq + 146.5816 * eoc
                g410 = -41.122 + 242.6940 * em - 471.0940 * emsq + 313.9530 * eoc
                g422 = -146.407 + 841.8800 * em - 1629.014 * emsq + 1083.4350 * eoc
                g520 = -532.114 + 3017.977 * em - 5740.032 * emsq + 3708.2760 * eoc
            } else {
                g211 = -72.099 + 331.819 * em - 508.738 * emsq + 266.724 * eoc
                g310 = -346.844 + 1582.851 * em - 2415.925 * emsq + 1246.113 * eoc
                g322 = -342.585 + 1554.908 * em - 2366.899 * emsq + 1215.972 * eoc
                g410 = -1052.797 + 4758.686 * em - 7193.992 * emsq + 3651.957 * eoc
                g422 = -3581.690 + 16178.110 * em - 24462.770 * emsq + 12422.520 * eoc
                g520 = if (em > 0.715) {
                    -5149.66 + 29936.92 * em - 54087.36 * emsq + 31324.56 * eoc
                } else {
                    1464.74 - 4664.75 * em + 3763.64 * emsq
                }
            }

            val g533: Double; val g521: Double; val g532: Double
            if (em < 0.7) {
                g533 = -919.22770 + 4988.6100 * em - 9064.7700 * emsq + 5542.21 * eoc
                g521 = -822.71072 + 4568.6173 * em - 8491.4146 * emsq + 5337.524 * eoc
                g532 = -853.66600 + 4690.2500 * em - 8624.7700 * emsq + 5341.4 * eoc
            } else {
                g533 = -37995.780 + 161616.52 * em - 229838.20 * emsq + 109377.94 * eoc
                g521 = -51752.104 + 218913.95 * em - 309468.16 * emsq + 146349.42 * eoc
                g532 = -40023.880 + 170470.89 * em - 242699.48 * emsq + 115605.82 * eoc
            }

            val sini2 = sinim * sinim
            val f220 = 0.75 * (1.0 + 2.0 * cosim + cosisq)
            val f221 = 1.5 * sini2
            val f321 = 1.875 * sinim * (1.0 - 2.0 * cosim - 3.0 * cosisq)
            val f322 = -1.875 * sinim * (1.0 + 2.0 * cosim - 3.0 * cosisq)
            val f441 = 35.0 * sini2 * f220
            val f442 = 39.3750 * sini2 * sini2
            val f522 = 9.84375 * sinim * (sini2 * (1.0 - 2.0 * cosim - 5.0 * cosisq) +
                0.33333333 * (-2.0 + 4.0 * cosim + 6.0 * cosisq))
            val f523 = sinim * (4.92187512 * sini2 * (-2.0 - 4.0 * cosim + 10.0 * cosisq) +
                6.56250012 * (1.0 + 2.0 * cosim - 3.0 * cosisq))
            val f542 = 29.53125 * sinim * (2.0 - 8.0 * cosim + cosisq *
                (-12.0 + 8.0 * cosim + 10.0 * cosisq))
            val f543 = 29.53125 * sinim * (-2.0 - 8.0 * cosim + cosisq *
                (12.0 + 8.0 * cosim - 10.0 * cosisq))
            val xno2 = nm * nm
            val ainv2 = aonv * aonv
            var temp1 = 3.0 * xno2 * ainv2
            var temp = temp1 * root22
            d2201 = temp * f220 * g201
            d2211 = temp * f221 * g211
            temp1 *= aonv
            temp = temp1 * root32
            d3210 = temp * f321 * g310
            d3222 = temp * f322 * g322
            temp1 *= aonv
            temp = 2.0 * temp1 * root44
            d4410 = temp * f441 * g410
            d4422 = temp * f442 * g422
            temp1 *= aonv
            temp = temp1 * root52
            d5220 = temp * f522 * g520
            d5232 = temp * f523 * g532
            temp = 2.0 * temp1 * root54
            d5421 = temp * f542 * g521
            d5433 = temp * f543 * g533
            xlamo = (mo + nodeo + nodeo - theta - theta).mod(TWO_PI)
            xfact = mdot + dmdt + 2.0 * (nodedot + dnodt - RPTIM) - nm
        }

        if (irez == 1) {
            val g200 = 1.0 + emsq * (-2.5 + 0.8125 * emsq)
            val g310 = 1.0 + 2.0 * emsq
            val g300 = 1.0 + emsq * (-6.0 + 6.60937 * emsq)
            val f220 = 0.75 * (1.0 + cosim) * (1.0 + cosim)
            val f311 = 0.9375 * sinim * sinim * (1.0 + 3.0 * cosim) - 0.75 * (1.0 + cosim)
            var f330 = 1.0 + cosim
            f330 = 1.875 * f330 * f330 * f330
            del1 = 3.0 * nm * nm * aonv * aonv
            del2 = 2.0 * del1 * f220 * g200 * q22
            del3 = 3.0 * del1 * f330 * g300 * q33 * aonv
            del1 = del1 * f311 * g310 * q31 * aonv
            xlamo = (mo + nodeo + argpo - theta).mod(TWO_PI)
            xfact = mdot + xpidot - RPTIM + dmdt + domdt + dnodt - nm
        }

        return DeepSpaceResonance(
            irez = irez,
            d2201 = d2201, d2211 = d2211, d3210 = d3210, d3222 = d3222,
            d4410 = d4410, d4422 = d4422, d5220 = d5220, d5232 = d5232,
            d5421 = d5421, d5433 = d5433,
            dedt = dedt, didt = didt, dmdt = dmdt, dnodt = dnodt, domdt = domdt,
            del1 = del1, del2 = del2, del3 = del3,
            xfact = xfact, xlamo = xlamo,
        )
    }
}

/** The reference's rptim: Earth's rotation rate, rad/min. */
internal const val RPTIM = 4.37526908801129966e-3

/** Days since 1949-12-31 00h UT — the Vallado reference's "epoch" convention. */
internal fun Instant.toJulianDate1949Offset(): Double = toJulianDate() - 2415020.0
