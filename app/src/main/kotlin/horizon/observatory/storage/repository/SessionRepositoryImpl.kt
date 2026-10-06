package horizon.observatory.storage.repository

import android.os.Build
import horizon.observatory.BuildConfig
import horizon.observatory.core.time.TimestampEngine
import horizon.observatory.domain.model.IllegalSessionTransitionException
import horizon.observatory.domain.model.SessionLifecycleState
import horizon.observatory.domain.model.SessionStateMachine
import horizon.observatory.domain.model.RawObservation
import horizon.observatory.storage.entity.ObservationEntity
import horizon.observatory.storage.entity.SessionEntity
import horizon.observatory.storage.room.ObservationDao
import horizon.observatory.storage.room.SessionDao
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class SessionRepositoryImpl(
    private val sessionDao: SessionDao,
    private val observationDao: ObservationDao
) : SessionRepository {
    override suspend fun createSession(): String {
        val sessionId = UUID.randomUUID().toString()
        val session = SessionEntity(
            sessionId = sessionId,
            startTimeMs = TimestampEngine.currentUtcMillis(),
            lifecycleState = SessionLifecycleState.READY.name,
            deviceManufacturer = Build.MANUFACTURER.ifBlank { "UNKNOWN" },
            deviceModel = Build.MODEL.ifBlank { "UNKNOWN" },
            androidVersion = Build.VERSION.RELEASE ?: "UNKNOWN",
            sdkVersion = Build.VERSION.SDK_INT,
            applicationVersion = BuildConfig.VERSION_NAME
        )
        sessionDao.insertSession(session)
        transition(sessionId, SessionLifecycleState.READY, SessionLifecycleState.RECORDING)
        return sessionId
    }

    override suspend fun appendObservation(sessionId: String, observation: RawObservation) {
        val session = sessionDao.getSession(sessionId) ?: error("Unknown session: $sessionId")
        require(session.lifecycleState == SessionLifecycleState.RECORDING.name) {
            "Cannot append observation to ${session.lifecycleState} session $sessionId"
        }
        val inserted = observationDao.insertObservation(observation.toObservationEntity(sessionId))
        check(inserted != -1L) {
            "Duplicate observation sequence session=$sessionId seq=${observation.sequenceNumber}"
        }
    }

    override suspend fun beginStopping(sessionId: String) {
        transition(sessionId, SessionLifecycleState.RECORDING, SessionLifecycleState.STOPPING)
    }

    override suspend fun closeSession(sessionId: String) {
        val current = sessionDao.getSession(sessionId)?.lifecycleState
            ?.let(SessionLifecycleState::valueOf)
            ?: error("Unknown session: $sessionId")
        SessionStateMachine.requireTransition(current, SessionLifecycleState.COMPLETED)
        check(sessionDao.closeFromStopping(sessionId, TimestampEngine.currentUtcMillis()) == 1) {
            "Session close compare-and-set failed: $sessionId"
        }
    }

    override suspend fun recoverUnclosedSessions(): List<SessionEntity> =
        sessionDao.getUnclosedSessions()

    override suspend fun markCrashClosed(sessionId: String) {
        sessionDao.markSessionCrashClosed(sessionId, TimestampEngine.currentUtcMillis())
    }

    override suspend fun updateSessionMetadata(
        sessionId: String,
        deviceMetadataJson: String,
        capabilityReportJson: String,
        permissionStateJson: String
    ) {
        check(sessionDao.updateMetadata(sessionId, deviceMetadataJson, capabilityReportJson, permissionStateJson) == 1) {
            "Session metadata update failed: $sessionId"
        }
    }

    override suspend fun getSession(sessionId: String): SessionEntity? = sessionDao.getSession(sessionId)
    override suspend fun getLatestSession(): SessionEntity? = sessionDao.latestSession()
    override suspend fun getLatestCompletedSession(): SessionEntity? = sessionDao.latestCompletedSession()
    override fun observeAllSessions(): Flow<List<SessionEntity>> = sessionDao.observeAllSessions()
    override fun observeSession(sessionId: String): Flow<SessionEntity?> = sessionDao.observeSession(sessionId)
    override fun getObservations(sessionId: String): Flow<List<ObservationEntity>> = observationDao.getObservationsOrderedForSession(sessionId)
    override suspend fun getObservationsSnapshot(sessionId: String): List<ObservationEntity> = observationDao.getObservationsSnapshot(sessionId)
    override suspend fun getRecentObservationsSnapshot(sessionId: String, limit: Int): List<ObservationEntity> =
        observationDao.getRecentObservationsSnapshot(sessionId, limit).asReversed()
    override suspend fun countObservations(sessionId: String): Int = observationDao.countForSession(sessionId)
    override suspend fun minSequence(sessionId: String): Long? = observationDao.minSequence(sessionId)
    override suspend fun maxSequence(sessionId: String): Long? = observationDao.maxSequence(sessionId)

    private suspend fun transition(sessionId: String, from: SessionLifecycleState, to: SessionLifecycleState) {
        SessionStateMachine.requireTransition(from, to)
        check(sessionDao.compareAndSetLifecycleState(sessionId, from.name, to.name) == 1) {
            val current = sessionDao.getSession(sessionId)?.lifecycleState ?: "UNKNOWN"
            "Illegal or concurrent session transition: $sessionId $current -> $to"
        }
    }
}
