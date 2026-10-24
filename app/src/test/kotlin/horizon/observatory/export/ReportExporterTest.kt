package horizon.observatory.export

import horizon.observatory.analysis.SessionAnalysisEngine
import horizon.observatory.analysis.integrity.GnssIntegrityIndicators
import horizon.observatory.astronomy.identity.GnssSatelliteId
import horizon.observatory.astronomy.identity.HintStatus
import horizon.observatory.astronomy.identity.IdentityHint
import horizon.observatory.astronomy.identity.IdentityHintResult
import horizon.observatory.astronomy.identity.IdentityTableCsv
import horizon.observatory.astronomy.identity.MappingConfidence
import horizon.observatory.astronomy.identity.MappingMethod
import horizon.observatory.storage.entity.ObservationEntity
import horizon.observatory.storage.entity.SessionEntity
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportExporterTest {
    private fun obs(seq: Long, type: String, source: String = "GNSS", raw: String = "{}") = ObservationEntity(
        sessionId = "S1", sequenceNumber = seq, timestampUtcMs = 1_760_000_000_000L + seq, type = type,
        provider = "TEST", rawPayloadJson = raw, source = source
    )

    private fun input(rows: List<ObservationEntity> = emptyList()) = ReportInput(
        session = SessionEntity(sessionId = "S1", startTimeMs = 1_760_000_000_000L),
        observations = rows,
        analysis = SessionAnalysisEngine().analyze(emptyList()),
        capabilityJson = "{}",
        antennaRecords = emptyList(),
        generatedAtUtcMs = 1_760_000_100_000L
    )

    @Test fun csvCellEscapesAndNeutralisesFormulas() {
        assertEquals("plain", ReportExporter.csvCell("plain"))
        assertEquals("\"a,b\"", ReportExporter.csvCell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", ReportExporter.csvCell("say \"hi\""))
        assertEquals("'=SUM(A1)", ReportExporter.csvCell("=SUM(A1)"))
        assertEquals("'@cmd", ReportExporter.csvCell("@cmd"))
        assertEquals("-85.5", ReportExporter.csvCell("-85.5"))   // negative numbers stay numbers
        assertEquals("", ReportExporter.csvCell(null))
    }

    @Test fun missingValuesAreNotAvailableNeverZero() {
        val md = ReportExporter.markdown(ReportSection.GNSS, input())
        assertTrue(md.contains(ReportExporter.NA))
        assertFalse(md.contains("Avg C/N0 | 0"))
        assertTrue(md.contains("No predicted values"))
    }

    @Test fun gnssReportExportsAgcSourceAlongsideSummaryValues() {
        val rows = listOf(
            obs(1, "GNSS_CLOCK", raw = """{"eventElapsedRealtimeNanos":1000000,"eventExtras":{"agcEvents":[{"levelDb":17.5,"constellationType":1,"carrierFrequencyHz":1575420000.0}]}}""")
        )
        val analysis = SessionAnalysisEngine().analyze(rows)
        val markdown = ReportExporter.markdown(ReportSection.GNSS, input(rows).copy(analysis = analysis))
        assertTrue(markdown.contains("| AGC source | EVENT_LEVEL |"))
        assertTrue(markdown.contains("| AGC state | EXACTLY_CONSTANT |"))
        assertTrue(markdown.contains("| AGC min | 17.50 dB |"))
    }

    @Test fun timelineCsvHasOneRowPerObservationInSequenceOrder() {
        val rows = listOf(obs(2, "GNSS_FIX"), obs(1, "SYSTEM_EVENT", source = "APP"))
        val csv = ReportExporter.csvFiles(ReportSection.TIMELINE, input(rows)).getValue("timeline.csv").trim().lines()
        assertEquals(3, csv.size)
        assertTrue(csv[1].startsWith("1,"))
        assertTrue(csv[2].startsWith("2,"))
    }

    @Test fun bundleContainsEverySectionAndValidChecksums() {
        val f = File.createTempFile("report", ".zip").also { it.delete() }
        ReportExporter.buildZip(f, ReportSection.values().toList(), input(listOf(obs(1, "CELLULAR_INFO", source = "CELLULAR"))))
        ZipFile(f).use { z ->
            ReportSection.values().forEach { assertTrue("missing ${it.slug}", z.getEntry("${it.slug}/report.md") != null) }
            assertTrue(z.getEntry("README.md") != null && z.getEntry("report_manifest.json") != null)
            val sums = z.getInputStream(z.getEntry("checksums.sha256")).bufferedReader().readLines().filter { it.isNotBlank() }
            assertTrue(sums.isNotEmpty())
            sums.forEach { line ->
                val (hash, name) = line.split("  ", limit = 2)
                val bytes = z.getInputStream(z.getEntry(name)).readBytes()
                assertEquals(name, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, hash)
            }
        }
        f.delete()
    }

    @Test fun predictedSectionIsLabelledAndNeverFabricated() {
        val md = ReportExporter.markdown(ReportSection.PREDICTED, input())
        assertTrue(md.contains("DERIVED_PREDICTED"))
        assertTrue(md.contains(ReportExporter.NA))
        val csvs = ReportExporter.csvFiles(ReportSection.PREDICTED, input())
        assertEquals(setOf("ground_tracks.csv", "passes.csv"), csvs.keys)
        csvs.values.forEach { assertEquals(1, it.trim().lines().size) } // header only: nothing invented
    }

    @Test fun pruneKeepsNewestReportsAndIgnoresOtherFiles() {
        val dir = java.nio.file.Files.createTempDirectory("reports").toFile()
        (1..12).forEach { n -> File(dir, "horizon_report_all_$n.zip").also { it.writeText("x"); it.setLastModified(1_000_000L + n * 1000L) } }
        val other = File(dir, "notes.txt").also { it.writeText("keep me") }
        assertEquals(2, ReportExporter.pruneOldReports(dir, keep = 10))
        assertFalse(File(dir, "horizon_report_all_1.zip").exists())
        assertFalse(File(dir, "horizon_report_all_2.zip").exists())
        assertTrue(File(dir, "horizon_report_all_12.zip").exists())
        assertTrue(other.exists())
        dir.deleteRecursively()
    }

    @Test fun identityHintsExportRoundTripsThroughTheImporterAsUnverified() {
        val hints = IdentityHintResult(
            listOf(
                IdentityHint(GnssSatelliteId(1, 13), HintStatus.CONSISTENT, 99001L, "TEST, SAT", 0.5, 20.0, "unique mutual best match"),
                IdentityHint(GnssSatelliteId(6, 5), HintStatus.AMBIGUOUS, 99002L, null, 1.0, 2.0, "x")
            ), 40, 100, 1_760_000_000_000L, 3.0, 4.0, null
        )
        val inp = input().copy(identityHints = hints)
        val parsed = IdentityTableCsv.parse(ReportExporter.csvFiles(ReportSection.IDENTITY_HINTS, inp).getValue("suggested_identity_rows.csv"))
        assertTrue(parsed.issues.toString(), parsed.issues.isEmpty())
        assertEquals(1, parsed.entries.size)
        assertEquals(MappingConfidence.UNVERIFIED, parsed.entries[0].provenance.confidence)
        assertEquals(MappingMethod.GEOMETRIC_CONSISTENCY_HINT, parsed.entries[0].provenance.method)
        assertTrue(ReportExporter.markdown(ReportSection.IDENTITY_HINTS, inp).contains("HINT ONLY"))
        assertTrue(ReportExporter.markdown(ReportSection.IDENTITY_HINTS, input()).contains(ReportExporter.NA))
    }

    @Test fun integritySectionIsLabelledAsNotAVerdictAndNeverFabricated() {
        val none = ReportExporter.markdown(ReportSection.INTEGRITY, input())
        assertTrue(none.contains("NOT A VERDICT"))
        assertTrue(none.contains(ReportExporter.NA))
        val inp = input().copy(integrity = GnssIntegrityIndicators.evaluate(emptyList(), emptyList(), emptyList()))
        val md = ReportExporter.markdown(ReportSection.INTEGRITY, inp)
        assertTrue(md.contains("UNAVAILABLE"))
        assertTrue(ReportExporter.csvFiles(ReportSection.INTEGRITY, inp).containsKey("indicator_metrics.csv"))
    }
}
