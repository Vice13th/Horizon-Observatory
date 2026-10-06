package horizon.observatory.astronomy

import kotlin.math.*

object TopocentricCalculator {
    fun compute(observer: ObserverLocation, satEci: EciState): TopocentricState =
        compute(observer.toEcef(), observer.latRad, observer.lonRad, satEci)

    /** Batch path: computes observer ECEF once instead of per-satellite (AR use case: one
     *  observer, many satellites per frame). */
    fun computeBatch(observer: ObserverLocation, satEcis: List<EciState>): List<TopocentricState> {
        val obsEcef = observer.toEcef()
        return satEcis.map { compute(obsEcef, observer.latRad, observer.lonRad, it) }
    }

    private fun compute(obsEcef: EcefState, obsLatRad: Double, obsLonRad: Double, satEci: EciState): TopocentricState {
        val satEcef = CoordinateTransforms.eciToEcef(satEci)
        val (e, n, u) = CoordinateTransforms.ecefToEnu(obsEcef, satEcef, obsLatRad, obsLonRad)
        val range = sqrt(e * e + n * n + u * u)
        val elevation = asin((u / range).coerceIn(-1.0, 1.0))
        var azimuth = atan2(e, n)
        if (azimuth < 0) azimuth += 2 * PI
        val dx = satEcef.xKm - obsEcef.xKm
        val dy = satEcef.yKm - obsEcef.yKm
        val dz = satEcef.zKm - obsEcef.zKm
        val rangeRate = (dx * satEcef.vxKmS + dy * satEcef.vyKmS + dz * satEcef.vzKmS) / range
        return TopocentricState(Math.toDegrees(azimuth), Math.toDegrees(elevation), range, rangeRate)
    }
}
