package horizon.observatory.astronomy.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometricIdentityMatcherTest {
    private fun o(svid: Int, az: Double, el: Double) = ObservedDirection(GnssSatelliteId(1, svid), az, el)
    private fun c(norad: Long, az: Double, el: Double) = CandidateDirection(norad, az, el, null)

    @Test fun separationKnownValues() {
        assertEquals(90.0, GeometricIdentityMatcher.separationDeg(0.0, 0.0, 0.0, 90.0), 1e-9)
        assertEquals(0.0, GeometricIdentityMatcher.separationDeg(5.0, 5.0, 5.0, 5.0), 1e-9)
        assertEquals(180.0, GeometricIdentityMatcher.separationDeg(0.0, 0.0, 180.0, 0.0), 1e-9)
        assertEquals(2.0, GeometricIdentityMatcher.separationDeg(0.0, 89.0, 180.0, 89.0), 1e-9) // across the zenith
    }

    @Test fun uniqueMutualBestIsConsistent() {
        val h = GeometricIdentityMatcher.match(listOf(o(1, 100.0, 40.0)), listOf(c(10, 100.5, 40.2), c(11, 200.0, 10.0))).single()
        assertEquals(HintStatus.CONSISTENT, h.status)
        assertEquals(10L, h.noradCatId)
    }

    @Test fun twoCloseCandidatesAreAmbiguous() {
        val h = GeometricIdentityMatcher.match(listOf(o(1, 100.0, 40.0)), listOf(c(10, 100.5, 40.0), c(11, 102.0, 40.0))).single()
        assertEquals(HintStatus.AMBIGUOUS, h.status)
    }

    @Test fun twoObservedClaimingOneCandidateAreBothAmbiguous() {
        val hs = GeometricIdentityMatcher.match(listOf(o(1, 100.0, 40.0), o(2, 101.5, 40.0)), listOf(c(10, 100.7, 40.0), c(11, 200.0, 10.0)))
        assertTrue(hs.all { it.status == HintStatus.AMBIGUOUS })
    }

    @Test fun farCandidateIsNoCandidate() {
        val h = GeometricIdentityMatcher.match(listOf(o(1, 0.0, 0.0)), listOf(c(10, 180.0, 50.0))).single()
        assertEquals(HintStatus.NO_CANDIDATE, h.status)
    }

    @Test fun twoSeparatedSatellitesBothMatch() {
        val hs = GeometricIdentityMatcher.match(
            listOf(o(1, 10.0, 30.0), o(2, 200.0, 50.0)),
            listOf(c(10, 10.4, 30.2), c(11, 199.5, 50.1), c(12, 100.0, 20.0))
        )
        assertEquals(listOf(10L, 11L), hs.map { it.noradCatId })
        assertTrue(hs.all { it.status == HintStatus.CONSISTENT })
    }
}
