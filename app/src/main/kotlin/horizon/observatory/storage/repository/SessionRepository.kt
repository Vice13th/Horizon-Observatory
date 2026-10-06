package horizon.observatory.storage.repository

import horizon.observatory.domain.model.RawObservation
import horizon.observatory.storage.entity.ObservationEntity
import horizon.observatory.storage.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    suspend fun createSession(): String
    suspend fun appendObservation(sessionId: String, observation: RawObservation)
    suspend fun beginStopping(sessionId: String)
    suspend fun closeSession(sessionId: String)
    suspend fun recoverUnclosedSessions(): List<SessionEntity>
    suspend fun markCrashClosed(sessionId: String)
    suspend fun updateSessionMetadata(
        sessionId: String,
        deviceMetadataJson: String,
        capabilityReportJson: String,
        permissionStateJson: String
    )
    suspend fun getSession(sessionId: String): SessionEntity?
    suspend fun getLatestSession(): SessionEntity?
    suspend fun getLatestCompletedSession(): SessionEntity?
    fun observeAllSessions(): Flow<List<SessionEntity>>
    fun observeSession(sessionId: String): Flow<SessionEntity?>
    fun getObservations(sessionId: String): Flow<List<ObservationEntity>>
    suspend fun getObservationsSnapshot(sessionId: String): List<ObservationEntity>
    suspend fun countObservations(sessionId: String): Int
    suspend fun minSequence(sessionId: String): Long?
    suspend fun maxSequence(sessionId: String): Long?
}
