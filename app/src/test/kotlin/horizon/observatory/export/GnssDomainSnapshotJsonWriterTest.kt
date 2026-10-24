package horizon.observatory.export

import horizon.observatory.domain.gnss.ConstellationSummary
import horizon.observatory.domain.gnss.GnssAntennaEvidence
import horizon.observatory.domain.gnss.GnssDomainSnapshot
import horizon.observatory.domain.gnss.GnssSignalStatistics
import horizon.observatory.domain.gnss.NavigationAssociation
import horizon.observatory.domain.gnss.SatelliteCorrelationKey
import horizon.observatory.domain.gnss.SatelliteEvidence
import horizon.observatory.domain.gnss.SatelliteEvidenceMatch
import horizon.observatory.domain.gnss.SatelliteHistoryPoint
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class GnssDomainSnapshotJsonWriterTest {
    @Test
    fun writesRoundTrippableSnapshotAndAntennaArrays() = runBlocking {
        val file = File.createTempFile("gnss-domain-", ".json")
        val antennaFile = File.createTempFile("gnss-antenna-", ".json")
        val snapshot = sampleSnapshot(
            satelliteEvidence = listOf(sampleSatellite(1)),
            historyPoints = listOf(sampleHistory(1))
        )
        try {
            GnssDomainSnapshotJsonWriter.write(file, snapshot)
            val json = JSONObject(file.readText())
            assertEquals(1, json.getInt("sampleCount"))
            assertEquals("DERIVED_FROM_PERSISTED_GNSS_OBSERVATIONS", json.getString("provenance"))
            assertEquals(1, json.getJSONArray("satelliteEvidence").length())
            assertEquals(1, json.getJSONArray("latestSatelliteEvidence").length())
            assertEquals(1, json.getJSONArray("historyPoints").length())
            assertEquals(1, json.getJSONArray("constellationSummaries").length())
            assertEquals(1, json.getJSONArray("antennaEvidence").length())
            assertEquals(1575420000.0, json.getJSONArray("distinctFrequenciesHz").getDouble(0), 0.0)

            val antennaCount = GnssDomainSnapshotJsonWriter.writeAntennaEvidence(antennaFile, snapshot.antennaEvidence)
            assertEquals(snapshot.antennaEvidence.size, antennaCount)
            assertEquals(1, org.json.JSONArray(antennaFile.readText()).length())
        } finally {
            file.delete()
            antennaFile.delete()
        }
    }

    @Test
    fun streamsLargeEvidenceAndHistoryArraysWithoutCombinedJsonString() = runBlocking {
        val count = 12_000
        val file = File.createTempFile("gnss-domain-large-", ".json")
        try {
            val evidence = List(count) { sampleSatellite(it + 1) }
            val history = List(count) { sampleHistory(it + 1) }
            val snapshot = sampleSnapshot(evidence, history)

            GnssDomainSnapshotJsonWriter.write(file, snapshot)

            assertTrue("Expected a multi-megabyte snapshot file", file.length() > 1_000_000L)
            assertEquals('{'.code, file.inputStream().use { it.read() })
            RandomAccessFile(file, "r").use { input ->
                input.seek(file.length() - 2L)
                assertEquals('}'.code, input.read())
            }
        } finally {
            file.delete()
        }
    }

    private fun sampleSnapshot(
        satelliteEvidence: List<SatelliteEvidence>,
        historyPoints: List<SatelliteHistoryPoint>
    ): GnssDomainSnapshot = GnssDomainSnapshot(
        signalStatistics = GnssSignalStatistics(
            sampleCount = satelliteEvidence.size,
            averageCn0DbHz = 42.0,
            minCn0DbHz = 30.0,
            maxCn0DbHz = 50.0,
            averageElevationDegrees = 35.0,
            minElevationDegrees = 10.0,
            maxElevationDegrees = 80.0,
            averageAgcDb = null,
            minAgcDb = null,
            maxAgcDb = null,
            distinctFrequenciesHz = listOf(1575420000.0)
        ),
        satelliteEvidence = satelliteEvidence,
        latestSatelliteEvidence = satelliteEvidence.takeLast(1),
        historyPoints = historyPoints,
        constellationSummaries = listOf(
            ConstellationSummary(
                constellationType = 1,
                evidenceCount = satelliteEvidence.size,
                distinctSatelliteCount = satelliteEvidence.map { it.svid }.distinct().size,
                matchedCount = satelliteEvidence.size,
                statusOnlyCount = 0,
                rawOnlyCount = 0,
                navigationMessageCount = 0,
                averageCn0DbHz = 42.0,
                averageElevationDegrees = 35.0,
                averageAgcDb = null
            )
        ),
        antennaEvidence = listOf(
            GnssAntennaEvidence(
                carrierFrequencyMHz = 1575.42,
                phaseCenterOffset = null,
                phaseCenterVariationCorrections = null,
                signalGainCorrections = null,
                monotonicTimestampNs = 1L,
                capabilityState = "TEST_FIXTURE",
                provenance = "SYNTHETIC_TEST_ONLY"
            )
        ),
        navigationMessageCount = 0,
        correlatedMeasurementCount = satelliteEvidence.size,
        statusOnlySatelliteCount = 0,
        rawOnlySatelliteCount = 0
    )

    private fun sampleSatellite(sequence: Int) = SatelliteEvidence(
        key = SatelliteCorrelationKey(1, (sequence % 32) + 1, 1575420000L),
        constellationType = 1,
        svid = (sequence % 32) + 1,
        carrierFrequencyHz = 1575420000.0,
        cn0DbHz = 42.0,
        basebandCn0DbHz = null,
        elevationDegrees = 35.0,
        azimuthDegrees = 120.0,
        usedInFix = true,
        measurementState = 4,
        multipathIndicator = 0,
        accumulatedDeltaRangeMeters = 10.0 + sequence,
        accumulatedDeltaRangeState = 1,
        automaticGainControlLevelDb = null,
        navigationMessageCount = 0,
        navigationAssociation = NavigationAssociation.NOT_ASSOCIATED,
        lastNavigationMessageMonotonicTimestampNs = null,
        observationMonotonicTimestampNs = sequence * 1_000_000L,
        statusMonotonicTimestampNs = sequence * 1_000_000L,
        rawSequenceNumber = sequence.toLong(),
        statusSequenceNumber = sequence.toLong(),
        match = SatelliteEvidenceMatch.STATUS_AND_RAW_MATCH,
        provenance = "SYNTHETIC_TEST_ONLY"
    )

    private fun sampleHistory(sequence: Int) = SatelliteHistoryPoint(
        sequenceNumber = sequence.toLong(),
        constellationType = 1,
        svid = (sequence % 32) + 1,
        carrierFrequencyHz = 1575420000.0,
        cn0DbHz = 42.0,
        basebandCn0DbHz = null,
        elevationDegrees = 35.0,
        azimuthDegrees = 120.0,
        usedInFix = true,
        agcDb = null,
        navigationMessageCount = 0,
        match = SatelliteEvidenceMatch.STATUS_AND_RAW_MATCH,
        monotonicTimestampNs = sequence * 1_000_000L
    )
}
