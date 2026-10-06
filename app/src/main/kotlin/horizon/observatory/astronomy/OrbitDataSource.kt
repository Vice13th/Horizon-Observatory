package horizon.observatory.astronomy

/**
 * Read side of the orbit catalog. Returns [CatalogedOrbit]: the full authoritative record plus
 * provenance, so catalog epoch, retrieval time and source reach every consumer. Implementations
 * must never reset or "refresh" retrieval time on read.
 */
interface OrbitDataSource {
    suspend fun getOrbit(noradCatId: Long): CatalogedOrbit?
    suspend fun getAllOrbits(): List<CatalogedOrbit>
}

data class OrbitStoreResult(
    val written: Int,
    val keptExistingNewerEpoch: Int
)

/** Write side of the orbit catalog. */
interface OrbitCatalogStore {
    suspend fun upsert(records: List<OmmRecord>, provenance: OrbitProvenance): OrbitStoreResult
}
