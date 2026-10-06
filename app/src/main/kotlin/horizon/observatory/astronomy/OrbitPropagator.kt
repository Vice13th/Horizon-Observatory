package horizon.observatory.astronomy

import kotlin.math.*

/**
 * Two-body (Keplerian) orbit propagator. NOT SGP4/SDP4.
 *
 * LIMITATION (explicit, not hidden per rule 14): real satellite catalogs (TLE/OMM) are mean
 * elements defined for use with SGP4, which models atmospheric drag, J2-J4 Earth oblateness,
 * solar/lunar perturbations, etc. This propagator does none of that -- it is plain two-body
 * Kepler propagation. Position error vs. real SGP4/observed ephemeris will grow over time
 * (worse for low-altitude/high-drag objects) and is NOT bounded here. This exists so Phase 1's
 * coordinate-transform pipeline (ECI->ECEF->ENU->az/el) can be built and tested deterministically
 * without depending on an unverified from-scratch SGP4 implementation. Replacing this with a
 * real SGP4 implementation is a distinct, separately-verifiable future task, not silently
 * substituted here.
 *
 * STATUS (orbit-prediction architecture checkpoint): NON-AUTHORITATIVE analytical fallback. It is
 * NOT an OrbitPropagationEngine, is not wired into HorizonContainer or SatellitePredictionUseCase,
 * and must never produce a value labelled as a prediction. It remains only for the deterministic
 * coordinate-chain tests (AstronomyMathTest).
 */
object OrbitPropagator {
    private const val GM_EARTH_KM3_S2 = 398600.4418

    fun propagateToEci(elements: OrbitalElements, atUtcMillis: Long): EciState {
        val a = elements.semiMajorAxisKm
        val e = elements.eccentricity
        val i = elements.inclinationRad
        val raan = elements.raanRad
        val argPe = elements.argOfPerigeeRad

        val dtSeconds = (atUtcMillis - elements.epochUtcMillis) / 1000.0
        val n = sqrt(GM_EARTH_KM3_S2 / (a * a * a)) // mean motion, rad/s
        val m = normalizeAngle(elements.meanAnomalyRad + n * dtSeconds)

        val eccAnomaly = solveKepler(m, e)
        val trueAnomaly = 2.0 * atan2(sqrt(1 + e) * sin(eccAnomaly / 2), sqrt(1 - e) * cos(eccAnomaly / 2))
        val r = a * (1 - e * cos(eccAnomaly))
        val p = a * (1 - e * e)
        val h = sqrt(GM_EARTH_KM3_S2 * p)

        // Perifocal frame (PQW)
        val xPf = r * cos(trueAnomaly)
        val yPf = r * sin(trueAnomaly)
        val vxPf = -GM_EARTH_KM3_S2 / h * sin(trueAnomaly)
        val vyPf = GM_EARTH_KM3_S2 / h * (e + cos(trueAnomaly))

        // PQW -> ECI via R3(-raan) * R1(-i) * R3(-argPe), applied as sequential rotations,
        // kept explicit/inspectable rather than a single fused matrix to reduce the risk of an
        // unverifiable sign/order error.
        // Step 1: rotate by argument of perigee about Z (perifocal -> ascending-node-aligned)
        var x = xPf * cos(argPe) - yPf * sin(argPe)
        var y = xPf * sin(argPe) + yPf * cos(argPe)
        var z = 0.0
        var vx = vxPf * cos(argPe) - vyPf * sin(argPe)
        var vy = vxPf * sin(argPe) + vyPf * cos(argPe)
        var vz = 0.0
        // Step 2: rotate by inclination about X
        run {
            val y2 = y * cos(i) - z * sin(i)
            val z2 = y * sin(i) + z * cos(i)
            y = y2; z = z2
            val vy2 = vy * cos(i) - vz * sin(i)
            val vz2 = vy * sin(i) + vz * cos(i)
            vy = vy2; vz = vz2
        }
        // Step 3: rotate by RAAN about Z
        run {
            val x3 = x * cos(raan) - y * sin(raan)
            val y3 = x * sin(raan) + y * cos(raan)
            x = x3; y = y3
            val vx3 = vx * cos(raan) - vy * sin(raan)
            val vy3 = vx * sin(raan) + vy * cos(raan)
            vx = vx3; vy = vy3
        }

        return EciState(x, y, z, vx, vy, vz, atUtcMillis)
    }

    private fun solveKepler(m: Double, e: Double, tolerance: Double = 1e-10, maxIterations: Int = 50): Double {
        var eAnom = if (e < 0.8) m else PI
        repeat(maxIterations) {
            val f = eAnom - e * sin(eAnom) - m
            val fPrime = 1 - e * cos(eAnom)
            val delta = f / fPrime
            eAnom -= delta
            if (abs(delta) < tolerance) return eAnom
        }
        return eAnom
    }

    private fun normalizeAngle(a: Double): Double {
        var r = a % (2 * PI)
        if (r < 0) r += 2 * PI
        return r
    }
}
