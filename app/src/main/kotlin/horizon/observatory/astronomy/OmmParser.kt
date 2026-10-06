package horizon.observatory.astronomy

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** [OmmParseIssue.recordIndex] value for problems with the payload as a whole. */
const val OMM_PAYLOAD_LEVEL_INDEX: Int = -1

data class OmmParseIssue(
    val recordIndex: Int,
    val objectHint: String?,
    val field: String?,
    val message: String
)

/**
 * Structured parse outcome. Nothing is dropped silently: every skipped entry contributes to
 * [skippedCount] and has at least one entry in [errors] (or a duplicate warning in [warnings]).
 */
data class OmmParseResult(
    val records: List<OmmRecord>,
    val acceptedCount: Int,
    val skippedCount: Int,
    val warnings: List<OmmParseIssue>,
    val errors: List<OmmParseIssue>
) {
    /** True when the payload itself could not be interpreted (not merely individual records). */
    val payloadRejected: Boolean
        get() = errors.any { it.recordIndex == OMM_PAYLOAD_LEVEL_INDEX }
}

/** OMM EPOCH parsing. EPOCH is UTC; a missing offset is interpreted as UTC, never as local time. */
object OmmEpoch {
    private val FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC)

    fun parse(raw: String): Instant? =
        try {
            Instant.from(FORMATTER.parse(raw.trim()))
        } catch (e: DateTimeException) {
            null
        }
}

/**
 * Parses CelesTrak-style OMM/JSON (array of objects using OMM field names). Pure parsing: no network
 * code, no Android dependency. Required orbital fields are never defaulted; a record missing any of
 * them, or carrying an out-of-range value, is skipped and reported.
 *
 * Required: NORAD_CAT_ID, EPOCH, MEAN_MOTION, ECCENTRICITY, INCLINATION, RA_OF_ASC_NODE,
 * ARG_OF_PERICENTER, MEAN_ANOMALY, BSTAR. Optional (preserved when present): OBJECT_NAME, OBJECT_ID,
 * CLASSIFICATION_TYPE, EPHEMERIS_TYPE, ELEMENT_SET_NO, REV_AT_EPOCH, MEAN_MOTION_DOT,
 * MEAN_MOTION_DDOT.
 */
object OmmParser {
    fun parse(json: String): OmmParseResult {
        val array = try {
            JSONArray(json)
        } catch (e: JSONException) {
            return OmmParseResult(
                records = emptyList(),
                acceptedCount = 0,
                skippedCount = 0,
                warnings = emptyList(),
                errors = listOf(
                    OmmParseIssue(OMM_PAYLOAD_LEVEL_INDEX, null, null, "payload is not a JSON array: ${e.message}")
                )
            )
        }

        val accepted = LinkedHashMap<Long, OmmRecord>()
        val warnings = ArrayList<OmmParseIssue>()
        val errors = ArrayList<OmmParseIssue>()
        var skipped = 0

        for (index in 0 until array.length()) {
            val element = array.opt(index)
            if (element !is JSONObject) {
                skipped++
                errors.add(OmmParseIssue(index, null, null, "array element is not a JSON object"))
                continue
            }
            val context = RecordContext(index, element)
            val record = context.build()
            warnings.addAll(context.warnings)
            if (record == null) {
                skipped++
                errors.addAll(context.errors)
                continue
            }
            val existing = accepted[record.noradCatId]
            if (existing == null) {
                accepted[record.noradCatId] = record
            } else {
                if (record.epochUtc.isAfter(existing.epochUtc)) accepted[record.noradCatId] = record
                skipped++
                warnings.add(
                    OmmParseIssue(
                        index, context.hint, "NORAD_CAT_ID",
                        "duplicate NORAD_CAT_ID ${record.noradCatId} in payload; kept the record with the later epoch"
                    )
                )
            }
        }

        val records = accepted.values.toList()
        return OmmParseResult(records, records.size, skipped, warnings, errors)
    }

    private class RecordContext(private val index: Int, private val json: JSONObject) {
        val warnings = ArrayList<OmmParseIssue>()
        val errors = ArrayList<OmmParseIssue>()
        val hint: String? = stringOrNull("OBJECT_NAME")

