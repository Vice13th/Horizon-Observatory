package horizon.observatory.resilience

import org.json.JSONException
import org.json.JSONObject

/** Kind of position a snapshot carries. Never inferred from the free-text provenance. */
enum class ResilientFixKind { OBSERVED_GNSS, SHORT_HORIZON_ESTIMATE }

/**
 * Latest eligible position published by Horizon.
 *
 * Missing is not zero: every optional value is `null` unless a source supplied it and it passed
 * validation. [timestampMonotonicNs] is `SystemClock.elapsedRealtimeNanos()` domain and is only
 * comparable within one boot; [utcTimestampMs] is informational and is never used to compute age.
 */
enum class LocationSourceClass {
    DIRECT_GNSS,
    FUSED_PROVIDER,
    NETWORK_DERIVED,
    CACHED,
    INTERPOLATED,
    PREDICTED,
    DEAD_RECKONED,
    UNKNOWN
}

/**
 * Provenance assessment for an Android Location fix.
 *
 * IMPORTANT: DIRECT_GNSS is a provider/source classification, not an integrity verdict. It does not
 * prove low multipath/NLOS risk, adequate satellite geometry, or that a PVT is accurate. Those are
 * separate evidence gates in the resilience runtime. UNKNOWN is mandatory when provider semantics
 * are unclear.
 */
data class LocationSourceAssessment(
    val sourceClass: LocationSourceClass,
    val provider: String?,
    val isMock: Boolean?,
    val reason: String
)

object LocationSourceClassifier {
    /** Does not infer GNSS provenance from hardware, satellite callbacks, or a technology label. */
    fun classify(provider: String?, isMock: Boolean?, cached: Boolean = false): LocationSourceAssessment {
        if (isMock == true) return LocationSourceAssessment(LocationSourceClass.UNKNOWN, provider, true, "MOCK_OR_TEST_LOCATION")
        if (cached) return LocationSourceAssessment(LocationSourceClass.CACHED, provider, isMock, "CACHED_FIX")
        val normalized = provider?.trim()?.lowercase()
        val sourceClass = when (normalized) {
            "gps", "gnss" -> LocationSourceClass.DIRECT_GNSS
            "network" -> LocationSourceClass.NETWORK_DERIVED
            "fused" -> LocationSourceClass.FUSED_PROVIDER
            else -> LocationSourceClass.UNKNOWN
        }
        val reason = when (sourceClass) {
            LocationSourceClass.DIRECT_GNSS -> "ANDROID_PROVIDER_GPS_OR_GNSS"
            LocationSourceClass.NETWORK_DERIVED -> "ANDROID_PROVIDER_NETWORK"
            LocationSourceClass.FUSED_PROVIDER -> "ANDROID_PROVIDER_FUSED"
            else -> "PROVIDER_SEMANTICS_UNKNOWN"
        }
        return LocationSourceAssessment(sourceClass, provider, isMock, reason)
    }
}

data class ResilientLocationSnapshot(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val altitudeM: Double?,
    val horizontalUncertaintyM: Double,
    val verticalUncertaintyM: Double?,
    val timestampMonotonicNs: Long,
    val navigationState: NavigationState,
    val provenance: String,
    /** Null = the producer did not state the kind (legacy v1 payloads). */
    val fixKind: ResilientFixKind? = null,
    val utcTimestampMs: Long? = null,
    val speedMps: Double? = null,
    val bearingDeg: Double? = null,
    /** Estimator identifier for [ResilientFixKind.SHORT_HORIZON_ESTIMATE]; null for observations. */
    val estimator: String? = null
)

object ResilientLocationValidator {
    /** First contract violation as a stable reason code, or null when the snapshot is publishable. */
    fun problem(s: ResilientLocationSnapshot): String? = when {
        !s.latitudeDeg.isFinite() || s.latitudeDeg < -90.0 || s.latitudeDeg > 90.0 -> "LATITUDE_OUT_OF_RANGE"
        !s.longitudeDeg.isFinite() || s.longitudeDeg < -180.0 || s.longitudeDeg > 180.0 -> "LONGITUDE_OUT_OF_RANGE"
        !s.horizontalUncertaintyM.isFinite() || s.horizontalUncertaintyM < 0.0 -> "INVALID_HORIZONTAL_UNCERTAINTY"
        s.altitudeM != null && !s.altitudeM.isFinite() -> "INVALID_ALTITUDE"
        s.verticalUncertaintyM != null && (!s.verticalUncertaintyM.isFinite() || s.verticalUncertaintyM < 0.0) -> "INVALID_VERTICAL_UNCERTAINTY"
        s.speedMps != null && (!s.speedMps.isFinite() || s.speedMps < 0.0) -> "INVALID_SPEED"
        s.bearingDeg != null && (!s.bearingDeg.isFinite() || s.bearingDeg < 0.0 || s.bearingDeg >= 360.0) -> "INVALID_BEARING"
        s.timestampMonotonicNs <= 0L -> "INVALID_MONOTONIC_TIMESTAMP"
        s.utcTimestampMs != null && s.utcTimestampMs <= 0L -> "INVALID_UTC_TIMESTAMP"
        s.provenance.isBlank() -> "MISSING_PROVENANCE"
        s.fixKind == ResilientFixKind.SHORT_HORIZON_ESTIMATE && s.estimator.isNullOrBlank() -> "MISSING_ESTIMATOR"
        else -> null
    }
}

