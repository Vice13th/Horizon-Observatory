package horizon.observatory.analysis.integrity

import kotlin.math.roundToInt

/**
 * Shared carrier-frequency grouping rule for integrity analysis and UI plots.
 * Invalid, unsupported, or missing frequencies remain in the explicit unknown bucket.
 */
object GnssCarrierBand {
    fun bucketMHz(carrierFrequencyHz: Double?): Int =
        carrierFrequencyHz
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { (it / 1.0e6).roundToInt() }
            ?: UNKNOWN_BUCKET

    const val UNKNOWN_BUCKET = -1
}
