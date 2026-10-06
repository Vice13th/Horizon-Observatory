package horizon.observatory.live

import horizon.observatory.storage.entity.ObservationEntity
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.sample

/**
 * Live signal path for UI consumers.
 *
 * The Room evidence table remains authoritative. This bus is deliberately a lightweight
 * notification channel carrying the identity of a successfully persisted observation. UI
 * consumers use the signal to re-read the authoritative bounded snapshot; they never replace
 * raw evidence with bus state.
 */
data class LiveObservationSignal(
    val sessionId: String,
    val sequenceNumber: Long,
    val type: String,
    val ingestionMonotonicTimestampNs: Long?
)

class LiveObservationBus {
    private val signals = MutableSharedFlow<LiveObservationSignal>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val allSignals: Flow<LiveObservationSignal> = signals

    fun publish(sessionId: String, observation: ObservationEntity) {
        signals.tryEmit(
            LiveObservationSignal(
                sessionId = sessionId,
                sequenceNumber = observation.sequenceNumber,
                type = observation.type,
                ingestionMonotonicTimestampNs = observation.ingestionMonotonicTimestampNs
            )
        )
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    /**
     * Sample all persisted-observation notifications so the UI can recover even if the Room
     * session observer has not yet delivered the session-row invalidation.
     */
    fun sampledAll(periodMs: Long = 250L): Flow<LiveObservationSignal> = allSignals.sample(periodMs)

}
