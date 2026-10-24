package horizon.observatory.astronomy.prediction

import horizon.observatory.astronomy.identity.GnssSatelliteId
import horizon.observatory.astronomy.time.PropagationTime
import horizon.observatory.astronomy.time.TimeBasis

data class SatellitePasses(val id: GnssSatelliteId, val passes: List<PredictedPass>, val stale: Boolean)
data class PassSampleResult(val satellites: List<SatellitePasses>, val requested: Int, val maskDeg: Double)

/** DERIVED_PREDICTED pass list for satellites the receiver observed, via the shared prediction use case. */
object PassSampler {
    suspend fun nextPasses(
        useCase: SatellitePredictionUseCase,
        ids: List<GnssSatelliteId>,
        observer: ObserverPosition,
        nowMs: Long,
        windowHours: Int = 12,
        maskDeg: Double = 10.0,
        maxSatellites: Int = 12,
        maxPassesPerSatellite: Int = 3
    ): PassSampleResult {
        val wanted = ids.distinct().take(maxSatellites)
        val out = ArrayList<SatellitePasses>()
        for (id in wanted) {
            var stale = false
            val passes = PassFinder.find(nowMs, nowMs + windowHours * 3_600_000L, maskDeg) { t ->
                val s = useCase.predict(PredictionRequest(listOf(id), observer, PropagationTime(t, TimeBasis.DEVICE_CLOCK_UTC)))
                    .states.firstOrNull()
                if (s?.displayState == PredictionDisplayState.STALE) stale = true
                s?.geometry?.let { AzEl(it.predictedAzimuthDeg, it.predictedElevationDeg) }
            }
            if (passes.isNotEmpty()) out.add(SatellitePasses(id, passes.take(maxPassesPerSatellite), stale))
        }
        return PassSampleResult(out, wanted.size, maskDeg)
    }
}
