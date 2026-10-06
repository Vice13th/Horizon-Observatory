package horizon.observatory.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import horizon.observatory.astronomy.OrbitCatalogEntity
import kotlinx.coroutines.flow.Flow

/** Query projection used by the merge policy; not a table. */
data class OrbitEpochIndexRow(
    val noradCatId: Long,
    val epochUtcMillis: Long
)

@Dao
interface OrbitCatalogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<OrbitCatalogEntity>)

    @Query("SELECT * FROM orbit_catalog WHERE noradCatId = :noradCatId")
    suspend fun get(noradCatId: Long): OrbitCatalogEntity?

    @Query("SELECT * FROM orbit_catalog")
    suspend fun getAll(): List<OrbitCatalogEntity>

    @Query("SELECT noradCatId, epochUtcMillis FROM orbit_catalog")
    suspend fun getEpochIndex(): List<OrbitEpochIndexRow>

    @Query("SELECT COUNT(*) FROM orbit_catalog")
    suspend fun count(): Int

    @Query("SELECT * FROM orbit_catalog")
    fun observeAll(): Flow<List<OrbitCatalogEntity>>
}
