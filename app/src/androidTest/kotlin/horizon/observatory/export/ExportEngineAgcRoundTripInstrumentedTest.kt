package horizon.observatory.export

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import horizon.observatory.storage.entity.ObservationEntity
import horizon.observatory.storage.entity.SessionEntity
import horizon.observatory.storage.repository.SessionRepositoryImpl
import horizon.observatory.storage.room.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/** Verifies event-level AGC payload fidelity through Room persistence and the export archive. */
@RunWith(AndroidJUnit4::class)
class ExportEngineAgcRoundTripInstrumentedTest {
    @Test
    fun eventLevelAgcSurvivesRoomJsonAndSqliteExportRoundTrip() = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sessionId = "agc-round-trip-${UUID.randomUUID()}"
        val payload = JSONObject()
            .put(
                "eventExtras",
                JSONObject().put(
                    "agcEvents",
                    JSONArray().put(
                        JSONObject()
                            .put("levelDb", -4.5)
                            .put("constellationType", 1)
                            .put("carrierFrequencyHz", 1_575_420_000.0)
                    )
                )
            )
            .toString()
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
                    applicationVersion = "agc-round-trip-test",
                    schemaVersion = 7
                )
            )
            val inserted = db.observationDao().insertObservation(
                ObservationEntity(
                    sessionId = sessionId,
                    sequenceNumber = 1L,
                    timestampUtcMs = 1_797_000_001_000L,
                    monotonicTimestampNs = 1_000_000_000L,
                    ingestionMonotonicTimestampNs = 1_000_000_100L,
                    timestampDomain = "ELAPSED_REALTIME",
                    source = "TEST",
                    technology = "GNSS",
                    type = "GNSS_CLOCK",
                    provider = "GnssClockCapture",
                    rawPayloadJson = payload,
                    normalizedPayloadJson = "{}",
                    capabilityState = "TEST_FIXTURE",
                    evidenceStatus = "OBSERVED",
                    provenance = "SYNTHETIC_TEST_ONLY"
                )
            )
            assertTrue("Room insert should succeed", inserted != -1L)

            val repository = SessionRepositoryImpl(db.sessionDao(), db.observationDao())
            val result = ExportEngine(context, repository).exportSession(sessionId)
            when (result) {
                is ExportEngine.ExportResult.Success -> {
                    zipFile = result.zipFile
                    assertEquals(1, result.observationCount)
                    ZipFile(result.zipFile).use { archive ->
                        val observations = archive.getInputStream(archive.getEntry("observations.json"))
                            .bufferedReader()
                            .use { JSONArray(it.readText()) }
                        assertEquals(1, observations.length())
                        val exportedPayload = observations.getJSONObject(0).getJSONObject("rawPayloadJson")
                        val exportedAgc = exportedPayload
                            .getJSONObject("eventExtras")
                            .getJSONArray("agcEvents")
                            .getJSONObject(0)
                        assertEquals(-4.5, exportedAgc.getDouble("levelDb"), 0.0)
                        assertEquals(1, exportedAgc.getInt("constellationType"))
                        assertEquals(1_575_420_000.0, exportedAgc.getDouble("carrierFrequencyHz"), 0.0)

                        val sqlite = File(context.cacheDir, "agc-round-trip-${UUID.randomUUID()}.sqlite")
                        try {
                            archive.getInputStream(archive.getEntry("horizon_session.sqlite")).use { input ->
                                sqlite.outputStream().use { output -> input.copyTo(output) }
                            }
                            SQLiteDatabase.openDatabase(
                                sqlite.path,
                                null,
                                SQLiteDatabase.OPEN_READONLY
                            ).use { exportedDb ->
                                exportedDb.rawQuery(
                                    "SELECT rawPayloadJson FROM observations WHERE sessionId = ? AND sequenceNumber = ?",
                                    arrayOf(sessionId, "1")
                                ).use { cursor ->
                                    assertTrue("Exported SQLite observation should exist", cursor.moveToFirst())
                                    val sqlitePayload = JSONObject(cursor.getString(0))
                                    val sqliteAgc = sqlitePayload
                                        .getJSONObject("eventExtras")
                                        .getJSONArray("agcEvents")
                                        .getJSONObject(0)
                                    assertEquals(-4.5, sqliteAgc.getDouble("levelDb"), 0.0)
                                    assertEquals(1, sqliteAgc.getInt("constellationType"))
                                    assertEquals(1_575_420_000.0, sqliteAgc.getDouble("carrierFrequencyHz"), 0.0)
                                }
                            }
                        } finally {
                            sqlite.delete()
                        }
                    }
                }
                is ExportEngine.ExportResult.Failed -> error("AGC round-trip export failed: $result")
            }
        } finally {
            zipFile?.delete()
            db.close()
        }
    }
}
