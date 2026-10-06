package horizon.observatory.astronomy.frames

import horizon.observatory.astronomy.CoordinateTransforms
import horizon.observatory.astronomy.EciState
import horizon.observatory.astronomy.EcefState
import horizon.observatory.astronomy.ObserverLocation
import horizon.observatory.astronomy.propagation.TemeState
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/** Observer-relative east/north/up vector, km. Local tangent frame at the observer (WGS-84 geodetic). */
data class EnuVectorKm(val eastKm: Double, val northKm: Double, val upKm: Double)

/** Azimuth clockwise from geodetic north, degrees in [0, 360). Elevation above local horizon, degrees. */
data class ObserverRelativeAngles(val azimuthDeg: Double, val elevationDeg: Double, val rangeKm: Double)

/**
 * Explicit reference-frame chain for predicted satellite geometry. Three separate stages so each can
 * be tested and audited:
 *
 *   1. [temeToEcef]        TEME (km, km/s)  -> ECEF (km, km/s)
 *   2. [ecefToEnu]         ECEF             -> observer ENU (km)
 *   3. [enuToAzElRange]    ENU              -> azimuth / elevation (deg) / range (km)
 *
 * Documented approximations (acceptable for sky-plot display, NOT for precision geodesy):
 *  - Stage 1 rotates about Z by GMST only (IAU-1982 polynomial via CoordinateTransforms.gmstRad).
 *    Polar motion and the equation of the equinoxes are ignored. UTC is used as UT1
 *    (|UT1-UTC| < 0.9 s, up to ~0.004 degrees of Earth rotation).
 *  - Observer height is WGS-84 ellipsoidal (Android Location altitude convention), in km.
 *  - No refraction, no light-time or aberration correction.
 */
object PredictionFramePipeline {

    fun temeToEcef(teme: TemeState): EcefState =
        CoordinateTransforms.eciToEcef(
            EciState(teme.xKm, teme.yKm, teme.zKm, teme.vxKmS, teme.vyKmS, teme.vzKmS, teme.atTime.utcMillis)
        )

    fun ecefToEnu(observer: ObserverLocation, satelliteEcef: EcefState): EnuVectorKm {
        val enu = CoordinateTransforms.ecefToEnu(observer.toEcef(), satelliteEcef, observer.latRad, observer.lonRad)
        return EnuVectorKm(enu[0], enu[1], enu[2])
    }

    fun enuToAzElRange(enu: EnuVectorKm): ObserverRelativeAngles {
        val range = sqrt(enu.eastKm * enu.eastKm + enu.northKm * enu.northKm + enu.upKm * enu.upKm)
        require(range > 0.0) { "zero range: elevation and azimuth are undefined" }
        val elevationRad = asin((enu.upKm / range).coerceIn(-1.0, 1.0))
        var azimuthRad = atan2(enu.eastKm, enu.northKm)
        if (azimuthRad < 0.0) azimuthRad += 2.0 * PI
        return ObserverRelativeAngles(Math.toDegrees(azimuthRad), Math.toDegrees(elevationRad), range)
    }
}
