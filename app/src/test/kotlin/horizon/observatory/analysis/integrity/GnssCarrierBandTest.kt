package horizon.observatory.analysis.integrity

import org.junit.Assert.assertEquals
import org.junit.Test

class GnssCarrierBandTest {
    @Test fun roundsCarrierFrequencyToNearestMhz() {
        assertEquals(1575, GnssCarrierBand.bucketMHz(1_575_420_000.0))
        // Half-MHz values round consistently in UI and analysis instead of being truncated.
        assertEquals(1176, GnssCarrierBand.bucketMHz(1_175_500_000.0))
        assertEquals(1177, GnssCarrierBand.bucketMHz(1_176_500_000.0))
    }

    @Test fun invalidOrMissingFrequencyUsesUnknownBucket() {
        assertEquals(-1, GnssCarrierBand.bucketMHz(null))
        assertEquals(-1, GnssCarrierBand.bucketMHz(Double.NaN))
        assertEquals(-1, GnssCarrierBand.bucketMHz(Double.POSITIVE_INFINITY))
        assertEquals(-1, GnssCarrierBand.bucketMHz(0.0))
        assertEquals(-1, GnssCarrierBand.bucketMHz(-1.0))
    }
}
