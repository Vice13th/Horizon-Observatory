package horizon.observatory.domain.assistance

/**
 * GNSS Assistance evidence -- explicitly NOT a GNSS observation (see task spec). Modeled as its
 * own domain, separate from domain.gnss, so nothing here can be confused with or merged into
 * raw GNSS evidence (GnssMeasurement, GnssStatus, GnssNavigationMessage, GnssClock remain
 * untouched -- see acquisition/gnss/GnssObservationSource.kt, unmodified this round).
 *
 * ANDROID API REALITY (see REPORT): no public Android/AndroidX API exposes SUPL traffic,
 * carrier A-GPS transactions, chipset assistance payloads, or GNSS-HAL assistance state to
 * applications. Nothing in this project currently produces AssistanceEvidence automatically --
 * see AssistanceEvidenceRecorder's own doc. This model exists so that IF a legitimate signal
 * ever becomes available (a documented API, an OEM-specific broadcast, a rooted-device log),
 * it has a correct, provenance-honest place to land -- not to manufacture evidence now.
 */
enum class AssistanceCategory {
    NETWORK,
    CELLULAR,
    GNSS_PROVIDER,
    PLATFORM,
    UNKNOWN
}

enum class AssistanceType {
    TIME,
    EPHEMERIS,
    ALMANAC,
    REFERENCE_LOCATION,
    SERVER_DATA,
    OTHER,
    UNKNOWN
}

/**
 * Distinct from domain.model.ObservationProvenance (which describes whether a stored ROW is
 * raw/normalized/derived data). This describes the epistemic status of the assistance FACT
 * itself: did Horizon actually see it, infer it from other observable evidence, or is it simply
 * unknown whether the platform used assistance at all. Never silently upgraded -- see the
 * recorder's doc.
 */
enum class AssistanceProvenance {
    /** Directly received or extracted by Horizon. Requires a real, named source. */
    OBSERVED,
    /** Explicitly derived from other observable evidence -- the derivation must be inspectable,
     *  not just plausible. */
    INFERRED,
    /** The platform may use assistance internally; Horizon has no direct evidence either way. */
    UNKNOWN
}

data class AssistanceEvidence(
    val eventId: String,
    val category: AssistanceCategory,
    val type: AssistanceType,
    /** Authoritative for correlation/ordering (task rule 13). Null only if truly unavailable. */
    val monotonicTimestampNs: Long?,
    /** Human-readable/session metadata only -- never used for correlation ordering (rule 14). */
    val wallClockTimestampMs: Long?,
    /** When Horizon itself recorded this evidence (ingestion time), distinct from the
     *  assistance event's own timestamp above -- mirrors RawObservation's existing
     *  monotonicTimestampNs vs ingestionMonotonicTimestampNs distinction. */
    val ingestionMonotonicTimestampNs: Long?,
    /** Only populated if an actual validity/age value is known -- never estimated. */
    val validityDurationMs: Long? = null,
    /** Only populated if the platform actually exposed provider metadata. */
    val providerMetadata: String? = null,
    val provenance: AssistanceProvenance,
    /** Any raw/reference fields actually available, as JSON -- null if none. Never a placeholder. */
    val rawMetadataJson: String? = null,
    /** Free-form, only if a real confidence/state signal exists -- null otherwise. */
    val confidenceState: String? = null
)
