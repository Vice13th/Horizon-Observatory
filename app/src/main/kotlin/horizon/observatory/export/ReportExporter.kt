package horizon.observatory.export

import horizon.observatory.analysis.AnalysisSnapshot
import horizon.observatory.analysis.integrity.IntegrityReport
import horizon.observatory.astronomy.identity.HintStatus
import horizon.observatory.astronomy.identity.IdentityHintResult
import horizon.observatory.astronomy.identity.IdentityTableCsv
import horizon.observatory.live.SkyDomeStyle
import horizon.observatory.storage.entity.ObservationEntity
import horizon.observatory.storage.entity.SessionEntity
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One exportable report per app section. Order = order in the bundle. */
enum class ReportSection(val slug: String, val title: String) {
    OVERVIEW("overview", "Overview"),
    GNSS("gnss", "GNSS"),
    ANTENNA("antenna", "Antenna"),
    CELLULAR("cellular", "Cellular"),
    SENSORS("sensors", "Sensors"),
    TIMELINE("timeline", "Timeline"),
    SESSION("session", "Session"),
    LOGS("logs", "Logs"),
    DIAGNOSTICS("diagnostics", "Diagnostics"),
    PREDICTED("predicted", "Predicted"),
    IDENTITY_HINTS("identity_hints", "Identity hints"),
    INTEGRITY("integrity", "Integrity indicators")
}

/** Everything a report needs, already loaded (this class does no I/O except writing the ZIP). */
data class ReportInput(
    val session: SessionEntity?,
    val observations: List<ObservationEntity>,
    val analysis: AnalysisSnapshot,
    val capabilityJson: String,
    val antennaRecords: List<String>,
    val generatedAtUtcMs: Long,
    val predicted: PredictedReportData? = null,
    val identityHints: IdentityHintResult? = null,
    val integrity: IntegrityReport? = null
)

/**
 * Human-readable (Markdown) + machine-readable (CSV) reports for every section, packaged as a
 * ZIP with a SHA-256 manifest. Rules: missing data is written "NOT AVAILABLE" (never 0); observed
 * evidence is never mixed with predictions; CSV cells that could be read as spreadsheet formulas
 * are neutralised; nothing is invented or smoothed.
 */
object ReportExporter {
    const val NA = "NOT AVAILABLE"
    private val NUMERIC = Regex("^-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?$")

