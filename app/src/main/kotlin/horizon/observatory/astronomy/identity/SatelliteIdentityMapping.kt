package horizon.observatory.astronomy.identity

import horizon.observatory.astronomy.time.PropagationTime
import horizon.observatory.domain.gnss.SatelliteCorrelationKey

/**
 * Receiver-side satellite identity: Android constellation type + SVID. This is NOT a NORAD id and
 * is not a permanent identity: an SVID/PRN is reused by different physical satellites over time.
 * (Carrier frequency, present on SatelliteCorrelationKey, is signal identity and is deliberately
 * excluded here.)
 */
data class GnssSatelliteId(val constellationType: Int, val svid: Int)

fun SatelliteCorrelationKey.toSatelliteId(): GnssSatelliteId = GnssSatelliteId(constellationType, svid)

enum class SatelliteMappingStatus { MATCHED, UNMATCHED, AMBIGUOUS, UNVERIFIED }

enum class MappingMethod {
    /** Entry supplied explicitly by the operator/project as data, with a stated source. */
    OPERATOR_SUPPLIED_TABLE,

    /** Candidate inferred from a name or pattern (for example "PRN 13" in an object name). */
    NAME_PATTERN_INFERRED
}

/** Confidence is categorical on purpose: no numeric score is invented. */
enum class MappingConfidence { VERIFIED, UNVERIFIED }

data class MappingProvenance(
    val source: String,
    val method: MappingMethod,
    val confidence: MappingConfidence,
    val evidence: String,
    /** Inclusive start of validity (device/catalog UTC ms), or null when unbounded. */
    val validFromUtcMs: Long?,
    /** Exclusive end of validity, or null when unbounded. */
    val validToUtcMs: Long?
) {
    fun isValidAt(utcMs: Long): Boolean =
        (validFromUtcMs == null || utcMs >= validFromUtcMs) && (validToUtcMs == null || utcMs < validToUtcMs)
}

/**
 * Result of resolving constellation+SVID to a NORAD object at a point in time. Only [Matched]
 * exposes a NORAD id that downstream code may use for prediction. Other variants deliberately do
 * not, so a guess cannot be consumed by accident. UNMATCHED is a valid, expected outcome.
 *
 * Distinct from SatelliteEvidenceMatch / NavigationAssociation, which describe navigation-message
 * association and are unrelated to catalog identity.
 */
sealed interface SatelliteIdentityMapping {
    val id: GnssSatelliteId
    val status: SatelliteMappingStatus

    data class Matched(
        override val id: GnssSatelliteId,
        val noradCatId: Long,
        val provenance: MappingProvenance
    ) : SatelliteIdentityMapping {
        override val status: SatelliteMappingStatus get() = SatelliteMappingStatus.MATCHED
    }

    data class Unmatched(
        override val id: GnssSatelliteId,
        val reason: String
    ) : SatelliteIdentityMapping {
        override val status: SatelliteMappingStatus get() = SatelliteMappingStatus.UNMATCHED
    }

    data class Ambiguous(
        override val id: GnssSatelliteId,
        val candidateNoradCatIds: List<Long>,
        val provenance: List<MappingProvenance>
    ) : SatelliteIdentityMapping {
        override val status: SatelliteMappingStatus get() = SatelliteMappingStatus.AMBIGUOUS
    }

    /** A candidate exists but no verified evidence supports it. The candidate must not be predicted. */
    data class Unverified(
        override val id: GnssSatelliteId,
        val candidateNoradCatId: Long,
        val provenance: MappingProvenance
    ) : SatelliteIdentityMapping {
        override val status: SatelliteMappingStatus get() = SatelliteMappingStatus.UNVERIFIED
    }
}

interface SatelliteIdentityResolver {
    fun resolve(id: GnssSatelliteId, at: PropagationTime): SatelliteIdentityMapping
}

data class SatelliteIdentityEntry(
    val id: GnssSatelliteId,
    val noradCatId: Long,
    val provenance: MappingProvenance
)

/**
 * Resolves against an explicit table with temporal validity. No name parsing, no nearest-satellite
 * logic, no default. An empty table resolves everything to UNMATCHED.
 *
 *  - no entry valid at the requested time            -> UNMATCHED
 *  - valid entries naming more than one NORAD id     -> AMBIGUOUS
 *  - exactly one NORAD id, at least one VERIFIED     -> MATCHED (verified provenance)
 *  - exactly one NORAD id, none VERIFIED             -> UNVERIFIED
 */
class TableSatelliteIdentityResolver(entries: List<SatelliteIdentityEntry>) : SatelliteIdentityResolver {
    private val byId: Map<GnssSatelliteId, List<SatelliteIdentityEntry>> = entries.groupBy { it.id }

    override fun resolve(id: GnssSatelliteId, at: PropagationTime): SatelliteIdentityMapping {
        val valid = (byId[id] ?: emptyList()).filter { it.provenance.isValidAt(at.utcMillis) }
        if (valid.isEmpty()) {
            return SatelliteIdentityMapping.Unmatched(id, "no identity table entry valid at ${at.utcMillis}")
        }
        val candidates = valid.map { it.noradCatId }.distinct().sorted()
        if (candidates.size > 1) {
            return SatelliteIdentityMapping.Ambiguous(id, candidates, valid.map { it.provenance })
        }
        val verified = valid.firstOrNull { it.provenance.confidence == MappingConfidence.VERIFIED }
        return if (verified != null) {
            SatelliteIdentityMapping.Matched(id, candidates.first(), verified.provenance)
        } else {
            SatelliteIdentityMapping.Unverified(id, candidates.first(), valid.first().provenance)
        }
    }
}