sealed interface SnapshotDecodeResult {
    data class Valid(val snapshot: ResilientLocationSnapshot) : SnapshotDecodeResult
    /** Nothing has been published. */
    object Absent : SnapshotDecodeResult
    /** Stored payload exists but cannot be trusted; [reason] is a stable diagnostic code. */
    data class Corrupt(val reason: String) : SnapshotDecodeResult
}

/**
 * JSON codec for the stored snapshot. Optional values are written as explicit JSON nulls
 * (`JSONObject.put(key, null)` would silently delete the key) and are read back without coercion.
 */
object ResilientLocationCodec {
    const val SCHEMA_VERSION = 2
    private const val LEGACY_SCHEMA_VERSION = 1

    /** @throws IllegalArgumentException when the snapshot violates [ResilientLocationValidator]. */
    fun encode(s: ResilientLocationSnapshot): String {
        ResilientLocationValidator.problem(s)?.let { throw IllegalArgumentException("Snapshot rejected: $it") }
        return JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("latitudeDeg", s.latitudeDeg)
            .put("longitudeDeg", s.longitudeDeg)
            .put("altitudeM", s.altitudeM ?: JSONObject.NULL)
            .put("horizontalUncertaintyM", s.horizontalUncertaintyM)
            .put("verticalUncertaintyM", s.verticalUncertaintyM ?: JSONObject.NULL)
            .put("timestampMonotonicNs", s.timestampMonotonicNs)
            .put("utcTimestampMs", s.utcTimestampMs ?: JSONObject.NULL)
            .put("speedMps", s.speedMps ?: JSONObject.NULL)
            .put("bearingDeg", s.bearingDeg ?: JSONObject.NULL)
            .put("navigationState", s.navigationState.name)
            .put("fixKind", s.fixKind?.name ?: JSONObject.NULL)
            .put("provenance", s.provenance)
            .put("estimator", s.estimator ?: JSONObject.NULL)
            .toString()
    }

    fun decode(raw: String?): SnapshotDecodeResult {
        if (raw == null) return SnapshotDecodeResult.Absent
        return try {
            SnapshotDecodeResult.Valid(parse(raw))
        } catch (e: CodecException) {
            SnapshotDecodeResult.Corrupt(e.reason)
        }
    }

    private fun parse(raw: String): ResilientLocationSnapshot {
        val j = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            throw CodecException("MALFORMED_JSON")
        }
        if (j.has("schemaVersion")) {
            val version = j.opt("schemaVersion") as? Int ?: throw CodecException("INVALID_schemaVersion")
            if (version < LEGACY_SCHEMA_VERSION || version > SCHEMA_VERSION) throw CodecException("UNSUPPORTED_SCHEMA_VERSION")
        }
        val stateName = optionalString(j, "navigationState") ?: throw CodecException("MISSING_navigationState")
        val state = NavigationState.entries.firstOrNull { it.name == stateName } ?: throw CodecException("UNKNOWN_navigationState")
        val kind = optionalString(j, "fixKind")?.let { name ->
            ResilientFixKind.entries.firstOrNull { it.name == name } ?: throw CodecException("UNKNOWN_fixKind")
        }
        val snapshot = ResilientLocationSnapshot(
            latitudeDeg = optionalDouble(j, "latitudeDeg") ?: throw CodecException("MISSING_latitudeDeg"),
            longitudeDeg = optionalDouble(j, "longitudeDeg") ?: throw CodecException("MISSING_longitudeDeg"),
            altitudeM = optionalDouble(j, "altitudeM"),
            horizontalUncertaintyM = optionalDouble(j, "horizontalUncertaintyM") ?: throw CodecException("MISSING_horizontalUncertaintyM"),
            verticalUncertaintyM = optionalDouble(j, "verticalUncertaintyM"),
            timestampMonotonicNs = optionalLong(j, "timestampMonotonicNs") ?: throw CodecException("MISSING_timestampMonotonicNs"),
            navigationState = state,
            provenance = optionalString(j, "provenance") ?: throw CodecException("MISSING_provenance"),
            fixKind = kind,
            utcTimestampMs = optionalLong(j, "utcTimestampMs"),
            speedMps = optionalDouble(j, "speedMps"),
            bearingDeg = optionalDouble(j, "bearingDeg"),
            estimator = optionalString(j, "estimator")
        )
        ResilientLocationValidator.problem(snapshot)?.let { throw CodecException("INVALID_VALUE_$it") }
        return snapshot
    }

    // Absent key and explicit JSON null both mean "not provided"; any other non-conforming value is corrupt.
    private fun optionalDouble(j: JSONObject, key: String): Double? {
        if (!j.has(key) || j.isNull(key)) return null
        val number = j.opt(key) as? Number ?: throw CodecException("INVALID_$key")
        val value = number.toDouble()
        if (!value.isFinite()) throw CodecException("INVALID_$key")
        return value
    }

    private fun optionalLong(j: JSONObject, key: String): Long? {
        if (!j.has(key) || j.isNull(key)) return null
        return when (val value = j.opt(key)) {
            is Int -> value.toLong()
            is Long -> value
            else -> throw CodecException("INVALID_$key")
        }
    }

    private fun optionalString(j: JSONObject, key: String): String? {
        if (!j.has(key) || j.isNull(key)) return null
        return j.opt(key) as? String ?: throw CodecException("INVALID_$key")
    }

    private class CodecException(val reason: String) : Exception(reason)
}
