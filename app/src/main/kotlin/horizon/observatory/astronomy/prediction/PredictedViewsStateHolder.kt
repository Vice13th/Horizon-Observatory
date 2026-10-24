package horizon.observatory.astronomy.prediction

import horizon.observatory.astronomy.identity.GnssSatelliteId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PredictedViewState<out T> {
    data object NotRun : PredictedViewState<Nothing>
    data class Ready<out T>(val value: T, val computedAtUtcMs: Long) : PredictedViewState<T>
    data class Failed(val reason: String) : PredictedViewState<Nothing>
}

fun <T> PredictedViewState<T>?.readyValue(): T? = (this as? PredictedViewState.Ready<T>)?.value
fun PredictedViewState<*>?.failureReason(): String? = (this as? PredictedViewState.Failed)?.reason
fun PredictedViewState<*>?.computedAtUtcMs(): Long? = (this as? PredictedViewState.Ready<*>)?.computedAtUtcMs

/**
 * Holds the latest PREDICTED ground-track and pass results so they survive navigation and recomposition
 * (same pattern as [PredictionLayerStateHolder]: refresh is driven by callers, nothing here polls).
 * Composables only observe these flows; the Room/engine work runs inside the use case on its compute dispatcher.
 */
class PredictedViewsStateHolder(private val useCase: SatellitePredictionUseCase) {
    private val tracks = MutableStateFlow<PredictedViewState<GroundTrackResult>>(PredictedViewState.NotRun)
    private val passes = MutableStateFlow<PredictedViewState<PassSampleResult>>(PredictedViewState.NotRun)
    val groundTracks: StateFlow<PredictedViewState<GroundTrackResult>> = tracks.asStateFlow()
    val nextPasses: StateFlow<PredictedViewState<PassSampleResult>> = passes.asStateFlow()

    suspend fun refreshGroundTracks(ids: List<GnssSatelliteId>, observer: ObserverPosition, nowMs: Long) {
        tracks.value = try {
            PredictedViewState.Ready(GroundTrackSampler.sample(useCase, ids, observer, nowMs), nowMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PredictedViewState.Failed("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    suspend fun refreshPasses(ids: List<GnssSatelliteId>, observer: ObserverPosition, nowMs: Long) {
        passes.value = try {
            PredictedViewState.Ready(PassSampler.nextPasses(useCase, ids, observer, nowMs), nowMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PredictedViewState.Failed("${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
