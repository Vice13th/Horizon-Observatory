package horizon.observatory.resilience

import horizon.observatory.domain.model.CapabilityState
import horizon.observatory.domain.model.EvidenceStatus
import horizon.observatory.domain.model.ObservationProvenance
import horizon.observatory.domain.model.ObservationType
import horizon.observatory.domain.model.RawObservation
import horizon.observatory.domain.model.TimestampDomain

internal const val S = 1_000_000_000L

/** Observation with explicit source (monotonicTimestampNs) and arrival (ingestion) clocks. */
internal fun rawObs(type: ObservationType, json: String, sourceNs: Long?, ingestNs: Long? = sourceNs) =
    RawObservation(
        type = type,
        utcTimestampMs = ((sourceNs ?: ingestNs ?: 1_000_000L) / 1_000_000L).coerceAtLeast(1L),
        provider = "test",
        payloadJson = json,
        monotonicTimestampNs = sourceNs,
        ingestionMonotonicTimestampNs = ingestNs,
        technology = "TEST",
        capabilityState = CapabilityState.AVAILABLE_NOW,
        evidenceStatus = EvidenceStatus.MEASURED,
        provenance = ObservationProvenance.RAW_ACQUISITION,
        timestampDomain = TimestampDomain.ANDROID_ELAPSED_REALTIME_NANOS
    )

internal fun fixJson(lat: Double = 50.0, lon: Double = 8.0, acc: Double? = 5.0, speed: Double? = null, bearing: Double? = null, vertical: Double? = null, alt: Double? = null): String {
    val j = org.json.JSONObject().put("latitude", lat).put("longitude", lon).put("provider", "gps").put("isMock", false)
    j.put("accuracyMeters", acc ?: org.json.JSONObject.NULL)
    j.put("speedMetersPerSecond", speed ?: org.json.JSONObject.NULL)
    j.put("bearingDegrees", bearing ?: org.json.JSONObject.NULL)
    j.put("verticalAccuracyMeters", vertical ?: org.json.JSONObject.NULL)
    j.put("altitude", alt ?: org.json.JSONObject.NULL)
    return j.toString()
}

internal const val STATUS_JSON = """{"satelliteCount":8,"satellites":[{"cn0DbHz":35,"usedInFix":true}]}"""
