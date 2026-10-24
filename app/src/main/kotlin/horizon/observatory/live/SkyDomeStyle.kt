package horizon.observatory.live

/** Presentation helpers for the observed sky. Missing C/N0 is never turned into a number. */
object SkyDomeStyle {
    const val CN0_MIN_DBHZ = 20.0
    const val CN0_MAX_DBHZ = 50.0

    /** 0.0 (weak, ≤20 dB-Hz) … 1.0 (strong, ≥50 dB-Hz); null when not reported or non-finite. */
    fun cn0Fraction(cn0DbHz: Double?): Double? =
        cn0DbHz?.takeIf { it.isFinite() }?.let { ((it - CN0_MIN_DBHZ) / (CN0_MAX_DBHZ - CN0_MIN_DBHZ)).coerceIn(0.0, 1.0) }

    /** android.location.GnssStatus constellation constants → single-letter RINEX-style prefix. */
    fun constellationLetter(type: Int): Char = when (type) {
        1 -> 'G'; 2 -> 'S'; 3 -> 'R'; 4 -> 'J'; 5 -> 'C'; 6 -> 'E'; 7 -> 'I'; else -> '?'
    }

    fun label(type: Int, svid: Int): String = "${constellationLetter(type)}$svid"
}
