package horizon.observatory.export.model

data class DatasetManifest(
    val exportTimestampUtcMs: Long,
    val schemaVersion: Int,
    val sessionId: String,
    val deviceManufacturer: String,
    val deviceModel: String,
    val totalObservations: Int,
    val totalSystemEvents: Int,
    val totalErrorEvents: Int,
    val startTimeUtcMs: Long,
    val endTimeUtcMs: Long?,
    val capabilityReportJson: String,
    val permissionStateJson: String,
    val sequenceMin: Long?,
    val sequenceMax: Long?,
    val integrityClean: Boolean,
    val sequenceContiguous: Boolean,
    val ingestionTimestampsNonDecreasing: Boolean,
    val ingestionTimestampedObservationCount: Int,
    val sourceTimestampRegressions: Int,
    val sourceTimestampedObservationCount: Int,
    val provenanceNote: String = "Source measurement time and HORIZON ingestion time are preserved separately. Source timestamps may arrive out of order across asynchronous producers; ingestion order is the integrity clock."
)
