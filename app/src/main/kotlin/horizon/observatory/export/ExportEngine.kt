package horizon.observatory.export

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import horizon.observatory.domain.model.IntegrityAudit
import horizon.observatory.domain.model.RawObservationView
import horizon.observatory.analysis.gnss.GnssCorrelationEngine
import horizon.observatory.domain.gnss.GnssDomainSnapshot
import horizon.observatory.export.model.DatasetManifest
import horizon.observatory.storage.entity.ObservationEntity
import horizon.observatory.storage.entity.SessionEntity
import horizon.observatory.storage.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

        val tempDir = File(context.cacheDir, "export_tmp_$sessionId").apply {
            deleteRecursively()
            check(mkdirs()) { "Unable to create export temp directory: $this" }
        }

        try {
            val observations = repository.getObservationsSnapshot(sessionId)
            val roomCount = repository.countObservations(sessionId)
            if (observations.size != roomCount) {
                return@withContext ExportResult.Failed(
                    "pre-write verification",
                    "Snapshot size (${observations.size}) != Room COUNT(*) ($roomCount)"
                )
            }

            val audit = IntegrityAudit.audit(
                observations.map {
                    RawObservationView(
                        sequenceNumber = it.sequenceNumber,
                        sourceMonotonicTimestampNs = it.monotonicTimestampNs,
                        ingestionMonotonicTimestampNs = it.ingestionMonotonicTimestampNs,
                        provider = it.provider
                    )
                }
            )
            val systemEvents = observations.filter { it.type == "SYSTEM_EVENT" }
            val errorEvents = observations.filter { it.type == "ERROR_EVENT" }

            val manifest = DatasetManifest(
                exportTimestampUtcMs = System.currentTimeMillis(),
                schemaVersion = session.schemaVersion,
                sessionId = sessionId,
                deviceManufacturer = session.deviceManufacturer,
                deviceModel = session.deviceModel,
                totalObservations = observations.size,
                totalSystemEvents = systemEvents.size,
                totalErrorEvents = errorEvents.size,
                startTimeUtcMs = session.startTimeMs,
                endTimeUtcMs = session.endTimeMs,
                capabilityReportJson = session.capabilityReportJson,
                permissionStateJson = session.permissionStateJson,
                sequenceMin = repository.minSequence(sessionId),
                sequenceMax = repository.maxSequence(sessionId),
                integrityClean = audit.isClean,
                sequenceContiguous = audit.sequenceContiguous,
                ingestionTimestampsNonDecreasing = audit.ingestionTimestampsNonDecreasing,
                ingestionTimestampedObservationCount = audit.ingestionTimestampedObservationCount,
                sourceTimestampRegressions = audit.sourceTimestampRegressions,
                sourceTimestampedObservationCount = audit.sourceTimestampedObservationCount
            )

            val manifestFile = File(tempDir, "dataset_manifest.json")
            manifestFile.writeText(manifest.toJson().toString(2))

            val jsonFile = File(tempDir, "observations.json")
            val jsonArray = JSONArray()
            observations.forEach { jsonArray.put(it.toJson()) }
            jsonFile.writeText(jsonArray.toString(2))

            val csvFile = File(tempDir, "observations.csv")
            csvFile.bufferedWriter().use { writer ->
                writer.appendLine(CSV_HEADER)
                observations.forEach { writer.appendLine(it.toCsvRow()) }
            }

            val eventsJsonFile = File(tempDir, "session_events.json")
            val eventsJson = JSONArray()
            (systemEvents + errorEvents).sortedBy { it.sequenceNumber }.forEach { eventsJson.put(it.toJson()) }
            eventsJsonFile.writeText(eventsJson.toString(2))

            val eventsCsvFile = File(tempDir, "session_events.csv")
            eventsCsvFile.bufferedWriter().use { writer ->
                writer.appendLine(EVENT_CSV_HEADER)
                (systemEvents + errorEvents).sortedBy { it.sequenceNumber }.forEach { writer.appendLine(it.toEventCsvRow()) }
            }

            val sessionLogFile = File(tempDir, "session_log.txt")
            sessionLogFile.writeText(buildSessionLog(session, observations, audit))

            val capabilityFile = File(tempDir, "capabilities.json")
            capabilityFile.writeText(JSONObject(session.capabilityReportJson).toString(2))

            val permissionsFile = File(tempDir, "permissions.json")
            permissionsFile.writeText(JSONObject(session.permissionStateJson).toString(2))

            val auditFile = File(tempDir, "integrity_audit.json")
            auditFile.writeText(audit.toJson().toString(2))

            val gnssDomain = GnssCorrelationEngine().buildSnapshot(observations)
            val gnssDomainFile = File(tempDir, "gnss_domain_snapshot.json")
            gnssDomainFile.writeText(gnssDomain.toJson().toString(2))

            val satelliteEvidenceCsvFile = File(tempDir, "satellite_evidence.csv")
            satelliteEvidenceCsvFile.bufferedWriter().use { writer ->
                writer.appendLine(SATELLITE_CSV_HEADER)
                gnssDomain.satelliteEvidence.forEach { s -> writer.appendLine(satelliteCsvRow(s)) }
            }

            val antennaEvidenceFile = File(tempDir, "gnss_antenna_evidence.json")
            antennaEvidenceFile.writeText(JSONArray().apply { gnssDomain.antennaEvidence.forEach { put(it.toJson()) } }.toString(2))

            val sqliteFile = File(tempDir, "horizon_session.sqlite")
            writeSessionSqlite(sqliteFile, session, observations, audit)

            val jsonCount = jsonArray.length()
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
            val finalFile = File(exportsDir, "horizon_session_$sessionId.zip")
            val tempZip = File(exportsDir, "$sessionId.zip.tmp")
            if (tempZip.exists()) tempZip.delete()
            if (finalFile.exists()) finalFile.delete()

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
            ZipOutputStream(tempZip.outputStream().buffered()).use { zip ->
                archiveFiles.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }

            if (!tempZip.renameTo(finalFile)) {
                return@withContext ExportResult.Failed(
                    "finalize",
                    "renameTo failed for $finalFile; temporary ZIP was created at $tempZip"
                )
            }

            val readback = verifyFinalArchive(finalFile, sessionId, roomCount)
            if (readback != null) {
                finalFile.delete()
                return@withContext ExportResult.Failed("readback", readback)
            }

            Log.i(TAG, "Export SUCCESS session=$sessionId observations=$roomCount file=$finalFile")
            ExportResult.Success(finalFile, roomCount)
        } catch (e: Exception) {
            Log.e(TAG, "Export failed for session=$sessionId", e)
            ExportResult.Failed("exception", e.message ?: e.toString())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun writeSessionSqlite(
        file: File,
        session: SessionEntity,
        observations: List<ObservationEntity>,
        audit: horizon.observatory.domain.model.IntegrityAuditResult
    ) {
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

                observations.forEach { row ->
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
        val extracted = File(file.parentFile, "readback_${sessionId}_${System.nanoTime()}.sqlite")
        try {
            var manifestJson: String? = null
            var jsonText: String? = null
            var csvText: String? = null
            var eventsJsonText: String? = null
            var eventsCsvText: String? = null
            var sessionLogText: String? = null
            var auditJson: String? = null
            var capabilitiesJson: String? = null
            var permissionsJson: String? = null
            var gnssDomainJson: String? = null
            var satelliteEvidenceCsv: String? = null
            var antennaEvidenceJson: String? = null
            var checksumsText: String? = null
            val entryHashes = linkedMapOf<String, String>()
            ZipInputStream(file.inputStream().buffered()).use { zipInput ->
                while (true) {
                    val entry = zipInput.nextEntry ?: break
                    val bytes = zipInput.readBytes()
                    entryHashes[entry.name] = sha256Bytes(bytes)
                    when (entry.name) {
                        "dataset_manifest.json" -> manifestJson = bytes.toString(Charsets.UTF_8)
                        "observations.json" -> jsonText = bytes.toString(Charsets.UTF_8)
                        "observations.csv" -> csvText = bytes.toString(Charsets.UTF_8)
                        "session_events.json" -> eventsJsonText = bytes.toString(Charsets.UTF_8)
                        "session_events.csv" -> eventsCsvText = bytes.toString(Charsets.UTF_8)
                        "session_log.txt" -> sessionLogText = bytes.toString(Charsets.UTF_8)
                        "integrity_audit.json" -> auditJson = bytes.toString(Charsets.UTF_8)
                        "capabilities.json" -> capabilitiesJson = bytes.toString(Charsets.UTF_8)
                        "permissions.json" -> permissionsJson = bytes.toString(Charsets.UTF_8)
                        "gnss_domain_snapshot.json" -> gnssDomainJson = bytes.toString(Charsets.UTF_8)
                        "satellite_evidence.csv" -> satelliteEvidenceCsv = bytes.toString(Charsets.UTF_8)
                        "gnss_antenna_evidence.json" -> antennaEvidenceJson = bytes.toString(Charsets.UTF_8)
                        "checksums.sha256" -> checksumsText = bytes.toString(Charsets.UTF_8)
                        "horizon_session.sqlite" -> extracted.writeBytes(bytes)
                    }
                }
            }
            if (listOf(manifestJson, jsonText, csvText, eventsJsonText, eventsCsvText, sessionLogText, auditJson, capabilitiesJson, permissionsJson, gnssDomainJson, satelliteEvidenceCsv, antennaEvidenceJson, checksumsText).any { it == null } || !extracted.exists()) {
                return "Required archive entry missing"
            }
            checksumsText!!.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                check(parts.size == 2) { "Invalid checksum line: $line" }
                val expectedHash = parts[0]
                val entryName = parts[1].trim()
                check(entryName != "checksums.sha256") { "Checksum file must not checksum itself" }
                check(entryHashes[entryName] == expectedHash) { "Checksum mismatch for $entryName" }
            }
            val manifest = JSONObject(manifestJson!!)
            check(manifest.optString("sessionId") == sessionId) { "Manifest sessionId mismatch" }
            check(manifest.optInt("totalObservations", -1) == expectedCount) { "Manifest count mismatch" }
            val jsonCount = JSONArray(jsonText!!).length()
            val csvCount = csvText!!.lineSequence().drop(1).count { it.isNotBlank() }
            val sqliteCount = verifySqlite(extracted, sessionId)
            check(jsonCount == expectedCount && csvCount == expectedCount && sqliteCount == expectedCount) {
                "Readback count mismatch: expected=$expectedCount json=$jsonCount csv=$csvCount sqlite=$sqliteCount"
            }
            check(auditJson!!.isNotBlank()) { "Integrity audit readback empty" }
            check(eventsJsonText!!.isNotBlank()) { "Session event log JSON readback empty" }
            check(eventsCsvText!!.isNotBlank()) { "Session event log CSV readback empty" }
            check(sessionLogText!!.isNotBlank()) { "Session log text readback empty" }
            check(capabilitiesJson!!.isNotBlank()) { "Capability export readback empty" }
            check(permissionsJson!!.isNotBlank()) { "Permission export readback empty" }
            val domainJson = JSONObject(gnssDomainJson!!)
            check(domainJson.has("satelliteEvidence") && domainJson.has("constellationSummaries")) { "GNSS domain snapshot readback invalid" }
            check(satelliteEvidenceCsv!!.lineSequence().count() >= 1) { "Satellite evidence CSV readback empty" }
            check(JSONArray(antennaEvidenceJson!!).length() >= 0) { "Antenna evidence JSON unreadable" }
            check(checksumsText!!.lineSequence().count { it.isNotBlank() } == entryHashes.size - 1) { "Unexpected checksum entry count" }
            return null
        } catch (e: Exception) {
            return e.message ?: e.toString()
        } finally {
            extracted.delete()
        }
    }

    private fun GnssDomainSnapshot.toJson(): JSONObject = JSONObject().apply {
        put("sampleCount", signalStatistics.sampleCount)
        put("averageCn0DbHz", signalStatistics.averageCn0DbHz ?: JSONObject.NULL)
        put("minCn0DbHz", signalStatistics.minCn0DbHz ?: JSONObject.NULL)
        put("maxCn0DbHz", signalStatistics.maxCn0DbHz ?: JSONObject.NULL)
        put("averageElevationDegrees", signalStatistics.averageElevationDegrees ?: JSONObject.NULL)
        put("minElevationDegrees", signalStatistics.minElevationDegrees ?: JSONObject.NULL)
        put("maxElevationDegrees", signalStatistics.maxElevationDegrees ?: JSONObject.NULL)
        put("averageAgcDb", signalStatistics.averageAgcDb ?: JSONObject.NULL)
        put("minAgcDb", signalStatistics.minAgcDb ?: JSONObject.NULL)
        put("maxAgcDb", signalStatistics.maxAgcDb ?: JSONObject.NULL)
        put("distinctFrequenciesHz", JSONArray().apply { signalStatistics.distinctFrequenciesHz.forEach { put(it) } })
        put("navigationMessageCount", navigationMessageCount)
        put("correlatedMeasurementCount", correlatedMeasurementCount)
        put("statusOnlySatelliteCount", statusOnlySatelliteCount)
        put("rawOnlySatelliteCount", rawOnlySatelliteCount)
        put("satelliteEvidence", JSONArray().apply { satelliteEvidence.forEach { put(it.toJson()) } })
        put("latestSatelliteEvidence", JSONArray().apply { latestSatelliteEvidence.forEach { put(it.toJson()) } })
        put("historyPoints", JSONArray().apply {
            historyPoints.forEach { point ->
                put(JSONObject().apply {
                    put("sequenceNumber", point.sequenceNumber ?: JSONObject.NULL)
                    put("constellationType", point.constellationType)
                    put("svid", point.svid)
                    put("carrierFrequencyHz", point.carrierFrequencyHz ?: JSONObject.NULL)
                    put("cn0DbHz", point.cn0DbHz ?: JSONObject.NULL)
                    put("basebandCn0DbHz", point.basebandCn0DbHz ?: JSONObject.NULL)
                    put("elevationDegrees", point.elevationDegrees ?: JSONObject.NULL)
                    put("azimuthDegrees", point.azimuthDegrees ?: JSONObject.NULL)
                    put("usedInFix", point.usedInFix ?: JSONObject.NULL)
                    put("agcDb", point.agcDb ?: JSONObject.NULL)
                    put("navigationMessageCount", point.navigationMessageCount)
                    put("match", point.match.name)
                    put("monotonicTimestampNs", point.monotonicTimestampNs ?: JSONObject.NULL)
                })
            }
        })
        put("constellationSummaries", JSONArray().apply {
            constellationSummaries.forEach { s ->
                put(JSONObject().apply {
                    put("constellationType", s.constellationType)
                    put("evidenceCount", s.evidenceCount)
                    put("distinctSatelliteCount", s.distinctSatelliteCount)
                    put("matchedCount", s.matchedCount)
                    put("statusOnlyCount", s.statusOnlyCount)
                    put("rawOnlyCount", s.rawOnlyCount)
                    put("navigationMessageCount", s.navigationMessageCount)
                    put("averageCn0DbHz", s.averageCn0DbHz ?: JSONObject.NULL)
                    put("averageElevationDegrees", s.averageElevationDegrees ?: JSONObject.NULL)
                    put("averageAgcDb", s.averageAgcDb ?: JSONObject.NULL)
                })
            }
        })
        put("antennaEvidence", JSONArray().apply { antennaEvidence.forEach { put(it.toJson()) } })
        put("provenance", "DERIVED_FROM_PERSISTED_GNSS_OBSERVATIONS")
    }

    private fun horizon.observatory.domain.gnss.SatelliteEvidence.toJson(): JSONObject = JSONObject().apply {
        put("constellationType", constellationType)
        put("svid", svid)
        put("carrierFrequencyHz", carrierFrequencyHz ?: JSONObject.NULL)
        put("cn0DbHz", cn0DbHz ?: JSONObject.NULL)
        put("basebandCn0DbHz", basebandCn0DbHz ?: JSONObject.NULL)
        put("elevationDegrees", elevationDegrees ?: JSONObject.NULL)
        put("azimuthDegrees", azimuthDegrees ?: JSONObject.NULL)
        put("usedInFix", usedInFix ?: JSONObject.NULL)
        put("measurementState", measurementState ?: JSONObject.NULL)
        put("multipathIndicator", multipathIndicator ?: JSONObject.NULL)
        put("accumulatedDeltaRangeMeters", accumulatedDeltaRangeMeters ?: JSONObject.NULL)
        put("accumulatedDeltaRangeState", accumulatedDeltaRangeState ?: JSONObject.NULL)
        put("automaticGainControlLevelDb", automaticGainControlLevelDb ?: JSONObject.NULL)
        put("navigationMessageCount", navigationMessageCount)
        put("navigationAssociation", navigationAssociation.name)
        put("observationMonotonicTimestampNs", observationMonotonicTimestampNs ?: JSONObject.NULL)
        put("statusMonotonicTimestampNs", statusMonotonicTimestampNs ?: JSONObject.NULL)
        put("rawSequenceNumber", rawSequenceNumber ?: JSONObject.NULL)
        put("statusSequenceNumber", statusSequenceNumber ?: JSONObject.NULL)
        put("match", match.name)
        put("provenance", provenance)
    }

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

    private fun buildSessionLog(
        session: SessionEntity,
        observations: List<ObservationEntity>,
        audit: horizon.observatory.domain.model.IntegrityAuditResult
    ): String = buildString {
        appendLine("HORIZON SESSION LOG")
        appendLine("sessionId=${session.sessionId}")
        appendLine("lifecycle=${session.lifecycleState}")
        appendLine("device=${session.deviceManufacturer} ${session.deviceModel}")
        appendLine("android=${session.androidVersion} api=${session.sdkVersion}")
        appendLine("startUtcMs=${session.startTimeMs}")
        appendLine("endUtcMs=${session.endTimeMs ?: "UNSET"}")
        appendLine("observationCount=${observations.size}")
        appendLine("integrityClean=${audit.isClean}")
        appendLine("sequenceContiguous=${audit.sequenceContiguous}")
        appendLine("ingestionTimestampsNonDecreasing=${audit.ingestionTimestampsNonDecreasing}")
        appendLine("ingestionTimestampedObservationCount=${audit.ingestionTimestampedObservationCount}")
        appendLine("sourceTimestampRegressions=${audit.sourceTimestampRegressions}")
        appendLine("sourceTimestampedObservationCount=${audit.sourceTimestampedObservationCount}")
        appendLine()
        observations.filter { it.type == "SYSTEM_EVENT" || it.type == "ERROR_EVENT" }
            .sortedBy { it.sequenceNumber }
            .forEach { row ->
                appendLine("[seq=${row.sequenceNumber}] [${row.timestampUtcMs}] [${row.type}] [${row.provider}] ${row.rawPayloadJson}")
            }
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
        private const val CSV_HEADER =
            "observationId,sessionId,sequenceNumber,timestampUtcMs,sourceMonotonicTimestampNs,ingestionMonotonicTimestampNs,source,technology,type,provider,timestampDomain,timestampUncertaintyNs,capabilityState,evidenceStatus,provenance"
        private const val SATELLITE_CSV_HEADER = "constellationType,svid,carrierFrequencyHz,cn0DbHz,basebandCn0DbHz,elevationDegrees,azimuthDegrees,usedInFix,automaticGainControlLevelDb,navigationMessageCount,navigationAssociation,rawSequenceNumber,statusSequenceNumber,match,observationMonotonicTimestampNs,statusMonotonicTimestampNs"
        private const val EVENT_CSV_HEADER =
            "observationId,sessionId,sequenceNumber,timestampUtcMs,sourceMonotonicTimestampNs,ingestionMonotonicTimestampNs,type,provider,capabilityState,evidenceStatus,rawPayloadJson"
    }
}
