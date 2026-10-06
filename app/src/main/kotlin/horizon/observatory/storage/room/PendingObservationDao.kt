package horizon.observatory.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import horizon.observatory.storage.entity.PendingObservationEntity

@Dao
interface PendingObservationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(observation: PendingObservationEntity): Long

    @Query("SELECT * FROM pending_observations WHERE sessionId = :sessionId ORDER BY sequenceNumber ASC LIMIT 1")
    suspend fun peek(sessionId: String): PendingObservationEntity?

    @Query("DELETE FROM pending_observations WHERE sessionId = :sessionId AND sequenceNumber = :sequenceNumber")
    suspend fun delete(sessionId: String, sequenceNumber: Long): Int

    @Query("SELECT COUNT(*) FROM pending_observations WHERE sessionId = :sessionId")
    suspend fun count(sessionId: String): Int
}
