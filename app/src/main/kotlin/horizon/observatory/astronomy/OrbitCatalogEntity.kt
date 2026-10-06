package horizon.observatory.astronomy

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of the latest ingested element set per NORAD object. Stores the COMPLETE OMM record
 * in source units, plus provenance. Freshness is never stored: it is computed from
 * [retrievedAtUtcMillis] / [epochUtcMillis] at read time, and those two values are never rewritten
 * by a read.
 *
 * [epochRaw] is authoritative (full source precision); [epochUtcMillis] exists for indexing/merge
 * decisions only.
 */
@Entity(tableName = "orbit_catalog")
data class OrbitCatalogEntity(
    @PrimaryKey val noradCatId: Long,
    val objectName: String?,
    val objectId: String?,
    val classification: String?,
    val epochRaw: String,
    val epochUtcMillis: Long,
    val meanMotionRevPerDay: Double,
    val eccentricity: Double,
    val inclinationDeg: Double,
    val raanDeg: Double,
    val argOfPericenterDeg: Double,
    val meanAnomalyDeg: Double,
    val ephemerisType: Int?,
    val elementSetNo: Int?,
    val revAtEpoch: Long?,
    val bstar: Double,
    val meanMotionDot: Double?,
    val meanMotionDdot: Double?,
    val catalogSource: String,
    val catalogSourceIdentifier: String?,
    val catalogFormat: String,
    val retrievedAtUtcMillis: Long
)
