package horizon.observatory.export

import horizon.observatory.analysis.AnalysisSnapshot
import horizon.observatory.astronomy.ObserverLocation
import horizon.observatory.astronomy.identity.GnssSatelliteId
import horizon.observatory.astronomy.prediction.GroundTrackResult
import horizon.observatory.astronomy.prediction.GroundTrackSampler
import horizon.observatory.astronomy.prediction.ObserverPosition
import horizon.observatory.astronomy.prediction.ObserverSource
import horizon.observatory.astronomy.prediction.PassSampleResult
import horizon.observatory.astronomy.prediction.PassSampler
import horizon.observatory.astronomy.prediction.SatellitePredictionUseCase

/** DERIVED_PREDICTED content for the separate "Predicted" report. [unavailableReason] explains an empty result. */
data class PredictedReportData(
    val tracks: GroundTrackResult?,
    val passes: PassSampleResult?,
    val unavailableReason: String?
)

/** Runs the same samplers as the on-screen panels. Never substitutes a position or an identity. */
suspend fun buildPredictedReportData(
    useCase: SatellitePredictionUseCase,
    analysis: AnalysisSnapshot,
    latDeg: Double?,
    lonDeg: Double?,
    altM: Double?,
    nowMs: Long
): PredictedReportData {
    if (latDeg == null || lonDeg == null || !latDeg.isFinite() || !lonDeg.isFinite()) {
        return PredictedReportData(null, null, "No receiver position (GNSS fix) available.")
    }
    val ids = analysis.gnssDomain.latestSatelliteEvidence.map { GnssSatelliteId(it.constellationType, it.svid) }.distinct()
    if (ids.isEmpty()) return PredictedReportData(null, null, "No observed satellites.")
    val observer = ObserverPosition(
        ObserverLocation(latDeg, lonDeg, (altM?.takeIf { it.isFinite() } ?: 0.0) / 1000.0),
        ObserverSource.RECEIVER_LOCATION_FIX, null
    )
    return try {
        PredictedReportData(
            GroundTrackSampler.sample(useCase, ids, observer, nowMs),
            PassSampler.nextPasses(useCase, ids, observer, nowMs),
            null
        )
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        PredictedReportData(null, null, "Prediction failed: ${e.javaClass.simpleName}: ${e.message}")
    }
}
