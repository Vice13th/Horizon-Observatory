package horizon.observatory.astronomy.prediction

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.cancellation.CancellationException

/**
 * State of the PREDICTED sky layer. Deliberately a different type from anything in the observed
 * layer (SatelliteEvidence / CorrelatedSkyPlot): the two layers are joined only by satellite id in
 * presentation code, never merged into one model. An observed satellite whose prediction state is
 * UNMATCHED / UNAVAILABLE is representable: "observed by the receiver, no verified catalog match".
 */
sealed interface PredictionLayerState {
    data object NotRun : PredictionLayerState
    data class Ready(val batch: PredictionBatch) : PredictionLayerState
    data class Failed(val reason: String) : PredictionLayerState
}

/** Holds the latest prediction batch. Refresh is driven by callers; nothing here polls or fetches. */
class PredictionLayerStateHolder(private val useCase: SatellitePredictionUseCase) {
    private val mutableState = MutableStateFlow<PredictionLayerState>(PredictionLayerState.NotRun)
    val state: StateFlow<PredictionLayerState> = mutableState.asStateFlow()

    suspend fun refresh(request: PredictionRequest) {
        mutableState.value = try {
            PredictionLayerState.Ready(useCase.predict(request))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PredictionLayerState.Failed("${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
