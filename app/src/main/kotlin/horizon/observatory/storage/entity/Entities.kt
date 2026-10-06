package horizon.observatory.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    indices = [Index(value = ["sessionId"], unique = true)]
)
data class SessionEntity(
    @PrimaryKey val sessionId: String,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    val lifecycleState: String = "READY",
    val deviceManufacturer: String = "UNKNOWN",
    val deviceModel: String = "UNKNOWN",
    val androidVersion: String = "UNKNOWN",
    val sdkVersion: Int = 0,
    val applicationVersion: String = "UNKNOWN",
    val schemaVersion: Int = 5,
    @ColumnInfo(defaultValue = "{}") val deviceMetadataJson: String = "{}",
    @ColumnInfo(defaultValue = "{}") val capabilityReportJson: String = "{}",
    @ColumnInfo(defaultValue = "{}") val permissionStateJson: String = "{}"
)

@Entity(
    tableName = "observations",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["timestampUtcMs"]),
        Index(value = ["monotonicTimestampNs"]),
        Index(value = ["ingestionMonotonicTimestampNs"]),
        Index(value = ["type"]),
        Index(value = ["sessionId", "sequenceNumber"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"]
        )
    ]
)
data class ObservationEntity(
    @PrimaryKey(autoGenerate = true) val observationId: Long = 0L,
    val sessionId: String,
    val sequenceNumber: Long,
    val timestampUtcMs: Long,
    val monotonicTimestampNs: Long? = null,
    val ingestionMonotonicTimestampNs: Long? = null,
    val timestampDomain: String = "UNKNOWN",
    val timestampUncertaintyNs: Long? = null,
    val source: String = "UNKNOWN",
    val technology: String = "UNKNOWN",
    val type: String,
    val provider: String,
    val rawPayloadJson: String,
    val normalizedPayloadJson: String = "{}",
    val capabilityState: String = "UNKNOWN",
    @ColumnInfo(defaultValue = "OBSERVED") val evidenceStatus: String = "OBSERVED",
    val provenance: String = "RAW_ACQUISITION"
)

@Entity(
    tableName = "pending_observations",
    primaryKeys = ["sessionId", "sequenceNumber"],
    indices = [Index(value = ["sessionId"])],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"]
        )
    ]
)
data class PendingObservationEntity(
    val sessionId: String,
    val sequenceNumber: Long,
    val timestampUtcMs: Long,
    val monotonicTimestampNs: Long? = null,
    val ingestionMonotonicTimestampNs: Long? = null,
    val timestampDomain: String,
    val timestampUncertaintyNs: Long? = null,
    val source: String,
    val technology: String,
    val type: String,
    val provider: String,
    val rawPayloadJson: String,
    @ColumnInfo(defaultValue = "{}") val normalizedPayloadJson: String = "{}",
    val capabilityState: String,
    val evidenceStatus: String,
    val provenance: String
)
