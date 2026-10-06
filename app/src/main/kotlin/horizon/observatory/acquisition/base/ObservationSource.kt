package horizon.observatory.acquisition.base

import kotlinx.coroutines.flow.Flow
import horizon.observatory.domain.model.RawObservation
import horizon.observatory.domain.model.SourceStatus

interface ObservationSource {
    val status: SourceStatus
    val observationFlow: Flow<RawObservation>
    fun start()
    fun stop()
}
