package horizon.observatory.analysis.gnss

import horizon.observatory.storage.entity.ObservationEntity
import horizon.observatory.domain.gnss.SatelliteEvidenceMatch
import horizon.observatory.domain.gnss.NavigationAssociation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssCorrelationEngineTest {
    private fun row(
        sequence: Long,
        type: String,
        monotonicNs: Long,
        payload: String
    ) = ObservationEntity(
        sessionId = "session",
        sequenceNumber = sequence,
        timestampUtcMs = sequence * 1000,
        monotonicTimestampNs = monotonicNs,
        ingestionMonotonicTimestampNs = monotonicNs,
        source = "GNSS",
        technology = "GNSS",
        type = type,
        provider = type,
        rawPayloadJson = payload
    )

    @Test
    fun joinsStatusAndRawByConstellationSvidAndTime() {
        val rows = listOf(
            row(1, "GNSS_STATUS", 1_000_000_000L, """
                {"satelliteCount":1,"satellites":[{"constellationType":1,"svid":18,"carrierFrequencyHz":1575420000,"cn0DbHz":18.0,"elevationDegrees":32.0,"azimuthDegrees":121.0,"usedInFix":true}]}
            """.trimIndent()),
            row(2, "GNSS_RAW_MEASUREMENT", 1_500_000_000L, """
                {"constellationType":1,"svid":18,"carrierFrequencyHz":1575420000,"cn0DbHz":22.5,"basebandCn0DbHz":21.0,"state":8,"multipathIndicator":0,"accumulatedDeltaRangeMeters":12.0,"accumulatedDeltaRangeState":2,"automaticGainControlLevelDb":6.0}
            """.trimIndent())
        )

        val snapshot = GnssCorrelationEngine().buildSnapshot(rows)
        assertEquals(1, snapshot.satelliteEvidence.size)
        val sat = snapshot.satelliteEvidence.single()
        assertEquals(1, sat.constellationType)
        assertEquals(18, sat.svid)
        assertEquals(22.5, sat.cn0DbHz!!, 0.001)
        assertEquals(2L, sat.rawSequenceNumber)
        assertEquals(1L, sat.statusSequenceNumber)
        assertEquals(32.0, sat.elevationDegrees!!, 0.001)
        assertEquals(121.0, sat.azimuthDegrees!!, 0.001)
        assertEquals(6.0, sat.automaticGainControlLevelDb!!, 0.001)
        assertEquals(SatelliteEvidenceMatch.STATUS_AND_RAW_MATCH, sat.match)
    }

    @Test
    fun doesNotJoinDifferentConstellationsWithSameSvid() {
        val rows = listOf(
            row(1, "GNSS_STATUS", 1_000_000_000L, """
                {"satelliteCount":2,"satellites":[
                  {"constellationType":1,"svid":5,"cn0DbHz":25.0,"elevationDegrees":40.0,"azimuthDegrees":10.0},
                  {"constellationType":3,"svid":5,"cn0DbHz":30.0,"elevationDegrees":20.0,"azimuthDegrees":90.0}
                ]}
            """.trimIndent()),
            row(2, "GNSS_RAW_MEASUREMENT", 1_200_000_000L, "{\"constellationType\":1,\"svid\":5,\"cn0DbHz\":26.0}"),
            row(3, "GNSS_RAW_MEASUREMENT", 1_300_000_000L, "{\"constellationType\":3,\"svid\":5,\"cn0DbHz\":31.0}")
        )

        val snapshot = GnssCorrelationEngine().buildSnapshot(rows)
        assertEquals(2, snapshot.satelliteEvidence.size)
        assertEquals(setOf(1, 3), snapshot.satelliteEvidence.map { it.constellationType }.toSet())
        assertEquals(setOf(26.0, 31.0), snapshot.satelliteEvidence.map { it.cn0DbHz }.toSet())
    }

    @Test
    fun preservesStatusOnlyAndRawOnlyStates() {
        val rows = listOf(
            row(1, "GNSS_STATUS", 1_000_000_000L, """
                {"satelliteCount":1,"satellites":[{"constellationType":1,"svid":7,"cn0DbHz":19.0,"elevationDegrees":10.0,"azimuthDegrees":200.0}]}
            """.trimIndent()),
            row(2, "GNSS_RAW_MEASUREMENT", 10_000_000_000L, "{\"constellationType\":3,\"svid\":29,\"cn0DbHz\":28.0}")
        )

        val snapshot = GnssCorrelationEngine(statusMatchWindowNs = 500_000_000L).buildSnapshot(rows)
        assertEquals(2, snapshot.satelliteEvidence.size)
        assertTrue(snapshot.satelliteEvidence.any { it.match == SatelliteEvidenceMatch.STATUS_ONLY })
        assertTrue(snapshot.satelliteEvidence.any { it.match == SatelliteEvidenceMatch.RAW_ONLY })
    }

    @Test
    fun navigationMessageCountIsAssociatedWithSatellite() {
        val rows = listOf(
            row(1, "GNSS_STATUS", 1_000_000_000L, """
                {"satelliteCount":1,"satellites":[{"constellationType":6,"svid":15,"cn0DbHz":27.0,"elevationDegrees":50.0,"azimuthDegrees":250.0}]}
            """.trimIndent()),
            row(2, "GNSS_RAW_MEASUREMENT", 1_100_000_000L, "{\"constellationType\":6,\"svid\":15,\"cn0DbHz\":27.0}"),
            row(3, "GNSS_NAVIGATION_MESSAGE", 1_200_000_000L, "{\"constellationType\":6,\"svid\":15,\"messageId\":1}"),
            row(4, "GNSS_NAVIGATION_MESSAGE", 1_300_000_000L, "{\"constellationType\":6,\"svid\":15,\"messageId\":2}")
        )

        val snapshot = GnssCorrelationEngine().buildSnapshot(rows)
        val sat = snapshot.satelliteEvidence.single()
        assertEquals(2, sat.navigationMessageCount)
        assertEquals(NavigationAssociation.ASSOCIATED, sat.navigationAssociation)
        assertNotNull(sat.lastNavigationMessageMonotonicTimestampNs)
    }

    @Test
    fun buildsLatestSatelliteAndConstellationSummaries() {
        val rows = listOf(
            row(1, "GNSS_STATUS", 1_000_000_000L, """
                {"satelliteCount":2,"satellites":[
                  {"constellationType":1,"svid":18,"cn0DbHz":18.0,"elevationDegrees":20.0,"azimuthDegrees":100.0},
                  {"constellationType":6,"svid":18,"cn0DbHz":24.0,"elevationDegrees":50.0,"azimuthDegrees":200.0}
                ]}
            """.trimIndent()),
            row(2, "GNSS_RAW_MEASUREMENT", 1_100_000_000L, "{\"constellationType\":1,\"svid\":18,\"cn0DbHz\":21.0}"),
            row(3, "GNSS_RAW_MEASUREMENT", 2_100_000_000L, "{\"constellationType\":1,\"svid\":18,\"cn0DbHz\":26.0}"),
            row(4, "GNSS_RAW_MEASUREMENT", 1_200_000_000L, "{\"constellationType\":6,\"svid\":18,\"cn0DbHz\":25.0}")
        )

        val snapshot = GnssCorrelationEngine().buildSnapshot(rows)
        assertEquals(2, snapshot.latestSatelliteEvidence.size)
        val gpsLatest = snapshot.latestSatelliteEvidence.first { it.constellationType == 1 && it.svid == 18 }
        assertEquals(26.0, gpsLatest.cn0DbHz!!, 0.001)
        assertEquals(2, snapshot.constellationSummaries.size)
        assertTrue(snapshot.historyPoints.size >= 3)
    }


    @Test
    fun keepsAntennaEvidenceSeparateAndComputesObservedStatistics() {
        val rows = listOf(
            row(1, "GNSS_ANTENNA_INFO", 1_000_000_000L, """
                {"carrierFrequencyMHz":1575.42,"phaseCenterOffset":"PCO","phaseCenterVariationCorrections":"PCV","signalGainCorrections":"GAIN"}
            """.trimIndent()),
            row(2, "GNSS_STATUS", 1_100_000_000L, """
                {"satelliteCount":1,"satellites":[{"constellationType":1,"svid":18,"cn0DbHz":21.0,"elevationDegrees":45.0,"azimuthDegrees":90.0}]}
            """.trimIndent()),
            row(3, "GNSS_RAW_MEASUREMENT", 1_200_000_000L, "{\"constellationType\":1,\"svid\":18,\"cn0DbHz\":23.0,\"carrierFrequencyHz\":1575420000,\"automaticGainControlLevelDb\":6.0}")
        )
        val snapshot = GnssCorrelationEngine().buildSnapshot(rows)
        assertEquals(1, snapshot.antennaEvidence.size)
        assertEquals(1, snapshot.signalStatistics.sampleCount)
        assertEquals(23.0, snapshot.signalStatistics.averageCn0DbHz!!, 0.001)
        assertEquals(1, snapshot.signalStatistics.distinctFrequenciesHz.size)
    }

}
