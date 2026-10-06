package horizon.observatory.astronomy.propagation

import org.junit.Ignore
import org.junit.Test

/**
 * PENDING / UNVERIFIED. Structure only. No numerical expectation is asserted anywhere because no
 * published reference vectors are imported and no concrete SGP4/SDP4 backend is integrated.
 *
 * To activate:
 *  1. Add SGP4-VER.TLE and tcppver.out to src/test/resources/sgp4/ (see README.md there).
 *  2. Integrate a vetted backend implementing Sgp4Sdp4Backend.
 *  3. Parse both files, propagate each object at each listed time, compare position/velocity with a
 *     documented tolerance, and remove @Ignore.
 *
 * A skipped test is NOT a pass.
 */
class Sgp4ReferenceVectorTest {
    @Ignore("PENDING: Vallado near-earth reference vectors not imported; no backend integrated")
    @Test
    fun nearEarthReferenceVectors() {
        TODO("compare near-earth objects against tcppver.out within documented tolerance")
    }

    @Ignore("PENDING: Vallado deep-space reference vectors not imported; no backend integrated")
    @Test
    fun deepSpaceReferenceVectors() {
        TODO("compare deep-space (12h/24h resonance) objects against tcppver.out within documented tolerance")
    }

    @Ignore("PENDING: epoch edge cases need an integrated backend")
    @Test
    fun epochEdgeCases() {
        TODO("exact epoch, before epoch, after epoch, multiple propagation intervals")
    }
}
