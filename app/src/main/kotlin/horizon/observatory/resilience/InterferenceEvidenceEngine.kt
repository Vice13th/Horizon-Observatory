package horizon.observatory.resilience

class InterferenceEvidenceEngine(
    private val cn0CollapseDb: Double = 10.0,
    private val satelliteDropFraction: Double = 0.50,
    private val degradedScore: Double = 0.25,
    private val jamLikelyScore: Double = 0.60
) {
    fun assess(s: InterferenceSnapshot): InterferenceAssessment {
        val signals = mutableListOf<EvidenceSignal>()
        var score = 0.0
        var weight = 0.0
        fun add(name: String, hit: Boolean, w: Double, value: String) {
            signals += EvidenceSignal(name, value, w)
            weight += w
            if (hit) score += w
        }
        val cn0Drop = if (s.cn0MedianDbHz != null && s.baselineCn0MedianDbHz != null)
            s.baselineCn0MedianDbHz - s.cn0MedianDbHz else null
        add("CN0_COLLAPSE", cn0Drop != null && cn0Drop >= cn0CollapseDb, 0.25, cn0Drop?.toString() ?: "UNKNOWN")
        val satDrop = if (s.trackedSatelliteCount != null && s.baselineTrackedSatelliteCount != null && s.baselineTrackedSatelliteCount > 0)
            (s.baselineTrackedSatelliteCount - s.trackedSatelliteCount).toDouble() / s.baselineTrackedSatelliteCount else null
        add("SATELLITE_DROP", satDrop != null && satDrop >= satelliteDropFraction, 0.20, satDrop?.toString() ?: "UNKNOWN")
        add("PVT_DEGRADATION", s.pvtDegraded == true, 0.15, s.pvtDegraded?.toString() ?: "UNKNOWN")
        add("CONTINUITY_LOSS", s.measurementContinuityLost == true, 0.15, s.measurementContinuityLost?.toString() ?: "UNKNOWN")
        add("CROSS_SOURCE_DISAGREEMENT", s.crossSourceDisagreement == true, 0.15, s.crossSourceDisagreement?.toString() ?: "UNKNOWN")
        add("DOPPLER_RESIDUAL", s.dopplerResidualRms != null && s.dopplerResidualRms > 3.0, 0.05, s.dopplerResidualRms?.toString() ?: "UNKNOWN")
        add("ADR_RESIDUAL", s.adrResidualRms != null && s.adrResidualRms > 1.0, 0.05, s.adrResidualRms?.toString() ?: "UNKNOWN")
        if (s.ageMs != null && s.ageMs > 5000) add("STALE_MEASUREMENTS", true, 0.10, s.ageMs.toString())
        val normalized = if (weight == 0.0) 0.0 else score / weight
        val hasEvidence = s.cn0MedianDbHz != null || s.baselineCn0MedianDbHz != null ||
            s.trackedSatelliteCount != null || s.usedInFixSatelliteCount != null ||
            s.baselineTrackedSatelliteCount != null || s.constellationCount != null ||
            s.bandCount != null || s.dopplerResidualRms != null || s.adrResidualRms != null ||
            s.pvtDegraded != null || s.measurementContinuityLost != null ||
            s.crossSourceDisagreement != null || s.ageMs != null
        val state = when {
            !hasEvidence -> InterferenceState.UNKNOWN
            normalized >= jamLikelyScore && (cn0Drop != null || satDrop != null) -> InterferenceState.JAM_LIKELY
            s.crossSourceDisagreement == true && s.cn0MedianDbHz != null && normalized >= degradedScore -> InterferenceState.SPOOF_LIKELY
            normalized >= degradedScore -> InterferenceState.DEGRADED
            s.hasRecoveryEvidence() && s.recoveryGnssCandidate() -> InterferenceState.RECOVERY
            else -> InterferenceState.NORMAL
        }
        return InterferenceAssessment(state, normalized, signals)
    }

    private fun InterferenceSnapshot.hasRecoveryEvidence(): Boolean =
        pvtDegraded != null || measurementContinuityLost != null ||
            (cn0MedianDbHz != null && baselineCn0MedianDbHz != null)

    private fun InterferenceSnapshot.recoveryGnssCandidate(): Boolean =
        pvtDegraded == false && measurementContinuityLost == false &&
            (cn0MedianDbHz == null || baselineCn0MedianDbHz == null || cn0MedianDbHz >= baselineCn0MedianDbHz)
}

