package horizon.observatory.storage.room

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ObservationDaoRecentSnapshotInstrumentedTest {
    private lateinit var db: AppDatabase

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    @Test
    fun recentSnapshot_is_bounded_and_keeps_newest_sequences_descending() = runBlocking {
        val dao = db.observationDao()
        val sessionId = "test-session"
        db.sessionDao().insertSession(
            horizon.observatory.storage.entity.SessionEntity(
                sessionId = sessionId,
                startTimeMs = 1L,
                lifecycleState = "RECORDING",
                deviceManufacturer = "TEST",
                deviceModel = "TEST",
                androidVersion = "TEST",
                sdkVersion = 1,
                applicationVersion = "TEST"
            )
        )
        repeat(5) { index ->
            dao.insertObservation(
                horizon.observatory.storage.entity.ObservationEntity(
                    sessionId = sessionId,
                    sequenceNumber = (index + 1).toLong(),
                    timestampUtcMs = (index + 1).toLong(),
                    monotonicTimestampNs = null,
                    ingestionMonotonicTimestampNs = null,
                    source = "TEST",
                    technology = "TEST",
                    type = "TEST",
                    provider = "TEST",
                    rawPayloadJson = "{}",
                    normalizedPayloadJson = "{}",
                    capabilityState = "TEST",
                    evidenceStatus = "OBSERVED",
                    provenance = "TEST",
                    timestampDomain = "UNKNOWN",
                    timestampUncertaintyNs = null
                )
            )
        }
        assertEquals(listOf(5L, 4L, 3L), dao.getRecentObservationsSnapshot(sessionId, 3).map { it.sequenceNumber })
    }

    @Test
    fun repository_recentSnapshot_reverses_newest_rows_to_ascending_order() = runBlocking {
        val dao = db.observationDao()
        val sessionId = "repository-test-session"
        db.sessionDao().insertSession(
            horizon.observatory.storage.entity.SessionEntity(
                sessionId = sessionId,
                startTimeMs = 1L,
                lifecycleState = "RECORDING",
                deviceManufacturer = "TEST",
                deviceModel = "TEST",
                androidVersion = "TEST",
                sdkVersion = 1,
                applicationVersion = "TEST"
            )
        )
        repeat(5) { index ->
            dao.insertObservation(
                horizon.observatory.storage.entity.ObservationEntity(
                    sessionId = sessionId,
                    sequenceNumber = (index + 1).toLong(),
                    timestampUtcMs = (index + 1).toLong(),
                    monotonicTimestampNs = null,
                    ingestionMonotonicTimestampNs = null,
                    source = "TEST",
                    technology = "TEST",
                    type = "TEST",
                    provider = "TEST",
                    rawPayloadJson = "{}",
                    normalizedPayloadJson = "{}",
                    capabilityState = "TEST",
                    evidenceStatus = "OBSERVED",
                    provenance = "TEST",
                    timestampDomain = "UNKNOWN",
                    timestampUncertaintyNs = null
                )
            )
        }
        val repository = horizon.observatory.storage.repository.SessionRepositoryImpl(db.sessionDao(), dao)
        assertEquals(listOf(3L, 4L, 5L), repository.getRecentObservationsSnapshot(sessionId, 3).map { it.sequenceNumber })
    }
}
