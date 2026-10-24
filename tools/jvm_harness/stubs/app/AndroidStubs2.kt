package android.content
import android.database.Cursor
import android.net.Uri
interface SharedPreferences {
    fun getString(key: String, defValue: String?): String?
    fun edit(): Editor
    interface Editor { fun putString(k: String, v: String?): Editor; fun remove(k: String): Editor; fun commit(): Boolean; fun apply() }
}
open class Context { open val filesDir: java.io.File get() = java.io.File(System.getProperty("java.io.tmpdir")); open fun getSharedPreferences(name: String, mode: Int): SharedPreferences = throw NotImplementedError(); companion object { const val MODE_PRIVATE = 0 } }
class ContentValues
abstract class ContentProvider {
    val context: Context? get() = null
    abstract fun onCreate(): Boolean
    abstract fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor?
    abstract fun getType(uri: Uri): String?
    abstract fun insert(uri: Uri, values: ContentValues?): Uri?
    abstract fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int
    abstract fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int
}
