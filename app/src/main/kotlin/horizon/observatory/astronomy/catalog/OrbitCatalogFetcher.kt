package horizon.observatory.astronomy.catalog

import horizon.observatory.astronomy.time.RetrievalTimeUtcMs

/** A public orbit-catalog endpoint. The request carries no device, user or observation data. */
data class OrbitCatalogSource(
    val name: String,
    val url: String,
    val format: String = "OMM_JSON"
)

data class OrbitFetchRequest(
    val source: OrbitCatalogSource,
    val timeoutMs: Long = 15_000L,
    val maxPayloadBytes: Int = 8 * 1024 * 1024
) {
    init {
        require(timeoutMs > 0L) { "timeoutMs must be positive" }
        require(maxPayloadBytes > 0) { "maxPayloadBytes must be positive" }
    }
}

/**
 * Every outcome is explicit. Cancellation is NOT a result: a cancelled fetch rethrows
 * CancellationException so structured concurrency keeps working. [completedAt] is the device-clock
 * time at which the attempt ended; on [Success] it becomes the retrieval time in the provenance.
 */
sealed interface OrbitFetchResult {
    val request: OrbitFetchRequest
    val completedAt: RetrievalTimeUtcMs

    data class Success(
        override val request: OrbitFetchRequest,
        override val completedAt: RetrievalTimeUtcMs,
        val httpStatus: Int,
        val payload: String
    ) : OrbitFetchResult

    data class HttpFailure(
        override val request: OrbitFetchRequest,
        override val completedAt: RetrievalTimeUtcMs,
        val httpStatus: Int
    ) : OrbitFetchResult

    data class Timeout(
        override val request: OrbitFetchRequest,
        override val completedAt: RetrievalTimeUtcMs,
        val detail: String
    ) : OrbitFetchResult

    data class EmptyPayload(
        override val request: OrbitFetchRequest,
        override val completedAt: RetrievalTimeUtcMs,
        val httpStatus: Int
    ) : OrbitFetchResult

    data class PayloadTooLarge(
        override val request: OrbitFetchRequest,
        override val completedAt: RetrievalTimeUtcMs,
        val limitBytes: Int
    ) : OrbitFetchResult

    data class NetworkFailure(
        override val request: OrbitFetchRequest,
        override val completedAt: RetrievalTimeUtcMs,
        val detail: String
    ) : OrbitFetchResult

    data class InvalidRequest(
        override val request: OrbitFetchRequest,
        override val completedAt: RetrievalTimeUtcMs,
        val detail: String
    ) : OrbitFetchResult
}

/** Mockable network boundary. Contains no parsing and no persistence. */
interface OrbitCatalogFetcher {
    suspend fun fetch(request: OrbitFetchRequest): OrbitFetchResult
}

/**
 * Default catalog endpoints. The URL is configuration, UNVERIFIED until a network run confirms it.
 */
object OrbitCatalogSources {
    val CELESTRAK_GPS_OPS = OrbitCatalogSource(
        name = "CELESTRAK_GP",
        url = "https://celestrak.org/NORAD/elements/gp.php?GROUP=gps-ops&FORMAT=json"
    )
}
