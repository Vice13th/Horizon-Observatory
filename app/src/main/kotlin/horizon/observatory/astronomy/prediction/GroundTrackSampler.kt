package horizon.observatory.astronomy.prediction

import horizon.observatory.astronomy.geo.Geodetic
import horizon.observatory.astronomy.identity.GnssSatelliteId
import horizon.observatory.astronomy.time.PropagationTime
import horizon.observatory.astronomy.time.TimeBasis

data class TrackPoint(val offsetMin: Int, val latDeg: Double, val lonDeg: Double, val altKm: Double)
data class SatelliteGroundTrack(val id: GnssSatelliteId, val points: List<TrackPoint>)
data class GroundTrackResult(
    val tracks: List<SatelliteGroundTrack>,
    /** Prediction status of every requested satellite at offset 0 (why a satellite has no track). */
    val statusNow: Map<PredictionStatus, Int>,
    val requested: Int
)

/**
 * DERIVED_PREDICTED only. Sub-satellite points = geodetic(ECEF) of the SGP4/SDP4 prediction at
 * several offsets around "now". Satellites without a verified identity/catalog/engine yield no
 * track; nothing is guessed. Runs through the same use case (and its Dispatchers.Default) as the
 * az/el prediction layer.
 */
object GroundTrackSampler {
    const val MAX_SATELLITES = 12
    val DEFAULT_OFFSETS_MIN: List<Int> = (-20..60 step 5).toList()

    suspend fun sample(
        useCase: SatellitePredictionUseCase,
        ids: List<GnssSatelliteId>,
        observer: ObserverPosition,
        centerUtcMs: Long,
        offsetsMin: List<Int> = DEFAULT_OFFSETS_MIN
    ): GroundTrackResult {
        val wanted = ids.distinct().take(MAX_SATELLITES)
        val acc = LinkedHashMap<GnssSatelliteId, MutableList<TrackPoint>>()
        var statusNow: Map<PredictionStatus, Int> = emptyMap()
        for (off in offsetsMin) {
            val time = PropagationTime(centerUtcMs + off * 60_000L, TimeBasis.DEVICE_CLOCK_UTC)
            val batch = useCase.predict(PredictionRequest(wanted, observer, time))
            if (off == 0) statusNow = batch.states.groupingBy { it.status }.eachCount()
            for (s in batch.states) {
                val g = s.geometry ?: continue
                val geo = Geodetic.fromEcef(g.ecef.xKm, g.ecef.yKm, g.ecef.zKm) ?: continue
                acc.getOrPut(s.satelliteId) { mutableListOf() }.add(TrackPoint(off, geo.latDeg, geo.lonDeg, geo.altKm))
            }
        }
        return GroundTrackResult(acc.map { SatelliteGroundTrack(it.key, it.value) }, statusNow, wanted.size)
    }
}
