package horizon.observatory.domain.assistance

import horizon.observatory.domain.model.CapabilityState
import horizon.observatory.domain.model.EvidenceStatus
import horizon.observatory.domain.model.ObservationProvenance
import horizon.observatory.domain.model.ObservationType
import horizon.observatory.domain.model.RawObservation
import horizon.observatory.domain.model.TimestampDomain
import org.json.JSONObject

/**
 * Converts AssistanceEvidence into the existing RawObservation pipeline
 * (ObservationPersistenceQueue -> observations table -> export), reusing 100% of existing
 * session/storage/export machinery with zero schema or export-format changes -- the generic
 * type/source/technology/rawPayloadJson columns already carry whatever ObservationType needs.
 *
 * NOT an ObservationSource: there is no start()/stop()/observationFlow, because there is no
 * automatic Android signal to poll or listen to (see REPORT: "RELEVANT ANDROID APIs NOT
 * AVAILABLE"). This is a pure conversion function, called explicitly by whatever code actually
 * has a piece of assistance evidence -- currently nothing in this project calls it
 * automatically. It exists so that IF a real source is ever added, it has a correct target to
 * call, without needing to touch the GNSS/session/storage/export layers again.
 *
 * Provenance is never defaulted or upgraded here: the caller must state OBSERVED, INFERRED, or
 * UNKNOWN explicitly on the AssistanceEvidence itself (rule: "Never silently convert INFERRED
 * into OBSERVED").
 */
object AssistanceEvidenceRecorder {

    /**
     * @param nowUtcMillis real wall-clock reading at conversion time, used ONLY as the
     *   non-nullable RawObservation.utcTimestampMs column's ingestion-time fallback when the
     *   assistance event's own wall-clock timestamp is unknown -- never as a substitute
     *   correlation timestamp (monotonicTimestampNs remains authoritative for that; see
     *   AssistanceCorrelationEngine). Defaulting to System.currentTimeMillis() while accepting
     *   it as a parameter keeps this function testable with a fixed clock, matching
     *   TimestampConverter's own pattern of injected clock reads rather than internal calls.
     *   The evidence's own wallClockTimestampMs (possibly null) is preserved separately inside
     *   the payload regardless of what this parameter is.
     */
    fun toRawObservation(evidence: AssistanceEvidence, nowUtcMillis: Long = System.currentTimeMillis()): RawObservation {
        val payload = JSONObject().apply {
            put("eventId", evidence.eventId)
            put("category", evidence.category.name)
            put("type", evidence.type.name)
            put("provenance", evidence.provenance.name)
            put("wallClockTimestampMs", evidence.wallClockTimestampMs?.let { it as Any } ?: JSONObject.NULL)
            put("validityDurationMs", evidence.validityDurationMs?.let { it as Any } ?: JSONObject.NULL)
            put("providerMetadata", evidence.providerMetadata ?: JSONObject.NULL)
            put("confidenceState", evidence.confidenceState ?: JSONObject.NULL)
            put(
                "rawMetadata",
                evidence.rawMetadataJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject.NULL
            )
        }

        // Section 14-equivalent for this domain: if no monotonic timestamp is available, the
        // timestamp domain is honestly UNKNOWN rather than fabricated from wall-clock alone.
        val timestampDomain = if (evidence.monotonicTimestampNs != null) {
            TimestampDomain.ANDROID_ELAPSED_REALTIME_NANOS
        } else {
            TimestampDomain.UNKNOWN
        }

        return RawObservation(
            type = ObservationType.ASSISTANCE_EVENT,
            // RawObservation.utcTimestampMs is non-nullable (existing model, unchanged). If the
            // evidence has its own real wall-clock timestamp, use it; otherwise fall back to a
            // REAL ingestion-time reading (nowUtcMillis) -- never a placeholder like epoch 0,
            // which would silently look like a real (wrong) 1970 timestamp downstream. The
            // distinction between "the event's own time" and "when Horizon ingested it" is not
            // lost: the evidence's own wallClockTimestampMs (null or not) is always preserved
            // verbatim in the payload above.
            utcTimestampMs = evidence.wallClockTimestampMs ?: nowUtcMillis,
            provider = evidence.category.name,
            payloadJson = payload.toString(),
            monotonicTimestampNs = evidence.monotonicTimestampNs,
            ingestionMonotonicTimestampNs = evidence.ingestionMonotonicTimestampNs,
            technology = evidence.type.name,
            capabilityState = CapabilityState.NOT_OBSERVED,
            evidenceStatus = when (evidence.provenance) {
                AssistanceProvenance.OBSERVED -> EvidenceStatus.OBSERVED
                AssistanceProvenance.INFERRED -> EvidenceStatus.MODEL_GENERATED
                AssistanceProvenance.UNKNOWN -> EvidenceStatus.UNKNOWN
            },
            provenance = ObservationProvenance.RAW_ACQUISITION,
            timestampDomain = timestampDomain,
            timestampUncertaintyNs = null
        )
    }
}
