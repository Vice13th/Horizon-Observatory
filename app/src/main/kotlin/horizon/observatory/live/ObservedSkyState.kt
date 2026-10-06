package horizon.observatory.live

import horizon.observatory.astronomy.identity.GnssSatelliteId
import horizon.observatory.domain.gnss.SatelliteEvidence

/**
 * One satellite of the OBSERVED sky layer: azimuth/elevation exactly as reported by the receiver
 * (SatelliteEvidence), true-north referenced. Never contains predicted values.
 */
data class ObservedSkyPoint(
    val satelliteId: GnssSatelliteId,
    val azimuthDeg: Double,
    val elevationDeg: Double,
    val cn0DbHz: Double?,
    val usedInFix: Boolean?
)

/**
 * Typed observed-layer state for the sky plot. A satellite with missing or non-finite az/el is NOT
 * plotted and NOT given a placeholder position; it is counted in [excludedWithoutGeometry] so the
 * UI can say so. (MISSING DATA != ZERO DATA.)
 */
data class ObservedSkyState(
    val points: List<ObservedSkyPoint>,
    val excludedWithoutGeometry: Int
) {
    companion object {
        val EMPTY = ObservedSkyState(emptyList(), 0)

        fun from(evidence: List<SatelliteEvidence>): ObservedSkyState {
            val points = ArrayList<ObservedSkyPoint>()
            var excluded = 0
            for (e in evidence) {
                val az = e.azimuthDegrees
                val el = e.elevationDegrees
                if (az == null || el == null || !az.isFinite() || !el.isFinite()) {
                    excluded++
                    continue
                }
                points.add(
                    ObservedSkyPoint(
                        satelliteId = GnssSatelliteId(e.constellationType, e.svid),
                        azimuthDeg = az,
                        elevationDeg = el,
                        cn0DbHz = e.cn0DbHz,
                        usedInFix = e.usedInFix
                    )
                )
            }
            return ObservedSkyState(points, excluded)
        }
    }
}
