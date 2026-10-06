package horizon.observatory.storage.queue

import androidx.room.withTransaction
import horizon.observatory.domain.model.RawObservation
import horizon.observatory.storage.repository.toObservationEntity
import horizon.observatory.storage.repository.toPendingEntity
import horizon.observatory.storage.room.AppDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ObservationPersistenceQueue {
    suspend fun enqueue(sessionId: String, observation: RawObservation)
    suspend fun drain(sessionId: String): Int
    suspend fun pendingCount(sessionId: String): Int
}

/**
 * Room-backed handoff queue. Enqueue is durable before the producer can report success.
 * Drain moves one item to the immutable evidence table and deletes the queue row in the same
 * Room transaction, so a process death can only leave the queue row present or the evidence row
 * present -- never an acknowledged observation silently lost between the two.
 */
class RoomObservationPersistenceQueue(
    private val database: AppDatabase
) : ObservationPersistenceQueue {
    private val drainMutex = Mutex()

    override suspend fun enqueue(sessionId: String, observation: RawObservation) {
        val inserted = database.pendingObservationDao().enqueue(observation.toPendingEntity(sessionId))
        check(inserted != -1L) {
            "Duplicate pending observation sequence: session=$sessionId sequence=${observation.sequenceNumber}"
        }
    }

    override suspend fun drain(sessionId: String): Int = drainMutex.withLock {
        var drained = 0
        while (true) {
            val didDrain = database.withTransaction {
                val pending = database.pendingObservationDao().peek(sessionId)
                    ?: return@withTransaction false

                val inserted = database.observationDao().insertObservation(pending.toObservationEntity())
                if (inserted == -1L) {
                    check(database.observationDao().countBySessionSequence(sessionId, pending.sequenceNumber) == 1) {
                        "Pending observation collided with missing evidence row: $sessionId/${pending.sequenceNumber}"
                    }
                }
                val deleted = database.pendingObservationDao().delete(sessionId, pending.sequenceNumber)
                check(deleted == 1) { "Pending observation delete failed: $sessionId/${pending.sequenceNumber}" }
                true
            }
            if (!didDrain) break
            drained++
        }
        drained
    }

    override suspend fun pendingCount(sessionId: String): Int =
        database.pendingObservationDao().count(sessionId)
}
