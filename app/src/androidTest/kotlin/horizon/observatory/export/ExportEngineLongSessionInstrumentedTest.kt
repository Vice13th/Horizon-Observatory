package horizon.observatory.export

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import horizon.observatory.storage.entity.ObservationEntity
import horizon.observatory.storage.entity.SessionEntity
import horizon.observatory.storage.repository.SessionRepositoryImpl
import horizon.observatory.storage.room.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device-side export stress fixture. Rows are synthetic and only test persistence/export mechanics;
 * they are not GNSS measurements or field evidence.
 */
@RunWith(AndroidJUnit4::class)
class ExportEngineLongSessionInstrumentedTest {
    @Test
    fun exportsAndReadbackVerifies48915SyntheticRowsWithinDeviceHeap() = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sessionId = "export-stress-48915"
        var zipFile: File? = null
        try {
            db.sessionDao().insertSession(
                SessionEntity(
                    sessionId = sessionId,
                    startTimeMs = 1_797_000_000_000L,
                    lifecycleState = "RECORDING",
                    deviceManufacturer = "TEST",
                    deviceModel = "SYNTHETIC",
                    androidVersion = "16",
                    sdkVersion = 36,
                    applicationVersion = "export-stress",
                    schemaVersion = 7
                )
            )
            val padding = "x".repeat(360)
            db.withTransaction {
                val dao = db.observationDao()
                repeat(48_915) { index ->
                    val sequence = index + 1L
                    val payload = """{"index":$sequence,"payload":"$padding"}"""
                    val inserted = dao.insertObservation(
                        ObservationEntity(
                            sessionId = sessionId,
                            sequenceNumber = sequence,
                            timestampUtcMs = 1_797_000_000_000L + sequence * 1000L,
                            monotonicTimestampNs = sequence * 1_000_000_000L,
                            ingestionMonotonicTimestampNs = sequence * 1_000_000_000L,
                            timestampDomain = "ELAPSED_REALTIME",
                            source = "TEST",
                            technology = "TEST",
                            type = "SYNTHETIC_EXPORT_STRESS",
                            provider = "instrumentation",
                            rawPayloadJson = payload,
                            normalizedPayloadJson = "{}",
                            capabilityState = "TEST_FIXTURE",
                            evidenceStatus = "OBSERVED",
                            provenance = "SYNTHETIC_TEST_ONLY"
                        )
                    )
                    check(inserted != -1L) { "Duplicate synthetic sequence $sequence" }
                }
            }

            val repository = SessionRepositoryImpl(db.sessionDao(), db.observationDao())
            val result = ExportEngine(context, repository).exportSession(sessionId)
            when (result) {
                is ExportEngine.ExportResult.Success -> {
                    zipFile = result.zipFile
                    assertEquals(48_915, result.observationCount)
                    assertTrue(result.zipFile.isFile)
                    assertTrue(result.zipFile.length() > 0L)
                }
                is ExportEngine.ExportResult.Failed -> error("Long-session export failed: $result")
            }
        } finally {
            zipFile?.delete()
            File(context.cacheDir, "export_tmp_$sessionId").deleteRecursively()
            db.close()
        }
    }
}
