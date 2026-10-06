package horizon.observatory.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import horizon.observatory.storage.entity.ObservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertObservation(observation: ObservationEntity): Long

    @Query("SELECT * FROM observations WHERE sessionId = :sessionId ORDER BY sequenceNumber ASC")
    fun getObservationsOrderedForSession(sessionId: String): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM observations WHERE sessionId = :sessionId ORDER BY sequenceNumber ASC")
    suspend fun getObservationsSnapshot(sessionId: String): List<ObservationEntity>

    @Query("SELECT * FROM observations WHERE sessionId = :sessionId ORDER BY sequenceNumber DESC LIMIT :limit")
    suspend fun getRecentObservationsSnapshot(sessionId: String, limit: Int): List<ObservationEntity>

    @Query("SELECT COUNT(*) FROM observations WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: String): Int

    @Query("SELECT MIN(sequenceNumber) FROM observations WHERE sessionId = :sessionId")
    suspend fun minSequence(sessionId: String): Long?

    @Query("SELECT MAX(sequenceNumber) FROM observations WHERE sessionId = :sessionId")
    suspend fun maxSequence(sessionId: String): Long?

    @Query("SELECT COUNT(*) FROM observations WHERE sessionId = :sessionId AND sequenceNumber = :sequenceNumber")
    suspend fun countBySessionSequence(sessionId: String, sequenceNumber: Long): Int

    @Query("SELECT COUNT(*) FROM observations WHERE sessionId = :sessionId AND evidenceStatus = :status")
    suspend fun countByEvidenceStatus(sessionId: String, status: String): Int
}
