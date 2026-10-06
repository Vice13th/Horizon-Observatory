package horizon.observatory.analysis

import horizon.observatory.storage.entity.ObservationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAnalysisEngineTest {
    private fun row(
        sequence: Long,
        type: String,
        technology: String = "UNKNOWN",
        payload: String
    ) = ObservationEntity(
        sessionId = "test",
        sequenceNumber = sequence,
        timestampUtcMs = sequence * 1000,
        monotonicTimestampNs = sequence * 1_000_000,
        ingestionMonotonicTimestampNs = sequence * 1_000_000,
        source = when {
            type.startsWith("GNSS") -> "GNSS"
            type == "CELLULAR_INFO" -> "CELLULAR"
            else -> "SYSTEM"
        },
        technology = technology,
        type = type,
        provider = "TEST",
        rawPayloadJson = payload
    )

    @Test
    fun exactConstantAgcIsReportedWithoutInventingVariation() {
        val rows = listOf(
            row(1, "GNSS_RAW_MEASUREMENT", payload = "{\"cn0DbHz\":31.0,\"automaticGainControlLevelDb\":-6.5}"),
            row(2, "GNSS_RAW_MEASUREMENT", payload = "{\"cn0DbHz\":32.0,\"automaticGainControlLevelDb\":-6.5}"),
            row(3, "GNSS_NAVIGATION_MESSAGE", payload = "{\"svid\":5,\"dataLength\":40}"),
            row(4, "GNSS_STATUS", payload = "{\"satelliteCount\":3,\"satellites\":[{\"constellationType\":1,\"svid\":5},{\"constellationType\":3,\"svid\":24},{\"constellationType\":5,\"svid\":29}]}"),
        )

        val a = SessionAnalysisEngine().analyze(rows)
        assertEquals("EXACTLY_CONSTANT", a.agcState)
        assertEquals(2, a.agcSampleCount)
        assertEquals(0.0, a.agcSpreadDb!!, 0.0)
        assertEquals(1, a.navigationMessageCount)
        assertEquals(3, a.distinctSatelliteCount)
        assertEquals(1, a.multiSatelliteEpochs)
    }

    @Test
    fun servingCellIsPreferredForLiveRsrpAnalysis() {
        val rows = listOf(
            row(1, "CELLULAR_INFO", "LTE", "{\"registered\":false,\"rsrp\":-85,\"acquisitionTrigger\":\"REQUEST_CELL_INFO_UPDATE\"}"),
            row(2, "CELLULAR_INFO", "LTE", "{\"registered\":true,\"rsrp\":-104,\"sourceAgeMs\":120,\"acquisitionTrigger\":\"REQUEST_CELL_INFO_UPDATE\"}"),
            row(3, "CELLULAR_INFO", "LTE", "{\"registered\":true,\"rsrp\":-101,\"sourceAgeMs\":80,\"acquisitionTrigger\":\"TELEPHONY_CALLBACK\"}"),
        )

        val a = SessionAnalysisEngine().analyze(rows)
        assertEquals(-102.5, a.averageRsrpDbm!!, 0.01)
        assertEquals(-101.0, a.latestServingRsrpDbm!!, 0.01)
        assertEquals("LTE", a.latestServingTechnology)
        assertEquals(2, a.freshCellRequestSamples)
        assertEquals(1, a.cellularCallbackSamples)
        assertTrue(a.latestCellAgeMs!! <= 80L)
    }
}
