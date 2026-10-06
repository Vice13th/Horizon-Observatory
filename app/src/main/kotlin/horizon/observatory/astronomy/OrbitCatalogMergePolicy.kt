package horizon.observatory.astronomy

data class OrbitMergeDecision(
    val toWrite: List<OmmRecord>,
    val rejectedOlderEpoch: List<Long>
)

/**
 * Decides which incoming records may replace stored ones. A record whose epoch is OLDER than the
 * stored epoch for the same NORAD object is rejected so a stale mirror can never overwrite newer
 * data. An equal epoch is written: it is the same element set re-retrieved, so its retrieval time
 * is legitimately updated. Incoming records are expected to be unique per NORAD id (OmmParser
 * guarantees this).
 */
object OrbitCatalogMergePolicy {
    fun decide(existingEpochMsByNorad: Map<Long, Long>, incoming: List<OmmRecord>): OrbitMergeDecision {
        val toWrite = ArrayList<OmmRecord>()
        val rejected = ArrayList<Long>()
        for (record in incoming) {
            val existing = existingEpochMsByNorad[record.noradCatId]
            if (existing != null && record.epochUtc.toEpochMilli() < existing) {
                rejected.add(record.noradCatId)
            } else {
                toWrite.add(record)
            }
        }
        return OrbitMergeDecision(toWrite, rejected)
    }
}