    internal fun csvCell(value: String?): String {
        if (value == null) return ""
        var v = value
        if (v.isNotEmpty() && !NUMERIC.matches(v) && v[0] in "=+-@\t\r") v = "'$v"
        return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    private fun csv(header: List<String>, rows: List<List<Any?>>): String =
        (listOf(header.joinToString(",")) + rows.map { r -> r.joinToString(",") { csvCell(it?.toString()) } })
            .joinToString("\r\n", postfix = "\r\n")

    private fun d(v: Double?, digits: Int = 2, unit: String = ""): String =
        v?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.${digits}f", it) + unit } ?: NA
    private fun n(v: Number?): String = v?.toString() ?: NA
    private fun iso(ms: Long?): String = ms?.let { Instant.ofEpochMilli(it).toString() } ?: NA
    private fun md(s: String?): String = (s ?: NA).replace("|", "\\|").replace("\n", " ")

    private fun header(section: ReportSection, i: ReportInput): String = buildString {
        appendLine("# Horizon Observatory — ${section.title} report")
        appendLine()
        appendLine("- Generated (UTC): ${iso(i.generatedAtUtcMs)}")
        appendLine("- Session: ${i.session?.sessionId ?: "NONE"} (${i.session?.lifecycleState ?: "NO SESSION"})")
        appendLine("- Scope: ${i.observations.size} observations (full stored session, not the on-screen live window)")
        appendLine(
            if (section == ReportSection.PREDICTED) "- Evidence class: DERIVED_PREDICTED. Never mixed with observed evidence."
            else if (section == ReportSection.INTEGRITY) "- Evidence class: DERIVED-FROM-OBSERVED diagnostics (indicators, not verdicts)."
            else if (section == ReportSection.IDENTITY_HINTS) "- Evidence class: HINT ONLY (derived from observed az/el + predictions). Not an identification."
            else "- Evidence class: OBSERVED / DERIVED-FROM-OBSERVED. No predicted values in this report."
        )
        appendLine()
    }

    fun markdown(section: ReportSection, i: ReportInput): String {
        val a = i.analysis
        val sb = StringBuilder(header(section, i))
        when (section) {
            ReportSection.OVERVIEW -> {
                sb.appendLine("| Metric | Value |\n|---|---|")
                listOf(
                    "Observations" to n(a.observationCount), "GNSS raw" to n(a.gnssRawCount),
                    "GNSS status" to n(a.gnssStatusCount), "GNSS fixes" to n(a.gnssFixCount),
                    "Cellular" to n(a.cellularCount), "Sensors" to n(a.sensorCount),
                    "Avg C/N0" to d(a.averageCn0DbHz, 1, " dB-Hz"), "Avg RSRP" to d(a.averageRsrpDbm, 1, " dBm"),
                    "Avg visible satellites" to d(a.averageVisibleSatellites, 1),
                    "Sequence gaps" to n(a.sequenceGapCount), "Timestamp monotonic violations" to n(a.timestampMonotonicViolations)
                ).forEach { sb.appendLine("| ${it.first} | ${md(it.second)} |") }
            }
            ReportSection.GNSS -> {
                sb.appendLine("| Metric | Value |\n|---|---|")
                listOf(
                    "Raw measurements" to n(a.gnssRawCount), "Status epochs" to n(a.gnssStatusCount), "Fixes" to n(a.gnssFixCount),
                    "Navigation messages" to n(a.navigationMessageCount), "Avg C/N0" to d(a.averageCn0DbHz, 1, " dB-Hz"),
                    "Latest satellite count" to n(a.latestSatelliteCount), "Distinct satellites" to n(a.distinctSatelliteCount),
                    "Multi-satellite epochs" to n(a.multiSatelliteEpochs),
                    "AGC source" to a.agcSource, "AGC state" to a.agcState,
                    "AGC min" to d(a.agcMinDb, 2, " dB"), "AGC max" to d(a.agcMaxDb, 2, " dB"),
                    "AGC spread" to d(a.agcSpreadDb, 2, " dB"), "AGC distinct levels" to n(a.agcDistinctLevels),
                    "Correlated measurements" to n(a.gnssDomain.correlatedMeasurementCount),
                    "Status-only satellites" to n(a.gnssDomain.statusOnlySatelliteCount),
                    "Raw-only satellites" to n(a.gnssDomain.rawOnlySatelliteCount)
                ).forEach { sb.appendLine("| ${it.first} | ${md(it.second)} |") }
                sb.appendLine("\n## Latest satellite evidence\n")
                sb.appendLine("| Sat | C/N0 dB-Hz | Elev ° | Azim ° | Used in fix |\n|---|---|---|---|---|")
                a.gnssDomain.latestSatelliteEvidence.sortedBy { it.constellationType * 1000 + it.svid }.forEach {
                    sb.appendLine(
                        "| ${SkyDomeStyle.label(it.constellationType, it.svid)} | ${d(it.cn0DbHz, 1)} | ${d(it.elevationDegrees, 1)} | " +
                            "${d(it.azimuthDegrees, 1)} | ${it.usedInFix?.toString() ?: NA} |"
                    )
                }
                sb.appendLine("\nFull table: `gnss_satellite_evidence.csv`.")
            }
            ReportSection.ANTENNA -> {
                sb.appendLine("Antenna evidence records: ${i.antennaRecords.size}\n")
                if (i.antennaRecords.isEmpty()) sb.appendLine(NA)
                i.antennaRecords.forEachIndexed { idx, j -> sb.appendLine("### Record ${idx + 1}\n\n```json\n$j\n```\n") }
            }
            ReportSection.CELLULAR -> {
                sb.appendLine("| Metric | Value |\n|---|---|")
                listOf(
                    "Cellular observations" to n(a.cellularCount), "Avg RSRP" to d(a.averageRsrpDbm, 1, " dBm"),
                    "Serving RSRP (latest)" to d(a.latestServingRsrpDbm, 1, " dBm"),
                    "Serving technology" to (a.latestServingTechnology ?: NA),
                    "Latest cell age" to (a.latestCellAgeMs?.let { "$it ms" } ?: NA),
                    "Fresh-request samples" to n(a.freshCellRequestSamples), "Callback samples" to n(a.cellularCallbackSamples)
                ).forEach { sb.appendLine("| ${it.first} | ${md(it.second)} |") }
                sb.appendLine("\nRows: `cellular_observations.csv`.")
            }
            ReportSection.SENSORS -> {
                sb.appendLine("- Sensor observations: ${n(a.sensorCount)}")
                sb.appendLine("- Estimated magnitude: ${d(a.estimatedSensorMagnitude, 3)}")
                sb.appendLine("\nRows: `sensor_observations.csv`.")
            }
            ReportSection.TIMELINE -> sb.appendLine("Unified ordered timeline: `timeline.csv` (${i.observations.size} rows; sequence order, source and ingestion clocks kept separate).")
            ReportSection.SESSION -> {
                val s = i.session
                sb.appendLine("| Field | Value |\n|---|---|")
                listOf(
                    "Session id" to s?.sessionId, "State" to s?.lifecycleState, "Start (UTC)" to iso(s?.startTimeMs),
                    "End (UTC)" to iso(s?.endTimeMs), "Device" to s?.let { "${it.deviceManufacturer} ${it.deviceModel}" },
                    "Android" to s?.let { "${it.androidVersion} (SDK ${it.sdkVersion})" },
                    "App version" to s?.applicationVersion, "Schema" to s?.schemaVersion?.toString()
                ).forEach { sb.appendLine("| ${it.first} | ${md(it.second)} |") }
            }
            ReportSection.LOGS -> {
                val ev = i.observations.count { it.type == "SYSTEM_EVENT" }
                val er = i.observations.count { it.type == "ERROR_EVENT" }
                sb.appendLine("- System events: $ev\n- Error events: $er\n\nRows: `logs.csv`.")
            }
            ReportSection.PREDICTED -> {
                sb.appendLine("> **DERIVED_PREDICTED** — SGP4/SDP4 from catalog elements, NOT receiver-measured. Geometry only (no refraction/obstruction). Limits: OMM mean-element error (~1–3 km/day), GMST-only frame; see docs/architecture/SCIENTIFIC_METHODS.md.\n")
                val p = i.predicted
                when {
                    p == null -> sb.appendLine("$NA — predicted data was not generated for this export.")
                    p.unavailableReason != null -> sb.appendLine("$NA — ${p.unavailableReason}")
                    else -> {
                        val t = p.tracks
                        val ps = p.passes
                        sb.appendLine("- Satellites with ground tracks: ${t?.tracks?.size ?: 0}/${t?.requested ?: 0}")
                        sb.appendLine("- Prediction status now: ${t?.statusNow?.entries?.joinToString { "${it.key}×${it.value}" } ?: NA}")
                        sb.appendLine("- Satellites with passes (next 12 h, mask ${ps?.maskDeg?.toInt() ?: 10}°): ${ps?.satellites?.size ?: 0}")
                        sb.appendLine("\nTables: `ground_tracks.csv`, `passes.csv`.")
                    }
                }
            }
            ReportSection.IDENTITY_HINTS -> {
                sb.appendLine("> **HINT ONLY — NOT AN IDENTIFICATION.** Geometric consistency between the receiver's az/el and SGP4/SDP4 predictions of every stored catalog object. The app never uses these rows for prediction; to use one, re-enter it as `OPERATOR_SUPPLIED_TABLE` with your own evidence and import it.\n")
                val h = i.identityHints
                when {
                    h == null -> sb.appendLine("$NA — identity hints were not generated for this export.")
                    h.unavailableReason != null -> sb.appendLine("$NA — ${h.unavailableReason}")
                    else -> {
                        sb.appendLine("- Epoch (UTC): ${iso(h.atUtcMs)}")
                        sb.appendLine("- Catalog objects: ${h.catalogSize}; candidates above horizon: ${h.candidateCount}")
                        sb.appendLine("- Thresholds (provisional, uncalibrated): best separation ≤ ${h.maxSeparationDeg}°, runner-up margin ≥ ${h.minMarginDeg}°, mutual best")
                        sb.appendLine("- CONSISTENT: ${h.hints.count { it.status == HintStatus.CONSISTENT }} · AMBIGUOUS: ${h.hints.count { it.status == HintStatus.AMBIGUOUS }} · NO_CANDIDATE: ${h.hints.count { it.status == HintStatus.NO_CANDIDATE }}")
                        sb.appendLine("\n| Sat | Status | NORAD | Catalog name (display only) | Sep ° | Runner-up ° | Reason |\n|---|---|---|---|---|---|---|")
                        h.hints.sortedBy { it.id.constellationType * 1000 + it.id.svid }.forEach {
                            sb.appendLine(
                                "| ${SkyDomeStyle.label(it.id.constellationType, it.id.svid)} | ${it.status} | ${n(it.noradCatId)} | ${md(it.displayName)} | " +
                                    "${d(it.separationDeg, 2)} | ${d(it.runnerUpSeparationDeg, 2)} | ${md(it.reason)} |"
                            )
                        }
                        sb.appendLine("\nImportable rows (CONSISTENT only, confidence UNVERIFIED): `suggested_identity_rows.csv`. Limitations: single epoch; receiver az/el may come from almanac and be several degrees off; chance alignment is possible; thresholds are uncalibrated.")
                    }
                }
            }
            ReportSection.INTEGRITY -> {
                sb.appendLine("> **DIAGNOSTIC INDICATORS — NOT A VERDICT.** Robust deviations from this session's own baseline, computed from observed raw GNSS evidence. They cannot establish integrity (best outcome: NOT_ESTABLISHED), are not independent of each other, make no attack attribution and use uncalibrated default thresholds.\n")
                val r = i.integrity
                if (r == null) {
                    sb.appendLine("$NA — integrity indicators were not generated for this export.")
                } else {
                    sb.appendLine("- Assessment: **${r.assessment}**")
                    sb.appendLine("- Reason codes: ${if (r.reasonCodes.isEmpty()) "none" else r.reasonCodes.joinToString(", ")}")
                    sb.appendLine("- Profile: `${r.profileId}` · epochs: ${r.epochCount} · span: ${d(r.timeSpanSeconds, 0, " s")}")
                    sb.appendLine("\n| Indicator | Status | Reason codes | Summary |\n|---|---|---|---|")
                    r.indicators.forEach { ind ->
                        sb.appendLine("| ${md(ind.title)} | ${ind.status} | ${md(ind.reasonCodes.joinToString(", "))} | ${md(ind.summary)} |")
                    }
                    r.indicators.forEach { ind ->
                        sb.appendLine("\n### ${md(ind.title)}\n")
                        sb.appendLine("Possible benign causes: ${ind.confounders.joinToString("; ")}")
                        if (ind.metrics.isNotEmpty()) {
                            sb.appendLine("\n| Metric | Value | Unit | Samples | Window (epochs) |\n|---|---|---|---|---|")
                            ind.metrics.forEach { m ->
                                sb.appendLine("| ${m.name} | ${d(m.value, 3)} | ${md(m.unit)} | ${m.sampleCount} | ${d(m.windowSeconds, 0)} |")
                            }
                        }
                    }
                    sb.appendLine("\n## Notes\n")
                    r.notes.forEach { sb.appendLine("- ${md(it)}") }
                    sb.appendLine("\nTable: `indicator_metrics.csv`.")
                }
            }
            ReportSection.DIAGNOSTICS -> {
                sb.appendLine("- Sequence gaps: ${n(a.sequenceGapCount)}")
                sb.appendLine("- Timestamp monotonic violations: ${n(a.timestampMonotonicViolations)} (diagnostic, not a failure by itself)")
                sb.appendLine("\n## Capability report\n\n```json\n${i.capabilityJson}\n```")
            }
        }
        return sb.toString()
    }

    private fun obsRows(list: List<ObservationEntity>) = list.map {
        listOf(
            it.sequenceNumber, it.timestampUtcMs, iso(it.timestampUtcMs), it.monotonicTimestampNs, it.ingestionMonotonicTimestampNs,
            it.timestampDomain, it.source, it.technology, it.type, it.provider, it.evidenceStatus, it.provenance,
            it.rawPayloadJson, it.normalizedPayloadJson
        )
    }
    private val OBS_HEADER = listOf(
        "sequence", "timestamp_utc_ms", "timestamp_iso", "monotonic_ns", "ingestion_monotonic_ns", "timestamp_domain",
        "source", "technology", "type", "provider", "evidence_status", "provenance", "raw_payload_json", "normalized_payload_json"
    )

    /** filename → content for the section's tabular data (may be empty). */
    fun csvFiles(section: ReportSection, i: ReportInput): Map<String, String> = when (section) {
        ReportSection.GNSS -> mapOf(
            "gnss_satellite_evidence.csv" to csv(
                listOf("constellation", "svid", "label", "carrier_frequency_hz", "cn0_dbhz", "baseband_cn0_dbhz", "elevation_deg", "azimuth_deg", "used_in_fix", "measurement_state", "multipath_indicator", "adr_m"),
                i.analysis.gnssDomain.latestSatelliteEvidence.map {
                    listOf(it.constellationType, it.svid, SkyDomeStyle.label(it.constellationType, it.svid), it.carrierFrequencyHz, it.cn0DbHz,
                        it.basebandCn0DbHz, it.elevationDegrees, it.azimuthDegrees, it.usedInFix, it.measurementState, it.multipathIndicator,
                        it.accumulatedDeltaRangeMeters)
                }
            ),
            "gnss_observations.csv" to csv(OBS_HEADER, obsRows(i.observations.filter { it.type.startsWith("GNSS") }))
        )
        ReportSection.CELLULAR -> mapOf("cellular_observations.csv" to csv(OBS_HEADER, obsRows(i.observations.filter { it.type == "CELLULAR_INFO" })))
        ReportSection.SENSORS -> mapOf("sensor_observations.csv" to csv(OBS_HEADER, obsRows(i.observations.filter { it.source == "SENSOR" })))
        ReportSection.TIMELINE -> mapOf("timeline.csv" to csv(OBS_HEADER, obsRows(i.observations.sortedBy { it.sequenceNumber })))
        ReportSection.LOGS -> mapOf("logs.csv" to csv(OBS_HEADER, obsRows(i.observations.filter { it.type == "SYSTEM_EVENT" || it.type == "ERROR_EVENT" })))
        ReportSection.PREDICTED -> {
            val p = i.predicted
            mapOf(
                "ground_tracks.csv" to csv(
                    listOf("satellite", "offset_min", "lat_deg", "lon_deg", "alt_km"),
                    p?.tracks?.tracks.orEmpty().flatMap { t ->
                        t.points.map { listOf(SkyDomeStyle.label(t.id.constellationType, t.id.svid), it.offsetMin, it.latDeg, it.lonDeg, it.altKm) }
                    }
                ),
                "passes.csv" to csv(
                    listOf("satellite", "rise_utc", "set_utc", "culmination_utc", "max_el_deg", "rise_az_deg", "set_az_deg", "catalog_stale"),
                    p?.passes?.satellites.orEmpty().flatMap { s ->
                        s.passes.map {
                            listOf(SkyDomeStyle.label(s.id.constellationType, s.id.svid), iso(it.riseMs), iso(it.setMs), iso(it.culminationMs),
                                it.maxElDeg, it.riseAzDeg, it.setAzDeg, s.stale)
                        }
                    }
                )
            )
        }
        ReportSection.IDENTITY_HINTS -> {
            val h = i.identityHints
            val hints = h?.hints.orEmpty()
            val epoch = iso(h?.atUtcMs)
            mapOf(
                "suggested_identity_rows.csv" to csv(
                    IdentityTableCsv.COLUMNS,
                    hints.filter { it.status == HintStatus.CONSISTENT && it.noradCatId != null }.map {
                        listOf(
                            it.id.constellationType, it.id.svid, it.noradCatId, "geometric_hint@$epoch",
                            "GEOMETRIC_CONSISTENCY_HINT", "UNVERIFIED",
                            "separation ${d(it.separationDeg, 2)} deg; runner-up ${d(it.runnerUpSeparationDeg, 2)} deg; single epoch; catalog name ${it.displayName ?: NA} (display only)",
                            null, null
                        )
                    }
                ),
                "hint_details.csv" to csv(
                    listOf("constellation", "svid", "label", "status", "norad_cat_id", "catalog_name", "separation_deg", "runner_up_deg", "reason"),
                    hints.map {
                        listOf(it.id.constellationType, it.id.svid, SkyDomeStyle.label(it.id.constellationType, it.id.svid), it.status,
                            it.noradCatId, it.displayName, it.separationDeg, it.runnerUpSeparationDeg, it.reason)
                    }
                )
            )
        }
        ReportSection.INTEGRITY -> mapOf(
            "indicator_metrics.csv" to csv(
                listOf("indicator", "status", "metric", "value", "unit", "sample_count", "window", "reason_codes"),
                i.integrity?.indicators.orEmpty().flatMap { ind ->
                    ind.metrics.map { m -> listOf(ind.id, ind.status, m.name, m.value, m.unit, m.sampleCount, m.windowSeconds, ind.reasonCodes.joinToString(";")) }
                }
            )
        )
        else -> emptyMap()
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private const val README = """# Horizon Observatory report bundle

Every section folder holds `report.md` (human-readable) and, where tabular, CSV files.
`checksums.sha256` lists SHA-256 of every file (sha256sum format).

Rules: missing data is "NOT AVAILABLE", never zero. Observed evidence is separate from predictions and
predicted values appear only under `predicted/` and are labelled DERIVED_PREDICTED. Timestamps: *_utc_ms = device wall clock (UTC), monotonic_ns =
source monotonic clock, ingestion_monotonic_ns = app ingestion clock; they are never merged.
"""

    fun buildZip(target: File, sections: List<ReportSection>, i: ReportInput): File {
        val entries = LinkedHashMap<String, ByteArray>()
        entries["README.md"] = README.toByteArray()
        for (s in sections) {
            entries["${s.slug}/report.md"] = markdown(s, i).toByteArray()
            csvFiles(s, i).forEach { (name, content) -> entries["${s.slug}/$name"] = content.toByteArray() }
        }
        val manifest = buildString {
            appendLine("{")
            appendLine("  \"generator\": \"Horizon Observatory\",")
            appendLine("  \"generated_utc\": \"${iso(i.generatedAtUtcMs)}\",")
            appendLine("  \"session_id\": ${i.session?.sessionId?.let { "\"${it.replace("\"", "'")}\"" } ?: "null"},")
            appendLine("  \"observation_count\": ${i.observations.size},")
            appendLine("  \"sections\": [${sections.joinToString(",") { "\"${it.slug}\"" }}]")
            appendLine("}")
        }
        entries["report_manifest.json"] = manifest.toByteArray()
        val checksums = entries.entries.joinToString("\n", postfix = "\n") { "${sha256(it.value)}  ${it.key}" }
        val tmp = File(target.parentFile, target.name + ".tmp")
        try {
            ZipOutputStream(tmp.outputStream().buffered()).use { zip ->
                (entries + ("checksums.sha256" to checksums.toByteArray())).forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
                }
            }
            check(tmp.renameTo(target)) { "Unable to finalise report ZIP: $target" }
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
        return target
    }

    /** Keeps the newest [keep] report ZIPs in [dir] (reports can be large and contain location data); returns deleted count. */
    fun pruneOldReports(dir: File, keep: Int = 10): Int {
        val zips = dir.listFiles { f -> f.isFile && f.name.startsWith("horizon_report_") && f.name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() } ?: return 0
        return zips.drop(keep).count { it.delete() }
    }
}
