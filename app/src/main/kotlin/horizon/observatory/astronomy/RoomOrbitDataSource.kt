package horizon.observatory.astronomy

import horizon.observatory.storage.room.OrbitCatalogDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomOrbitDataSource(private val dao: OrbitCatalogDao) : OrbitDataSource, OrbitCatalogStore {
    private val writeMutex = Mutex()

    override suspend fun getOrbit(noradCatId: Long): CatalogedOrbit? =
        dao.get(noradCatId)?.toCatalogedOrbit()

    override suspend fun getAllOrbits(): List<CatalogedOrbit> =
        dao.getAll().map { it.toCatalogedOrbit() }

    override suspend fun upsert(records: List<OmmRecord>, provenance: OrbitProvenance): OrbitStoreResult =
        writeMutex.withLock {
            val existing = dao.getEpochIndex().associate { it.noradCatId to it.epochUtcMillis }
            val decision = OrbitCatalogMergePolicy.decide(existing, records)
            dao.upsertAll(decision.toWrite.map { CatalogedOrbit(it, provenance).toEntity() })
            OrbitStoreResult(
                written = decision.toWrite.size,
                keptExistingNewerEpoch = decision.rejectedOlderEpoch.size
            )
        }
}
