package horizon.observatory.astronomy.catalog

import horizon.observatory.astronomy.OmmParseIssue
import horizon.observatory.astronomy.OmmParser
import horizon.observatory.astronomy.OrbitCatalogStore
import horizon.observatory.astronomy.OrbitProvenance
import horizon.observatory.astronomy.time.RetrievalTimeUtcMs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.cancellation.CancellationException

enum class OrbitIngestOutcome {
    STORED,
    FETCH_HTTP_FAILURE,
    FETCH_TIMEOUT,
    FETCH_EMPTY_PAYLOAD,
    FETCH_PAYLOAD_TOO_LARGE,
    FETCH_NETWORK_FAILURE,
    FETCH_INVALID_REQUEST,
    PARSE_PAYLOAD_REJECTED,
    PARSE_NO_RECORDS_ACCEPTED,
    STORE_FAILED
}

/** Local diagnostics for one ingest attempt. Never uploaded anywhere. */
data class OrbitCatalogIngestReport(
    val source: String,
    val sourceIdentifier: String,
    val outcome: OrbitIngestOutcome,
    val httpStatus: Int?,
    val completedAt: RetrievalTimeUtcMs,
    val parsedAccepted: Int,
    val parsedSkipped: Int,
    val warnings: List<OmmParseIssue>,
    val errors: List<OmmParseIssue>,
    val stored: Int,
    val keptExistingNewerEpoch: Int,
    val failureReason: String?
)

/**
 * fetch -> parse -> persist. Storage is only touched after a payload was fetched AND at least one
 * record was accepted, so a failed or empty fetch can never wipe or age-reset existing data.
 */
class OrbitCatalogIngestor(
    private val fetcher: OrbitCatalogFetcher,
    private val store: OrbitCatalogStore
) {
    private val lastReportState = MutableStateFlow<OrbitCatalogIngestReport?>(null)
    val lastReport: StateFlow<OrbitCatalogIngestReport?> = lastReportState.asStateFlow()

    suspend fun ingest(request: OrbitFetchRequest): OrbitCatalogIngestReport {
        val report = when (val fetched = fetcher.fetch(request)) {
            is OrbitFetchResult.Success -> parseAndStore(fetched)
            is OrbitFetchResult.HttpFailure ->
                failure(fetched, OrbitIngestOutcome.FETCH_HTTP_FAILURE, fetched.httpStatus, "HTTP status ${fetched.httpStatus}")
            is OrbitFetchResult.Timeout ->
                failure(fetched, OrbitIngestOutcome.FETCH_TIMEOUT, null, fetched.detail)
            is OrbitFetchResult.EmptyPayload ->
                failure(fetched, OrbitIngestOutcome.FETCH_EMPTY_PAYLOAD, fetched.httpStatus, "empty response body")
            is OrbitFetchResult.PayloadTooLarge ->
                failure(fetched, OrbitIngestOutcome.FETCH_PAYLOAD_TOO_LARGE, null, "payload exceeded ${fetched.limitBytes} bytes")
            is OrbitFetchResult.NetworkFailure ->
                failure(fetched, OrbitIngestOutcome.FETCH_NETWORK_FAILURE, null, fetched.detail)
            is OrbitFetchResult.InvalidRequest ->
                failure(fetched, OrbitIngestOutcome.FETCH_INVALID_REQUEST, null, fetched.detail)
        }
        lastReportState.value = report
        return report
    }

    private suspend fun parseAndStore(fetched: OrbitFetchResult.Success): OrbitCatalogIngestReport {
        val parsed = OmmParser.parse(fetched.payload)
        val base = OrbitCatalogIngestReport(
            source = fetched.request.source.name,
            sourceIdentifier = fetched.request.source.url,
            outcome = OrbitIngestOutcome.STORED,
            httpStatus = fetched.httpStatus,
            completedAt = fetched.completedAt,
            parsedAccepted = parsed.acceptedCount,
            parsedSkipped = parsed.skippedCount,
            warnings = parsed.warnings,
            errors = parsed.errors,
            stored = 0,
            keptExistingNewerEpoch = 0,
            failureReason = null
        )
        if (parsed.payloadRejected) {
            return base.copy(
                outcome = OrbitIngestOutcome.PARSE_PAYLOAD_REJECTED,
                failureReason = parsed.errors.firstOrNull()?.message ?: "payload rejected"
            )
        }
        if (parsed.acceptedCount == 0) {
            return base.copy(
                outcome = OrbitIngestOutcome.PARSE_NO_RECORDS_ACCEPTED,
                failureReason = "payload parsed but no record was accepted (${parsed.skippedCount} skipped)"
            )
        }
        val provenance = OrbitProvenance(
            source = fetched.request.source.name,
            sourceIdentifier = fetched.request.source.url,
            format = fetched.request.source.format,
            retrievedAt = fetched.completedAt
        )
        return try {
            val result = store.upsert(parsed.records, provenance)
            base.copy(stored = result.written, keptExistingNewerEpoch = result.keptExistingNewerEpoch)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            base.copy(
                outcome = OrbitIngestOutcome.STORE_FAILED,
                failureReason = "${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    private fun failure(
        fetched: OrbitFetchResult,
        outcome: OrbitIngestOutcome,
        httpStatus: Int?,
        reason: String
    ): OrbitCatalogIngestReport =
        OrbitCatalogIngestReport(
            source = fetched.request.source.name,
            sourceIdentifier = fetched.request.source.url,
            outcome = outcome,
            httpStatus = httpStatus,
            completedAt = fetched.completedAt,
            parsedAccepted = 0,
            parsedSkipped = 0,
            warnings = emptyList(),
            errors = emptyList(),
            stored = 0,
            keptExistingNewerEpoch = 0,
            failureReason = reason
        )
}
