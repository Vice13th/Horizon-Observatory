package horizon.observatory.export

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import horizon.observatory.domain.model.IncrementalIntegrityAudit
import horizon.observatory.domain.model.RawObservationView
import horizon.observatory.analysis.gnss.GnssCorrelationEngine
import horizon.observatory.export.model.DatasetManifest
import horizon.observatory.storage.entity.ObservationEntity
import horizon.observatory.storage.entity.SessionEntity
import horizon.observatory.storage.repository.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Exports the real persisted session without synthesizing observations.
 * The archive contains measurements, the session event/error log, capability/permission
 * snapshots, integrity evidence, and a self-verification receipt.
 */
class ExportEngine(
    private val context: Context,
    private val repository: SessionRepository
) {
    sealed class ExportResult {
        data class Success(val zipFile: File, val observationCount: Int) : ExportResult()
        data class Failed(val stage: String, val reason: String) : ExportResult()
    }

    suspend fun exportSession(sessionId: String): ExportResult = withContext(Dispatchers.IO) {
        val session = repository.getSession(sessionId)
            ?: return@withContext ExportResult.Failed("lookup", "No session found for id=$sessionId")

        val safeSessionToken = safeFileToken(sessionId)
        val tempDir = File(context.cacheDir, "export_tmp_" + safeSessionToken + "_" + System.nanoTime())
        var temporaryZip: File? = null

        try {
            check(tempDir.mkdirs()) { "Unable to create export temp directory: $tempDir" }
            val roomCount = repository.countObservations(sessionId)
            val auditBuilder = IncrementalIntegrityAudit()
            val gnssObservations = ArrayList<ObservationEntity>()
            var preflightCount = 0
            var systemEventCount = 0
            var errorEventCount = 0
            var gnssPayloadChars = 0L
            var gnssStatusSatelliteCount = 0
            var afterSequence = Long.MIN_VALUE

            // Pass 1: ordered keyset pages. Only scalar audit state and the bounded GNSS
            // correlation subset are retained; the session-wide observation list is never built.
            while (true) {
                currentCoroutineContext().ensureActive()
                val page = repository.getObservationPage(sessionId, afterSequence, PAGE_SIZE)
                if (page.isEmpty()) break
                for (row in page) {
                    preflightCount += 1
                    auditBuilder.append(
                        RawObservationView(
                            sequenceNumber = row.sequenceNumber,
                            sourceMonotonicTimestampNs = row.monotonicTimestampNs,
                            ingestionMonotonicTimestampNs = row.ingestionMonotonicTimestampNs,
                            provider = row.provider
                        )
                    )
                    when (row.type) {
                        "SYSTEM_EVENT" -> systemEventCount += 1
                        "ERROR_EVENT" -> errorEventCount += 1
                    }

                    if (row.type in GNSS_CORRELATION_TYPES) {
                        // Correlation reads rawPayloadJson only. Drop the duplicated normalized JSON
                        // from this bounded working set to keep large-session export below the heap ceiling.
                        gnssObservations += row.copy(normalizedPayloadJson = "{}")
                        gnssPayloadChars += row.rawPayloadJson.length.toLong()
                        if (gnssObservations.size > MAX_GNSS_CORRELATION_SOURCE_ROWS ||
                            gnssPayloadChars > MAX_GNSS_CORRELATION_PAYLOAD_CHARS
                        ) {
                            return@withContext ExportResult.Failed(
                                "GNSS correlation safety limit",
                                "Correlation input exceeded its explicit row/payload budget; no observations were truncated or exported."
                            )
                        }
                        if (row.type == "GNSS_STATUS") {
                            val satellites = runCatching {
                                JSONObject(row.rawPayloadJson).optJSONArray("satellites")?.length() ?: 0
                            }.getOrDefault(0)
                            gnssStatusSatelliteCount += satellites
                            if (satellites > MAX_SATELLITES_PER_STATUS_ROW ||
                                gnssStatusSatelliteCount > MAX_GNSS_STATUS_SATELLITES
                            ) {
                                return@withContext ExportResult.Failed(
                                    "GNSS correlation safety limit",
                                    "GNSS status satellite-row budget exceeded; export aborted without altering source evidence."
                                )
                            }
                        }
                    }
                }
                afterSequence = page.last().sequenceNumber
            }

            if (preflightCount != roomCount) {
                return@withContext ExportResult.Failed(
                    "pre-write verification",
                    "Paged preflight count ($preflightCount) != Room COUNT(*) ($roomCount)"
                )
            }
            val audit = auditBuilder.finish()

            val manifest = DatasetManifest(
                exportTimestampUtcMs = System.currentTimeMillis(),
                schemaVersion = session.schemaVersion,
                sessionId = sessionId,
                deviceManufacturer = session.deviceManufacturer,
                deviceModel = session.deviceModel,
                totalObservations = roomCount,
                totalSystemEvents = systemEventCount,
                totalErrorEvents = errorEventCount,
                startTimeUtcMs = session.startTimeMs,
                endTimeUtcMs = session.endTimeMs,
                capabilityReportJson = session.capabilityReportJson,
                permissionStateJson = session.permissionStateJson,
                sequenceMin = audit.firstSequence,
                sequenceMax = audit.lastSequence,
                integrityClean = audit.isClean,
                sequenceContiguous = audit.sequenceContiguous,
                ingestionTimestampsNonDecreasing = audit.ingestionTimestampsNonDecreasing,
                ingestionTimestampedObservationCount = audit.ingestionTimestampedObservationCount,
                sourceTimestampRegressions = audit.sourceTimestampRegressions,
                sourceTimestampedObservationCount = audit.sourceTimestampedObservationCount,
                gnssDeliveryDiagnosticsJson = runCatching {
                    JSONObject(session.deviceMetadataJson).optJSONObject("gnssDeliveryDiagnostics")?.toString() ?: "{}"
                }.getOrDefault("{}")
            )

            val manifestFile = File(tempDir, "dataset_manifest.json")
            manifestFile.writeText(manifest.toJson().toString(2))

            val jsonFile = File(tempDir, "observations.json")
            val csvFile = File(tempDir, "observations.csv")
            val eventsJsonFile = File(tempDir, "session_events.json")
            val eventsCsvFile = File(tempDir, "session_events.csv")
            val sessionLogFile = File(tempDir, "session_log.txt")
            var jsonCount = 0
            var eventJsonCount = 0
            var outputCount = 0
            var outputEventCount = 0

            // Pass 2: stream all primary exports from bounded keyset pages in sequence order.
            val jsonArray = StreamingJsonArrayWriter.open(jsonFile)
            val eventJsonArray = StreamingJsonArrayWriter.open(eventsJsonFile)
            try {
                jsonArray.use { json ->
                    eventJsonArray.use { events ->
                        csvFile.bufferedWriter().use { csvWriter ->
                            eventsCsvFile.bufferedWriter().use { eventCsvWriter ->
                                sessionLogFile.bufferedWriter().use { logWriter ->
                                    csvWriter.appendLine(CSV_HEADER)
                                    eventCsvWriter.appendLine(EVENT_CSV_HEADER)
                                    writeSessionLogHeader(session, roomCount, audit, logWriter)
                                    var outputAfterSequence = Long.MIN_VALUE
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val page = repository.getObservationPage(sessionId, outputAfterSequence, PAGE_SIZE)
                                        if (page.isEmpty()) break
                                        for (row in page) {
                                            json.append(row.toJson())
                                            csvWriter.appendLine(row.toCsvRow())
                                            if (row.type == "SYSTEM_EVENT" || row.type == "ERROR_EVENT") {
                                                events.append(row.toJson())
                                                eventCsvWriter.appendLine(row.toEventCsvRow())
                                                logWriter.appendLine("[seq=" + row.sequenceNumber + "] [" + row.timestampUtcMs + "] [" + row.type + "] [" + row.provider + "] " + row.rawPayloadJson)
                                                outputEventCount += 1
                                            }
                                            outputCount += 1
                                        }
                                        outputAfterSequence = page.last().sequenceNumber
                                    }
                                    json.finish()
                                    events.finish()
                                    jsonCount = json.count
                                    eventJsonCount = events.count
                                }
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }

            if (outputCount != roomCount || jsonCount != roomCount ||
                outputEventCount != systemEventCount + errorEventCount ||
                eventJsonCount != outputEventCount
            ) {
                return@withContext ExportResult.Failed(
                    "stream verification",
                    "Pass count mismatch: room=$roomCount output=$outputCount json=$jsonCount events=$outputEventCount/$eventJsonCount"
                )
            }

            val capabilityFile = File(tempDir, "capabilities.json")
            capabilityFile.writeText(JSONObject(session.capabilityReportJson).toString(2))

            val permissionsFile = File(tempDir, "permissions.json")
            permissionsFile.writeText(JSONObject(session.permissionStateJson).toString(2))

            val auditFile = File(tempDir, "integrity_audit.json")
            auditFile.writeText(audit.toJson().toString(2))

            val gnssDomain = GnssCorrelationEngine().buildSnapshotAndRelease(gnssObservations)
            val gnssDomainFile = File(tempDir, "gnss_domain_snapshot.json")
            GnssDomainSnapshotJsonWriter.write(gnssDomainFile, gnssDomain)

            val satelliteEvidenceCsvFile = File(tempDir, "satellite_evidence.csv")
            satelliteEvidenceCsvFile.bufferedWriter().use { writer ->
                writer.appendLine(SATELLITE_CSV_HEADER)
                gnssDomain.satelliteEvidence.forEach { s -> writer.appendLine(satelliteCsvRow(s)) }
            }

            val antennaEvidenceFile = File(tempDir, "gnss_antenna_evidence.json")
            val antennaJsonCount = GnssDomainSnapshotJsonWriter.writeAntennaEvidence(antennaEvidenceFile, gnssDomain.antennaEvidence)
            check(antennaJsonCount == gnssDomain.antennaEvidence.size) {
                "Antenna evidence JSON count mismatch: expected=${gnssDomain.antennaEvidence.size} actual=$antennaJsonCount"
            }

            val sqliteFile = File(tempDir, "horizon_session.sqlite")
            writeSessionSqlite(sqliteFile, session, sessionId, audit, roomCount)

            val csvRowCount = csvFile.useLines { it.drop(1).count { row -> row.isNotBlank() } }
            val sqliteCount = verifySqlite(sqliteFile, sessionId)
            if (jsonCount != roomCount || csvRowCount != roomCount || sqliteCount != roomCount) {
                return@withContext ExportResult.Failed(
                    "post-write verification",
                    "Count mismatch: room=$roomCount json=$jsonCount csv=$csvRowCount sqlite=$sqliteCount"
                )
            }

            val exportsBase = context.getExternalFilesDir(null) ?: context.filesDir
            val exportsDir = File(exportsBase, "exports").apply {
                if (!exists()) {
                    check(mkdirs()) { "Unable to create exports directory: $this" }
                }
                check(isDirectory) { "Exports path is not a directory: $this" }
            }
            val finalFile = File(exportsDir, "horizon_session_$safeSessionToken.zip")
            val tempZipFile = File(exportsDir, ".$safeSessionToken." + System.nanoTime() + ".zip.tmp")
            check(!tempZipFile.exists()) { "Temporary ZIP path collision: $tempZipFile" }
            temporaryZip = tempZipFile

            val archiveFilesWithoutChecksums = listOf(
                manifestFile, jsonFile, csvFile, eventsJsonFile, eventsCsvFile, sessionLogFile,
                capabilityFile, permissionsFile, auditFile, gnssDomainFile, satelliteEvidenceCsvFile, antennaEvidenceFile, sqliteFile
            )
            val checksumsFile = File(tempDir, "checksums.sha256")
            checksumsFile.bufferedWriter().use { writer ->
                archiveFilesWithoutChecksums.forEach { file ->
                    writer.appendLine("${sha256File(file)}  ${file.name}")
                }
            }
            val archiveFiles = archiveFilesWithoutChecksums + checksumsFile
            ZipOutputStream(tempZipFile.outputStream().buffered()).use { zip ->
                archiveFiles.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }

            // Verify the complete candidate before touching an existing export. Prefer an atomic
            // same-directory replacement; leave the previous final file intact if verification fails.
            val readback = verifyFinalArchive(tempZipFile, sessionId, roomCount)
            if (readback != null) {
                return@withContext ExportResult.Failed("readback", readback)
            }
            replaceAtomically(tempZipFile, finalFile)
            temporaryZip = null

            Log.i(TAG, "Export SUCCESS session=$sessionId observations=$roomCount file=$finalFile")
            ExportResult.Success(finalFile, roomCount)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Export failed for session=$sessionId", e)
            ExportResult.Failed("exception", e.message ?: e.toString())
        } finally {
            tempDir.deleteRecursively()
            temporaryZip?.delete()
        }
    }

    /**
     * Writes a JSON array incrementally. Avoids retaining every JSONObject and one giant
     * pretty-printed String at once when exporting long measurement sessions.
     */
    private fun writeJsonArray(file: File, objects: Sequence<JSONObject>): Int {
        var count = 0
        file.bufferedWriter().use { writer ->
            writer.append("[\n")
            objects.forEach { item ->
                if (count > 0) writer.append(",\n")
                writer.append("  ")
                writer.append(item.toString())
                count += 1
            }
            writer.append("\n]\n")
        }
        return count
    }

    private suspend fun writeSessionSqlite(
        file: File,
        session: SessionEntity,
        sessionId: String,
        audit: horizon.observatory.domain.model.IntegrityAuditResult,
        expectedCount: Int
    ): Int {
        var writtenCount = 0
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("CREATE TABLE sessions (sessionId TEXT PRIMARY KEY, startTimeMs INTEGER NOT NULL, endTimeMs INTEGER, lifecycleState TEXT NOT NULL, deviceManufacturer TEXT NOT NULL, deviceModel TEXT NOT NULL, androidVersion TEXT NOT NULL, sdkVersion INTEGER NOT NULL, applicationVersion TEXT NOT NULL, schemaVersion INTEGER NOT NULL, deviceMetadataJson TEXT NOT NULL, capabilityReportJson TEXT NOT NULL, permissionStateJson TEXT NOT NULL)")
            db.execSQL("CREATE TABLE observations (observationId INTEGER PRIMARY KEY, sessionId TEXT NOT NULL, sequenceNumber INTEGER NOT NULL, timestampUtcMs INTEGER NOT NULL, monotonicTimestampNs INTEGER, ingestionMonotonicTimestampNs INTEGER, timestampDomain TEXT NOT NULL, timestampUncertaintyNs INTEGER, source TEXT NOT NULL, technology TEXT NOT NULL, type TEXT NOT NULL, provider TEXT NOT NULL, rawPayloadJson TEXT NOT NULL, normalizedPayloadJson TEXT NOT NULL, capabilityState TEXT NOT NULL, evidenceStatus TEXT NOT NULL, provenance TEXT NOT NULL, UNIQUE(sessionId, sequenceNumber))")
            db.execSQL("CREATE TABLE integrity_audit (sessionId TEXT PRIMARY KEY, observationCount INTEGER NOT NULL, firstSequence INTEGER, lastSequence INTEGER, sequenceContiguous INTEGER NOT NULL, ingestionTimestampsNonDecreasing INTEGER NOT NULL, ingestionTimestampedObservationCount INTEGER NOT NULL, sourceTimestampRegressions INTEGER NOT NULL, sourceTimestampedObservationCount INTEGER NOT NULL)")
            db.beginTransaction()
            try {
                val sessionValues = ContentValues().apply {
                    put("sessionId", session.sessionId); put("startTimeMs", session.startTimeMs)
                    session.endTimeMs?.let { put("endTimeMs", it) }
                    put("lifecycleState", session.lifecycleState); put("deviceManufacturer", session.deviceManufacturer)
                    put("deviceModel", session.deviceModel); put("androidVersion", session.androidVersion)
                    put("sdkVersion", session.sdkVersion); put("applicationVersion", session.applicationVersion)
                    put("schemaVersion", session.schemaVersion); put("deviceMetadataJson", session.deviceMetadataJson)
                    put("capabilityReportJson", session.capabilityReportJson); put("permissionStateJson", session.permissionStateJson)
                }
                db.insertOrThrow("sessions", null, sessionValues)

                var afterSequence = Long.MIN_VALUE
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val page = repository.getObservationPage(sessionId, afterSequence, PAGE_SIZE)
                    if (page.isEmpty()) break
                    for (row in page) {
                        val values = ContentValues().apply {
                            put("observationId", row.observationId); put("sessionId", row.sessionId); put("sequenceNumber", row.sequenceNumber)
                            put("timestampUtcMs", row.timestampUtcMs); row.monotonicTimestampNs?.let { put("monotonicTimestampNs", it) }
                            row.ingestionMonotonicTimestampNs?.let { put("ingestionMonotonicTimestampNs", it) }
                            put("timestampDomain", row.timestampDomain); row.timestampUncertaintyNs?.let { put("timestampUncertaintyNs", it) }
                            put("source", row.source); put("technology", row.technology); put("type", row.type); put("provider", row.provider)
                            put("rawPayloadJson", row.rawPayloadJson); put("normalizedPayloadJson", row.normalizedPayloadJson)
                            put("capabilityState", row.capabilityState); put("evidenceStatus", row.evidenceStatus); put("provenance", row.provenance)
                        }
                        db.insertOrThrow("observations", null, values)
                    }
                    writtenCount += page.size
                    afterSequence = page.last().sequenceNumber
                }
                check(writtenCount == expectedCount) {
                    "SQLite export row count changed during export: expected=$expectedCount actual=$writtenCount"
                }

                val auditValues = ContentValues().apply {
                    put("sessionId", session.sessionId); put("observationCount", audit.observationCount)
                    audit.firstSequence?.let { put("firstSequence", it) }
                    audit.lastSequence?.let { put("lastSequence", it) }
                    put("sequenceContiguous", if (audit.sequenceContiguous) 1 else 0)
                    put("ingestionTimestampsNonDecreasing", if (audit.ingestionTimestampsNonDecreasing) 1 else 0)
                    put("ingestionTimestampedObservationCount", audit.ingestionTimestampedObservationCount)
                    put("sourceTimestampRegressions", audit.sourceTimestampRegressions)
                    put("sourceTimestampedObservationCount", audit.sourceTimestampedObservationCount)
                }
                db.insertOrThrow("integrity_audit", null, auditValues)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            db.close()
        }
        return writtenCount
    }

    private fun verifySqliteDeviceMetadata(file: File, sessionId: String): String {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            db.rawQuery("SELECT deviceMetadataJson FROM sessions WHERE sessionId = ?", arrayOf(sessionId)).use { cursor ->
                check(cursor.moveToFirst()) { "SQLite session metadata row missing" }
                return cursor.getString(0)
            }
        } finally {
            db.close()
        }
    }

    private fun verifySqliteObservationTypeCounts(file: File, sessionId: String): Map<String, Int> {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val counts = linkedMapOf<String, Int>()
            db.rawQuery(
                "SELECT type, COUNT(*) FROM observations WHERE sessionId = ? GROUP BY type",
                arrayOf(sessionId)
            ).use { cursor ->
                while (cursor.moveToNext()) counts[cursor.getString(0)] = cursor.getInt(1)
            }
            return counts
        } finally {
            db.close()
        }
    }

    private fun verifySqlite(file: File, sessionId: String): Int {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            db.rawQuery("SELECT COUNT(*) FROM sessions WHERE sessionId = ?", arrayOf(sessionId)).use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 1) { "SQLite session row missing" }
            }
            db.rawQuery("SELECT COUNT(*) FROM observations WHERE sessionId = ?", arrayOf(sessionId)).use { cursor ->
                check(cursor.moveToFirst()) { "SQLite observation count query failed" }
                return cursor.getInt(0)
            }
        } finally {
            db.close()
        }
    }

    private fun verifyFinalArchive(file: File, sessionId: String, expectedCount: Int): String? {
        // Verify the archive using disk-backed entries. readBytes() plus retained JSON/CSV strings
        // duplicated the largest export payloads in the 256 MiB app heap and caused OOM on long sessions.
        val verifyDir = File(file.parentFile, "readback_" + safeFileToken(sessionId) + "_" + System.nanoTime())
        try {
            check(verifyDir.mkdirs()) { "Unable to create archive readback directory" }
            val entryFiles = linkedMapOf<String, File>()
            val entryHashes = linkedMapOf<String, String>()
            ZipInputStream(file.inputStream().buffered()).use { zipInput ->
                while (true) {
                    val entry = zipInput.nextEntry ?: break
                    check(!entry.isDirectory && entry.name == File(entry.name).name) {
                        "Unexpected archive path: ${entry.name}"
                    }
                    val entryFile = File(verifyDir, entry.name)
                    entryFile.outputStream().buffered().use { output -> zipInput.copyTo(output) }
                    entryFiles[entry.name] = entryFile
                    entryHashes[entry.name] = sha256File(entryFile)
                    zipInput.closeEntry()
                }
            }

            val required = listOf(
                "dataset_manifest.json", "observations.json", "observations.csv",
                "session_events.json", "session_events.csv", "session_log.txt",
                "integrity_audit.json", "capabilities.json", "permissions.json",
                "gnss_domain_snapshot.json", "satellite_evidence.csv",
                "gnss_antenna_evidence.json", "checksums.sha256", "horizon_session.sqlite"
            )
            val missing = required.filter { entryFiles[it] == null }
            check(missing.isEmpty()) { "Required archive entries missing: ${missing.joinToString()}" }

            val checksumsFile = entryFiles.getValue("checksums.sha256")
            val checksumLines = checksumsFile.readLines().filter { it.isNotBlank() }
            checksumLines.forEach { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                check(parts.size == 2) { "Invalid checksum line: $line" }
                val expectedHash = parts[0]
                val entryName = parts[1].trim()
                check(entryName != "checksums.sha256") { "Checksum file must not checksum itself" }
                check(entryHashes[entryName] == expectedHash) { "Checksum mismatch for $entryName" }
            }
            check(checksumLines.size == entryFiles.size - 1) { "Unexpected checksum entry count" }

            val manifest = JSONObject(entryFiles.getValue("dataset_manifest.json").readText())
            check(manifest.optString("sessionId") == sessionId) { "Manifest sessionId mismatch" }
            check(manifest.optInt("totalObservations", -1) == expectedCount) { "Manifest count mismatch" }
            val diagnostics = manifest.optJSONObject("gnssDeliveryDiagnostics")
                ?: error("GNSS delivery diagnostics missing from manifest")
            if (diagnostics.length() > 0) {
                check(diagnostics.optInt("schemaVersion", -1) == 1) { "Unsupported GNSS diagnostics schema" }
                if (diagnostics.optString("state") == "FINALIZED") {
                    check(diagnostics.optBoolean("invariantsHold", false)) { "GNSS delivery diagnostics invariant failed" }
                }
                if (diagnostics.optString("state") == "FINALIZATION_INCOMPLETE" ||
                    diagnostics.optString("state") == "UNAVAILABLE_PROCESS_LOSS") {
                    check(!diagnostics.optBoolean("countsComplete", true)) { "Incomplete GNSS diagnostics incorrectly marked complete" }
                }
            }

            val jsonCount = StreamingJsonArrayWriter.countObjects(entryFiles.getValue("observations.json"))
            val csvCount = entryFiles.getValue("observations.csv").useLines { lines ->
                lines.drop(1).count { it.isNotBlank() }
            }
            val sqliteCount = verifySqlite(entryFiles.getValue("horizon_session.sqlite"), sessionId)
            val sqliteTypeCounts = verifySqliteObservationTypeCounts(entryFiles.getValue("horizon_session.sqlite"), sessionId)
            val sqliteMetadata = verifySqliteDeviceMetadata(entryFiles.getValue("horizon_session.sqlite"), sessionId)
            val sqliteDiagnostics = runCatching { JSONObject(sqliteMetadata).optJSONObject("gnssDeliveryDiagnostics") }.getOrNull()
            check((diagnostics.length() == 0 && sqliteDiagnostics == null) ||
                (sqliteDiagnostics != null && sqliteDiagnostics.toString() == diagnostics.toString())) {
                "GNSS delivery diagnostics mismatch between manifest and SQLite session metadata"
            }
            if (diagnostics.optString("state") == "FINALIZED") {
                val mismatches = horizon.observatory.acquisition.gnss.reconcileAcceptedDeliveriesWithPersistedRows(
                    diagnostics,
                    sqliteTypeCounts
                )
                check(mismatches.isEmpty()) {
                    "GNSS accepted-to-persisted row mismatch: ${mismatches.joinToString(separator = "; ")}"
                }
            }
            val sessionLog = entryFiles.getValue("session_log.txt").readText()
            if (diagnostics.length() > 0) {
                check(sessionLog.contains("gnssDeliveryDiagnosticsState=${diagnostics.optString("state")}")) {
                    "GNSS delivery diagnostics state mismatch in session log"
                }
                check(sessionLog.contains("gnssDeliveryDiagnostics=${diagnostics.toString()}")) {
                    "GNSS delivery diagnostics payload mismatch in session log"
                }
            }
            check(jsonCount == expectedCount && csvCount == expectedCount && sqliteCount == expectedCount) {
                "Readback count mismatch: expected=$expectedCount json=$jsonCount csv=$csvCount sqlite=$sqliteCount"
            }

            check(entryFiles.getValue("integrity_audit.json").length() > 0L) { "Integrity audit readback empty" }
            check(entryFiles.getValue("session_events.json").length() > 0L) { "Session event log JSON readback empty" }
            check(entryFiles.getValue("session_events.csv").length() > 0L) { "Session event log CSV readback empty" }
            check(entryFiles.getValue("session_log.txt").length() > 0L) { "Session log text readback empty" }
            check(entryFiles.getValue("capabilities.json").length() > 0L) { "Capability export readback empty" }
            check(entryFiles.getValue("permissions.json").length() > 0L) { "Permission export readback empty" }
            val domainFile = entryFiles.getValue("gnss_domain_snapshot.json")
            check(domainFile.length() > 0L) { "GNSS domain snapshot readback empty" }
            check(domainFile.useLines { lines ->
                var hasSatelliteEvidence = false
                var hasConstellationSummaries = false
                lines.forEach { line ->
                    if (line.contains("satelliteEvidence")) hasSatelliteEvidence = true
                    if (line.contains("constellationSummaries")) hasConstellationSummaries = true
                }
                hasSatelliteEvidence && hasConstellationSummaries
            }) { "GNSS domain snapshot readback invalid" }
            check(entryFiles.getValue("satellite_evidence.csv").length() > 0L) { "Satellite evidence CSV readback empty" }
            check(entryFiles.getValue("gnss_antenna_evidence.json").length() > 0L) { "Antenna evidence JSON readback empty" }
            return null
        } catch (e: Exception) {
            return e.message ?: e.toString()
        } finally {
            verifyDir.deleteRecursively()
        }
    }

    /** Same JSON the session package uses for antenna evidence (single source of truth for reports). */
    fun antennaEvidenceJson(e: horizon.observatory.domain.gnss.GnssAntennaEvidence): String = e.toJson().toString(2)

    private fun horizon.observatory.domain.gnss.GnssAntennaEvidence.toJson(): JSONObject = JSONObject().apply {
        put("carrierFrequencyMHz", carrierFrequencyMHz ?: JSONObject.NULL)
        put("phaseCenterOffset", phaseCenterOffset ?: JSONObject.NULL)
        put("phaseCenterVariationCorrections", phaseCenterVariationCorrections ?: JSONObject.NULL)
        put("signalGainCorrections", signalGainCorrections ?: JSONObject.NULL)
        put("monotonicTimestampNs", monotonicTimestampNs ?: JSONObject.NULL)
        put("capabilityState", capabilityState)
        put("provenance", provenance)
    }

    private fun satelliteCsvRow(s: horizon.observatory.domain.gnss.SatelliteEvidence): String = listOf(
        s.constellationType, s.svid, s.carrierFrequencyHz, s.cn0DbHz, s.basebandCn0DbHz,
        s.elevationDegrees, s.azimuthDegrees, s.usedInFix, s.automaticGainControlLevelDb,
        s.navigationMessageCount, s.navigationAssociation.name, s.rawSequenceNumber,
        s.statusSequenceNumber, s.match.name, s.observationMonotonicTimestampNs,
        s.statusMonotonicTimestampNs
    ).joinToString(",") { csvEscape(it.toString()) }

    private fun writeSessionLogHeader(
        session: SessionEntity,
        observationCount: Int,
        audit: horizon.observatory.domain.model.IntegrityAuditResult,
        writer: BufferedWriter
    ) {
        writer.appendLine("HORIZON SESSION LOG")
        writer.appendLine("sessionId=${session.sessionId}")
        writer.appendLine("lifecycle=${session.lifecycleState}")
        writer.appendLine("device=${session.deviceManufacturer} ${session.deviceModel}")
        writer.appendLine("android=${session.androidVersion} api=${session.sdkVersion}")
        writer.appendLine("startUtcMs=${session.startTimeMs}")
        writer.appendLine("endUtcMs=${session.endTimeMs ?: "UNSET"}")
        writer.appendLine("observationCount=$observationCount")
        writer.appendLine("integrityClean=${audit.isClean}")
        writer.appendLine("sequenceContiguous=${audit.sequenceContiguous}")
        writer.appendLine("ingestionTimestampsNonDecreasing=${audit.ingestionTimestampsNonDecreasing}")
        writer.appendLine("ingestionTimestampedObservationCount=${audit.ingestionTimestampedObservationCount}")
        writer.appendLine("sourceTimestampRegressions=${audit.sourceTimestampRegressions}")
        writer.appendLine("sourceTimestampedObservationCount=${audit.sourceTimestampedObservationCount}")
        val diagnostics = runCatching { JSONObject(session.deviceMetadataJson).optJSONObject("gnssDeliveryDiagnostics") }
            .getOrNull()
        writer.appendLine("gnssDeliveryDiagnosticsState=${diagnostics?.optString("state", "UNAVAILABLE") ?: "UNAVAILABLE"}")
        writer.appendLine("gnssDeliveryDiagnosticsInvariantsHold=${diagnostics?.optBoolean("invariantsHold", false) ?: false}")
        writer.appendLine("gnssDeliveryDiagnostics=${diagnostics?.toString() ?: "{}"}")
        writer.appendLine()
    }

    private fun DatasetManifest.toJson(): JSONObject = JSONObject().apply {
        put("exportTimestampUtcMs", exportTimestampUtcMs)
        put("schemaVersion", schemaVersion)
        put("sessionId", sessionId)
        put("deviceManufacturer", deviceManufacturer)
        put("deviceModel", deviceModel)
        put("totalObservations", totalObservations)
        put("totalSystemEvents", totalSystemEvents)
        put("totalErrorEvents", totalErrorEvents)
        put("startTimeUtcMs", startTimeUtcMs)
        put("endTimeUtcMs", endTimeUtcMs ?: JSONObject.NULL)
        put("capabilityReport", JSONObject(capabilityReportJson))
        put("permissionState", JSONObject(permissionStateJson))
        put("sequenceMin", sequenceMin ?: JSONObject.NULL)
        put("sequenceMax", sequenceMax ?: JSONObject.NULL)
        put("integrityClean", integrityClean)
        put("sequenceContiguous", sequenceContiguous)
        put("ingestionTimestampsNonDecreasing", ingestionTimestampsNonDecreasing)
        put("ingestionTimestampedObservationCount", ingestionTimestampedObservationCount)
        put("sourceTimestampRegressions", sourceTimestampRegressions)
        put("sourceTimestampedObservationCount", sourceTimestampedObservationCount)
        put("provenanceNote", provenanceNote)
        put("gnssDeliveryDiagnostics", JSONObject(gnssDeliveryDiagnosticsJson))
    }

    private fun horizon.observatory.domain.model.IntegrityAuditResult.toJson(): JSONObject = JSONObject().apply {
        put("observationCount", observationCount)
        put("firstSequence", firstSequence ?: JSONObject.NULL)
        put("lastSequence", lastSequence ?: JSONObject.NULL)
        put("sequenceContiguous", sequenceContiguous)
        put("ingestionTimestampsNonDecreasing", ingestionTimestampsNonDecreasing)
        put("ingestionTimestampedObservationCount", ingestionTimestampedObservationCount)
        put("sourceTimestampRegressions", sourceTimestampRegressions)
        put("sourceTimestampedObservationCount", sourceTimestampedObservationCount)
        put("isClean", isClean)
    }

    private fun ObservationEntity.toJson(): JSONObject = JSONObject().apply {
        put("observationId", observationId)
        put("sessionId", sessionId)
        put("sequenceNumber", sequenceNumber)
        put("timestampUtcMs", timestampUtcMs)
        put("sourceMonotonicTimestampNs", monotonicTimestampNs ?: JSONObject.NULL)
        put("ingestionMonotonicTimestampNs", ingestionMonotonicTimestampNs ?: JSONObject.NULL)
        put("source", source)
        put("technology", technology)
        put("type", type)
        put("provider", provider)
        put("timestampDomain", timestampDomain)
        put("timestampUncertaintyNs", timestampUncertaintyNs ?: JSONObject.NULL)
        put("rawPayloadJson", JSONObject(rawPayloadJson))
        put("normalizedPayloadJson", JSONObject(normalizedPayloadJson))
        put("capabilityState", capabilityState)
        put("evidenceStatus", evidenceStatus)
        put("provenance", provenance)
    }

    private fun ObservationEntity.toEventCsvRow(): String = listOf(
        observationId, sessionId, sequenceNumber, timestampUtcMs, monotonicTimestampNs, ingestionMonotonicTimestampNs,
        type, provider, capabilityState, evidenceStatus, rawPayloadJson
    ).joinToString(",") { csvEscape(it.toString()) }

    private fun ObservationEntity.toCsvRow(): String = listOf(
        observationId, sessionId, sequenceNumber, timestampUtcMs, monotonicTimestampNs, ingestionMonotonicTimestampNs,
        source, technology, type, provider, timestampDomain, timestampUncertaintyNs, capabilityState, evidenceStatus, provenance
    ).joinToString(",") { csvEscape(it.toString()) }

    private fun csvEscape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value

    private fun safeFileToken(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(96).trim('.').ifBlank { "session" }

    private fun replaceAtomically(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (unsupported: AtomicMoveNotSupportedException) {
            // Some filesystems do not implement atomic replacement; do the safest same-directory
            // replacement supported by that filesystem, without deleting the old file first.
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sha256File(file: File): String =
        file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }

    private fun sha256Bytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "HorizonExport"
        private const val PAGE_SIZE = 256
        // The 2026-10-18 verified 93,754-row field session contains 43,345 GNSS correlation
        // source rows and 42,981,919 raw-payload characters. These caps cover that observed load
        // with headroom while still failing closed on unbounded future input.
        private const val MAX_GNSS_CORRELATION_SOURCE_ROWS = 50_000
        private const val MAX_GNSS_CORRELATION_PAYLOAD_CHARS = 64L * 1024L * 1024L
        private const val MAX_SATELLITES_PER_STATUS_ROW = 2_000
        private const val MAX_GNSS_STATUS_SATELLITES = 100_000
        private val GNSS_CORRELATION_TYPES = setOf(
            "GNSS_STATUS",
            "GNSS_RAW_MEASUREMENT",
            "GNSS_NAVIGATION_MESSAGE",
            "GNSS_ANTENNA_INFO"
        )
        private const val CSV_HEADER =
            "observationId,sessionId,sequenceNumber,timestampUtcMs,sourceMonotonicTimestampNs,ingestionMonotonicTimestampNs,source,technology,type,provider,timestampDomain,timestampUncertaintyNs,capabilityState,evidenceStatus,provenance"
        private const val SATELLITE_CSV_HEADER = "constellationType,svid,carrierFrequencyHz,cn0DbHz,basebandCn0DbHz,elevationDegrees,azimuthDegrees,usedInFix,automaticGainControlLevelDb,navigationMessageCount,navigationAssociation,rawSequenceNumber,statusSequenceNumber,match,observationMonotonicTimestampNs,statusMonotonicTimestampNs"
        private const val EVENT_CSV_HEADER =
            "observationId,sessionId,sequenceNumber,timestampUtcMs,sourceMonotonicTimestampNs,ingestionMonotonicTimestampNs,type,provider,capabilityState,evidenceStatus,rawPayloadJson"
    }
}
