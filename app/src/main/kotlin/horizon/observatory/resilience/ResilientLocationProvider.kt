package horizon.observatory.resilience

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.SystemClock

/**
 * Persists the latest snapshot as ONE schema-versioned JSON document (see [ResilientLocationCodec]).
 * Invalid snapshots are refused at publish time; unreadable stored payloads surface as CORRUPT, never
 * as "no snapshot" and never as a coordinate.
 */
class ResilientLocationStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Returns false (and stores nothing) when the snapshot violates the contract. */
    fun publish(snapshot: ResilientLocationSnapshot): Boolean {
        val encoded = try {
            ResilientLocationCodec.encode(snapshot)
        } catch (e: IllegalArgumentException) {
            return false
        }
        return prefs.edit().putString(KEY_SNAPSHOT, encoded).commit()
    }

    fun readResult(): SnapshotDecodeResult = ResilientLocationCodec.decode(prefs.getString(KEY_SNAPSHOT, null))

    /** Convenience for callers that only want a structurally valid snapshot; freshness is NOT checked. */
    fun read(): ResilientLocationSnapshot? = (readResult() as? SnapshotDecodeResult.Valid)?.snapshot

    fun clear() { prefs.edit().remove(KEY_SNAPSHOT).commit() }

    /** Test hook: stores an arbitrary payload verbatim (used to prove CORRUPT handling). */
    internal fun writeRawForTest(raw: String) { prefs.edit().putString(KEY_SNAPSHOT, raw).commit() }

    companion object {
        const val AUTHORITY = "horizon.observatory.resilient-location"
        const val PATH_LATEST = "latest"
        const val READ_PERMISSION = "horizon.observatory.permission.READ_RESILIENT_LOCATION"
        private const val PREFS = "horizon_resilience_location"
        private const val KEY_SNAPSHOT = "latest_snapshot"

        /** Variant-aware identity for installations that are not the legacy primary package. */
        fun authorityForPackage(packageName: String): String = when {
            packageName.startsWith("horizon.observatory.parallel") -> "horizon.observatory.parallel.resilient-location"
            packageName.startsWith("horizon.observatory.ts") -> "horizon.observatory.ts.resilient-location"
            packageName.startsWith("horizon.observatory.t1") -> "horizon.observatory.t1.resilient-location"
            else -> AUTHORITY
        }

        fun readPermissionForPackage(packageName: String): String = when {
            packageName.startsWith("horizon.observatory.parallel") -> "horizon.observatory.parallel.permission.READ_RESILIENT_LOCATION"
            packageName.startsWith("horizon.observatory.ts") -> "horizon.observatory.ts.permission.READ_RESILIENT_LOCATION"
            packageName.startsWith("horizon.observatory.t1") -> "horizon.observatory.t1.permission.READ_RESILIENT_LOCATION"
            else -> READ_PERMISSION
        }
    }
}

/**
 * Explicit Horizon API (NOT an Android system location provider, never a mock provider).
 * Contract: [ResilientLocationContract]. Read-only; protected by [ResilientLocationStore.READ_PERMISSION].
 */
class ResilientLocationProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val ctx = context ?: return null
        if (uri.authority != ResilientLocationStore.authorityForPackage(ctx.packageName) || uri.lastPathSegment != ResilientLocationStore.PATH_LATEST) return null
        val row = ResilientLocationContract.row(ResilientLocationStore(ctx).readResult(), SystemClock.elapsedRealtimeNanos())
        val columns: Array<String> = projection?.let { requested -> Array(requested.size) { requested[it] } } ?: ResilientLocationContract.COLUMNS
        val cursor = MatrixCursor(columns, 1)
        cursor.addRow(Array<Any?>(columns.size) { row[columns[it]] })
        return cursor
    }

    override fun getType(uri: Uri): String? = "vnd.android.cursor.item/vnd.horizon.resilient-location"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
