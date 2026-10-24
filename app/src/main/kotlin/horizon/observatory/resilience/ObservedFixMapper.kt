package horizon.observatory.resilience

import horizon.observatory.domain.model.ObservationType
import horizon.observatory.domain.model.RawObservation
import org.json.JSONException
import org.json.JSONObject

sealed interface ObservedFixMapping {
    data class Mapped(val snapshot: ResilientLocationSnapshot) : ObservedFixMapping
    data class Rejected(val reason: String) : ObservedFixMapping
}

/**
 * Maps a raw GNSS_FIX observation to a publishable snapshot. The raw observation is never modified.
 *
 * Required: valid latitude/longitude, valid horizontal accuracy, source monotonic timestamp.
 * Everything else is optional: absent, JSON-null or invalid values become `null` -- never 0 and never
 * copied from another field (horizontal accuracy is not a vertical accuracy).
 */
object ObservedFixMapper {
    const val PROVENANCE = "GNSS_OBSERVED"

    fun map(observation: RawObservation, navigationState: NavigationState): ObservedFixMapping {
        if (observation.type != ObservationType.GNSS_FIX) return ObservedFixMapping.Rejected("NOT_A_GNSS_FIX")
        val monotonicNs = observation.monotonicTimestampNs ?: return ObservedFixMapping.Rejected("MISSING_SOURCE_TIMESTAMP")
        val payload = try {
            JSONObject(observation.payloadJson)
        } catch (e: JSONException) {
            return ObservedFixMapping.Rejected("MALFORMED_PAYLOAD")
        }
        val provider = payload.optString("provider", observation.provider).takeIf { it.isNotBlank() }
        val mockState = when {
            !payload.has("isMock") || payload.isNull("isMock") -> null
            payload.opt("isMock") is Boolean -> payload.optBoolean("isMock")
            else -> null
        }
        // Mock/test fixes never enter the observed-GNSS snapshot path. Unknown provider semantics
        // remain unknown rather than being promoted to GNSS based on the observation technology.
        if (mockState == true) return ObservedFixMapping.Rejected("MOCK_LOCATION")
        if (mockState == null) return ObservedFixMapping.Rejected("MOCK_STATUS_UNAVAILABLE")
        val source = LocationSourceClassifier.classify(provider, mockState)
        if (source.sourceClass != LocationSourceClass.DIRECT_GNSS) {
            return ObservedFixMapping.Rejected("SOURCE_NOT_CONFIRMED_GNSS:${source.reason}")
        }
        val latitude = finite(payload, "latitude") ?: return ObservedFixMapping.Rejected("MISSING_LATITUDE")
        val longitude = finite(payload, "longitude") ?: return ObservedFixMapping.Rejected("MISSING_LONGITUDE")
        val horizontalAccuracy = finite(payload, "accuracyMeters")?.takeIf { it >= 0.0 }
            ?: return ObservedFixMapping.Rejected("MISSING_OR_INVALID_HORIZONTAL_ACCURACY")
        val snapshot = ResilientLocationSnapshot(
            latitudeDeg = latitude,
            longitudeDeg = longitude,
            altitudeM = finite(payload, "altitude"),
            horizontalUncertaintyM = horizontalAccuracy,
            verticalUncertaintyM = finite(payload, "verticalAccuracyMeters")?.takeIf { it >= 0.0 },
            timestampMonotonicNs = monotonicNs,
            navigationState = navigationState,
            provenance = "$PROVENANCE:${source.sourceClass.name}:${source.reason}",
            fixKind = ResilientFixKind.OBSERVED_GNSS,
            utcTimestampMs = observation.utcTimestampMs.takeIf { it > 0L },
            speedMps = finite(payload, "speedMetersPerSecond")?.takeIf { it >= 0.0 },
            bearingDeg = finite(payload, "bearingDegrees")?.takeIf { it >= 0.0 && it < 360.0 },
            estimator = null
        )
        ResilientLocationValidator.problem(snapshot)?.let { return ObservedFixMapping.Rejected(it) }
        return ObservedFixMapping.Mapped(snapshot)
    }

    private fun finite(payload: JSONObject, key: String): Double? {
        if (!payload.has(key) || payload.isNull(key)) return null
        val number = payload.opt(key) as? Number ?: return null
        return number.toDouble().takeIf { it.isFinite() }
    }
}
