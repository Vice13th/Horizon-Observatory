package horizon.observatory.live

import horizon.observatory.astronomy.identity.GnssSatelliteId
import horizon.observatory.domain.gnss.NavigationAssociation
import horizon.observatory.domain.gnss.SatelliteCorrelationKey
import horizon.observatory.domain.gnss.SatelliteEvidence
import horizon.observatory.domain.gnss.SatelliteEvidenceMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObservedSkyStateTest {
    private fun evidence(svid: Int, az: Double?, el: Double?, cn0: Double? = 30.0, used: Boolean? = true) =
        SatelliteEvidence(
            key = SatelliteCorrelationKey(1, svid),
            constellationType = 1,
            svid = svid,
            carrierFrequencyHz = null,
            cn0DbHz = cn0,
            basebandCn0DbHz = null,
            elevationDegrees = el,
            azimuthDegrees = az,
            usedInFix = used,
            measurementState = null,
            multipathIndicator = null,
            accumulatedDeltaRangeMeters = null,
            accumulatedDeltaRangeState = null,
            automaticGainControlLevelDb = null,
            navigationMessageCount = 0,
            navigationAssociation = NavigationAssociation.NOT_ASSOCIATED,
            lastNavigationMessageMonotonicTimestampNs = null,
            observationMonotonicTimestampNs = null,
            statusMonotonicTimestampNs = null,
            rawSequenceNumber = null,
            statusSequenceNumber = null,
            match = SatelliteEvidenceMatch.STATUS_ONLY
        )

    @Test
    fun `receiver az and el are carried unchanged`() {
        val s = ObservedSkyState.from(listOf(evidence(13, az = 123.5, el = 45.25, cn0 = 41.0, used = false)))
        val p = s.points.single()
        assertEquals(GnssSatelliteId(1, 13), p.satelliteId)
        assertEquals(123.5, p.azimuthDeg, 0.0)
        assertEquals(45.25, p.elevationDeg, 0.0)
        assertEquals(41.0, p.cn0DbHz!!, 0.0)
        assertEquals(false, p.usedInFix)
        assertEquals(0, s.excludedWithoutGeometry)
    }

    @Test
    fun `missing or non-finite geometry is excluded and counted never placed at zero`() {
        val s = ObservedSkyState.from(
            listOf(
                evidence(1, az = null, el = 30.0),
                evidence(2, az = 10.0, el = null),
                evidence(3, az = Double.NaN, el = 30.0),
                evidence(4, az = 10.0, el = Double.POSITIVE_INFINITY),
                evidence(5, az = 0.0, el = 0.0)
            )
        )
        assertEquals(listOf(5), s.points.map { it.satelliteId.svid })
        assertEquals(4, s.excludedWithoutGeometry)
    }

    @Test
    fun `unknown optional fields stay null`() {
        val p = ObservedSkyState.from(listOf(evidence(7, 1.0, 2.0, cn0 = null, used = null))).points.single()
        assertNull(p.cn0DbHz)
        assertNull(p.usedInFix)
    }

    @Test
    fun `empty input gives an empty state`() {
        assertEquals(ObservedSkyState.EMPTY, ObservedSkyState.from(emptyList()))
    }
}