        fun build(): OmmRecord? {
            val noradCatId = requiredLong("NORAD_CAT_ID")
            val epochRaw = requiredString("EPOCH")
            val meanMotion = requiredDouble("MEAN_MOTION")
            val eccentricity = requiredDouble("ECCENTRICITY")
            val inclination = requiredDouble("INCLINATION")
            val raan = requiredDouble("RA_OF_ASC_NODE")
            val argOfPericenter = requiredDouble("ARG_OF_PERICENTER")
            val meanAnomaly = requiredDouble("MEAN_ANOMALY")
            val bstar = requiredDouble("BSTAR")

            var epochUtc: Instant? = null
            if (epochRaw != null) {
                epochUtc = OmmEpoch.parse(epochRaw)
                if (epochUtc == null) error("EPOCH", "not an ISO-8601 UTC timestamp: $epochRaw")
            }
            if (noradCatId != null && noradCatId <= 0L) error("NORAD_CAT_ID", "must be positive")
            if (meanMotion != null && meanMotion <= 0.0) error("MEAN_MOTION", "must be > 0 rev/day")
            if (eccentricity != null && (eccentricity < 0.0 || eccentricity >= 1.0)) {
                error("ECCENTRICITY", "must satisfy 0 <= e < 1")
            }
            if (inclination != null && (inclination < 0.0 || inclination > 180.0)) {
                error("INCLINATION", "must be within [0, 180] degrees")
            }
            if (raan != null && (raan < 0.0 || raan > 360.0)) error("RA_OF_ASC_NODE", "must be within [0, 360] degrees")
            if (argOfPericenter != null && (argOfPericenter < 0.0 || argOfPericenter > 360.0)) {
                error("ARG_OF_PERICENTER", "must be within [0, 360] degrees")
            }
            if (meanAnomaly != null && (meanAnomaly < 0.0 || meanAnomaly > 360.0)) {
                error("MEAN_ANOMALY", "must be within [0, 360] degrees")
            }

            if (errors.isNotEmpty() ||
                noradCatId == null || epochRaw == null || epochUtc == null ||
                meanMotion == null || eccentricity == null || inclination == null ||
                raan == null || argOfPericenter == null || meanAnomaly == null || bstar == null
            ) {
                return null
            }

            val ephemerisType = optionalInt("EPHEMERIS_TYPE")
            if (ephemerisType != null && ephemerisType != 0) {
                warn("EPHEMERIS_TYPE", "value $ephemerisType is not 0; SGP4/SDP4 element sets are defined for type 0")
            }

            return OmmRecord(
                noradCatId = noradCatId,
                objectName = stringOrNull("OBJECT_NAME"),
                objectId = stringOrNull("OBJECT_ID"),
                classification = stringOrNull("CLASSIFICATION_TYPE"),
                epochRaw = epochRaw,
                epochUtc = epochUtc,
                meanMotionRevPerDay = meanMotion,
                eccentricity = eccentricity,
                inclinationDeg = inclination,
                raanDeg = raan,
                argOfPericenterDeg = argOfPericenter,
                meanAnomalyDeg = meanAnomaly,
                ephemerisType = ephemerisType,
                elementSetNo = optionalInt("ELEMENT_SET_NO"),
                revAtEpoch = optionalLong("REV_AT_EPOCH"),
                bstar = bstar,
                meanMotionDot = optionalDouble("MEAN_MOTION_DOT"),
                meanMotionDdot = optionalDouble("MEAN_MOTION_DDOT")
            )
        }

        fun error(field: String?, message: String) {
            errors.add(OmmParseIssue(index, hint, field, message))
        }

        private fun warn(field: String?, message: String) {
            warnings.add(OmmParseIssue(index, hint, field, message))
        }

        private fun rawOrNull(field: String): Any? =
            if (json.has(field) && !json.isNull(field)) json.get(field) else null

        private fun stringOrNull(field: String): String? {
            val raw = rawOrNull(field) ?: return null
            val text = raw.toString().trim()
            return if (text.isEmpty()) null else text
        }

        private fun requiredString(field: String): String? {
            val text = stringOrNull(field)
            if (text == null) error(field, "missing required field")
            return text
        }

        private fun requiredDouble(field: String): Double? {
            val raw = rawOrNull(field)
            if (raw == null) {
                error(field, "missing required field")
                return null
            }
            val value = toFiniteDouble(raw)
            if (value == null) error(field, "not a finite number: $raw")
            return value
        }

        private fun requiredLong(field: String): Long? {
            val raw = rawOrNull(field)
            if (raw == null) {
                error(field, "missing required field")
                return null
            }
            val value = toExactLong(raw)
            if (value == null) error(field, "not an integer: $raw")
            return value
        }

        private fun optionalDouble(field: String): Double? {
            val raw = rawOrNull(field) ?: return null
            val value = toFiniteDouble(raw)
            if (value == null) warn(field, "ignored non-numeric value: $raw")
            return value
        }

        private fun optionalLong(field: String): Long? {
            val raw = rawOrNull(field) ?: return null
            val value = toExactLong(raw)
            if (value == null) warn(field, "ignored non-integer value: $raw")
            return value
        }

        private fun optionalInt(field: String): Int? {
            val value = optionalLong(field) ?: return null
            if (value < Int.MIN_VALUE.toLong() || value > Int.MAX_VALUE.toLong()) {
                warn(field, "ignored out-of-range integer: $value")
                return null
            }
            return value.toInt()
        }

        private fun toFiniteDouble(raw: Any): Double? {
            val value: Double? = when (raw) {
                is Number -> raw.toDouble()
                is String -> raw.trim().toDoubleOrNull()
                else -> null
            }
            return if (value != null && value.isFinite()) value else null
        }

        private fun toExactLong(raw: Any): Long? =
            when (raw) {
                is Int -> raw.toLong()
                is Long -> raw
                is Number -> {
                    val d = raw.toDouble()
                    if (d.isFinite() && d == Math.rint(d) && Math.abs(d) < 9.0e15) d.toLong() else null
                }
                is String -> raw.trim().toLongOrNull()
                else -> null
            }
    }
}
