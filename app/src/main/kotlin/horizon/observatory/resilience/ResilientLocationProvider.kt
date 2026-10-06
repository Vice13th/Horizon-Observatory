package horizon.observatory.resilience

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import org.json.JSONObject


data class ResilientLocationSnapshot(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val altitudeM: Double,
    val horizontalUncertaintyM: Double,
    val verticalUncertaintyM: Double,
    val timestampMonotonicNs: Long,
    val navigationState: NavigationState,
    val provenance: String
)

class ResilientLocationStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun publish(snapshot: ResilientLocationSnapshot) {
        prefs.edit().putString(KEY_SNAPSHOT, JSONObject()
            .put("latitudeDeg", snapshot.latitudeDeg)
            .put("longitudeDeg", snapshot.longitudeDeg)
            .put("altitudeM", snapshot.altitudeM)
            .put("horizontalUncertaintyM", snapshot.horizontalUncertaintyM)
            .put("verticalUncertaintyM", snapshot.verticalUncertaintyM)
            .put("timestampMonotonicNs", snapshot.timestampMonotonicNs)
            .put("navigationState", snapshot.navigationState.name)
            .put("provenance", snapshot.provenance)
            .toString()).apply()
    }

    fun read(): ResilientLocationSnapshot? {
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching {
            val j = JSONObject(raw)
            ResilientLocationSnapshot(
                latitudeDeg = j.getDouble("latitudeDeg"),
                longitudeDeg = j.getDouble("longitudeDeg"),
                altitudeM = j.getDouble("altitudeM"),
                horizontalUncertaintyM = j.getDouble("horizontalUncertaintyM"),
                verticalUncertaintyM = j.getDouble("verticalUncertaintyM"),
                timestampMonotonicNs = j.getLong("timestampMonotonicNs"),
                navigationState = NavigationState.valueOf(j.getString("navigationState")),
                provenance = j.getString("provenance")
            )
        }.getOrNull()
    }

    fun clear() { prefs.edit().remove(KEY_SNAPSHOT).apply() }

    companion object {
        const val AUTHORITY = "horizon.observatory.resilient-location"
        const val PATH_LATEST = "latest"
        const val READ_PERMISSION = "horizon.observatory.permission.READ_RESILIENT_LOCATION"
        private const val PREFS = "horizon_resilience_location"
        private const val KEY_SNAPSHOT = "latest_snapshot"
    }
}

class ResilientLocationProvider : ContentProvider() {
    override fun onCreate(): Boolean = context != null

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        if (uri.authority != ResilientLocationStore.AUTHORITY || uri.lastPathSegment != ResilientLocationStore.PATH_LATEST) return null
        val snapshot = context?.let { ResilientLocationStore(it).read() } ?: return null
        val columns = projection?.map { it }?.toTypedArray() ?: DEFAULT_COLUMNS
        val cursor = MatrixCursor(columns)
        val row = Array<Any?>(columns.size) { index ->
            when (columns[index]) {
                "latitudeDeg" -> snapshot.latitudeDeg
                "longitudeDeg" -> snapshot.longitudeDeg
                "altitudeM" -> snapshot.altitudeM
                "horizontalUncertaintyM" -> snapshot.horizontalUncertaintyM
                "verticalUncertaintyM" -> snapshot.verticalUncertaintyM
                "timestampMonotonicNs" -> snapshot.timestampMonotonicNs
                "navigationState" -> snapshot.navigationState.name
                "provenance" -> snapshot.provenance
                else -> null
            }
        }
        cursor.addRow(row)
        return cursor
    }

    override fun getType(uri: Uri): String? = "vnd.android.cursor.item/vnd.horizon.resilient-location"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private val DEFAULT_COLUMNS = arrayOf(
            "latitudeDeg", "longitudeDeg", "altitudeM", "horizontalUncertaintyM",
            "verticalUncertaintyM", "timestampMonotonicNs", "navigationState", "provenance"
        )
    }
}
