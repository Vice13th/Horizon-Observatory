package androidx.room
import kotlin.reflect.KClass
annotation class Entity(val tableName: String = "", val indices: Array<Index> = [], val foreignKeys: Array<ForeignKey> = [], val primaryKeys: Array<String> = [])
annotation class PrimaryKey(val autoGenerate: Boolean = false)
annotation class ColumnInfo(val name: String = "", val index: Boolean = false, val defaultValue: String = "[value-unspecified]")
annotation class Index(val value: Array<String> = [], val name: String = "", val unique: Boolean = false)
annotation class ForeignKey(val entity: KClass<*>, val parentColumns: Array<String>, val childColumns: Array<String>, val onDelete: Int = 1, val onUpdate: Int = 1, val deferred: Boolean = false) {
    companion object { const val NO_ACTION = 1; const val RESTRICT = 2; const val SET_NULL = 3; const val SET_DEFAULT = 4; const val CASCADE = 5 }
}
