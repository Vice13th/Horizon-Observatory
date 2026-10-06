package horizon.observatory.storage.repository

import horizon.observatory.domain.model.ObservationType
import horizon.observatory.domain.model.RawObservation
import horizon.observatory.storage.entity.ObservationEntity
import horizon.observatory.storage.entity.PendingObservationEntity

fun RawObservation.toPendingEntity(sessionId: String): PendingObservationEntity =
    PendingObservationEntity(
        sessionId = sessionId,
        sequenceNumber = sequenceNumber,
        timestampUtcMs = utcTimestampMs,
        monotonicTimestampNs = monotonicTimestampNs,
        ingestionMonotonicTimestampNs = ingestionMonotonicTimestampNs,
        timestampDomain = timestampDomain.name,
        timestampUncertaintyNs = timestampUncertaintyNs,
        source = sourceCategoryFor(type),
        technology = technology,
        type = type.name,
        provider = provider,
        rawPayloadJson = payloadJson,
        capabilityState = capabilityState.name,
        evidenceStatus = evidenceStatus.name,
        provenance = provenance.name
    )

fun PendingObservationEntity.toObservationEntity(): ObservationEntity =
    ObservationEntity(
        sessionId = sessionId,
        sequenceNumber = sequenceNumber,
        timestampUtcMs = timestampUtcMs,
        monotonicTimestampNs = monotonicTimestampNs,
        ingestionMonotonicTimestampNs = ingestionMonotonicTimestampNs,
        timestampDomain = timestampDomain,
        timestampUncertaintyNs = timestampUncertaintyNs,
        source = source,
        technology = technology,
        type = type,
        provider = provider,
        rawPayloadJson = rawPayloadJson,
        normalizedPayloadJson = normalizedPayloadJson,
        capabilityState = capabilityState,
        evidenceStatus = evidenceStatus,
        provenance = provenance
    )

fun RawObservation.toObservationEntity(sessionId: String): ObservationEntity =
    ObservationEntity(
        sessionId = sessionId,
        sequenceNumber = sequenceNumber,
        timestampUtcMs = utcTimestampMs,
        monotonicTimestampNs = monotonicTimestampNs,
        ingestionMonotonicTimestampNs = ingestionMonotonicTimestampNs,
        timestampDomain = timestampDomain.name,
        timestampUncertaintyNs = timestampUncertaintyNs,
        source = sourceCategoryFor(type),
        technology = technology,
        type = type.name,
        provider = provider,
        rawPayloadJson = payloadJson,
        capabilityState = capabilityState.name,
        evidenceStatus = evidenceStatus.name,
        provenance = provenance.name
    )

private fun sourceCategoryFor(type: ObservationType): String = when (type) {
    ObservationType.GNSS_FIX,
    ObservationType.GNSS_RAW_MEASUREMENT,
    ObservationType.GNSS_STATUS,
    ObservationType.GNSS_NAVIGATION_MESSAGE,
    ObservationType.GNSS_ANTENNA_INFO -> "GNSS"
    ObservationType.CELLULAR_INFO -> "CELLULAR"
    ObservationType.ASSISTANCE_EVENT -> "ASSISTANCE"
    ObservationType.SYSTEM_EVENT,
    ObservationType.ERROR_EVENT -> "SYSTEM"
    else -> "SENSOR"
}
