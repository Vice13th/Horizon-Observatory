package horizon.observatory.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import horizon.observatory.storage.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: SessionEntity)

    @Query("UPDATE sessions SET lifecycleState = :toState WHERE sessionId = :sessionId AND lifecycleState = :fromState")
    suspend fun compareAndSetLifecycleState(sessionId: String, fromState: String, toState: String): Int

    @Query("UPDATE sessions SET endTimeMs = :endTimeMs, lifecycleState = 'COMPLETED' WHERE sessionId = :sessionId AND lifecycleState = 'STOPPING'")
    suspend fun closeFromStopping(sessionId: String, endTimeMs: Long): Int

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId")
    fun observeSession(sessionId: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions ORDER BY startTimeMs DESC")
    fun observeAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY startTimeMs DESC LIMIT 1")
    suspend fun latestSession(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE lifecycleState = 'COMPLETED' ORDER BY endTimeMs DESC LIMIT 1")
    suspend fun latestCompletedSession(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE lifecycleState IN ('READY', 'RECORDING', 'STOPPING') ORDER BY startTimeMs ASC")
    suspend fun getUnclosedSessions(): List<SessionEntity>

    @Query("UPDATE sessions SET lifecycleState = 'CRASH_CLOSED', endTimeMs = :endTimeMs WHERE sessionId = :sessionId AND lifecycleState IN ('READY', 'RECORDING', 'STOPPING')")
    suspend fun markSessionCrashClosed(sessionId: String, endTimeMs: Long): Int

    @Query("UPDATE sessions SET deviceMetadataJson = :deviceMetadataJson, capabilityReportJson = :capabilityReportJson, permissionStateJson = :permissionStateJson WHERE sessionId = :sessionId")
    suspend fun updateMetadata(
        sessionId: String,
        deviceMetadataJson: String,
        capabilityReportJson: String,
        permissionStateJson: String
    ): Int
}
