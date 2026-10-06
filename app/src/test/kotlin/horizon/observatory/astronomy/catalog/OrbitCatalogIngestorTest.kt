package horizon.observatory.astronomy.catalog

import horizon.observatory.astronomy.OmmRecord
import horizon.observatory.astronomy.OrbitCatalogMergePolicy
import horizon.observatory.astronomy.OrbitCatalogStore
import horizon.observatory.astronomy.OrbitProvenance
import horizon.observatory.astronomy.OrbitStoreResult
import horizon.observatory.astronomy.time.RetrievalTimeUtcMs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitCatalogIngestorTest {
    private val request = OrbitFetchRequest(OrbitCatalogSource("TEST", "https://example.invalid/gp"))
    private val at = RetrievalTimeUtcMs(42L)

    private class FakeFetcher(private val result: (OrbitFetchRequest) -> OrbitFetchResult) : OrbitCatalogFetcher {
        override suspend fun fetch(request: OrbitFetchRequest): OrbitFetchResult = result(request)
    }

    private class FakeStore : OrbitCatalogStore {
        val epochs = HashMap<Long, Long>()
        val provenance = HashMap<Long, OrbitProvenance>()
        var calls = 0
        var failWith: Exception? = null
        override suspend fun upsert(records: List<OmmRecord>, provenance: OrbitProvenance): OrbitStoreResult {
            calls++
            failWith?.let { throw it }
            val d = OrbitCatalogMergePolicy.decide(epochs, records)
            d.toWrite.forEach { epochs[it.noradCatId] = it.epochUtc.toEpochMilli(); this.provenance[it.noradCatId] = provenance }
            return OrbitStoreResult(d.toWrite.size, d.rejectedOlderEpoch.size)
        }
    }

    private fun payload(vararg ids: Long) = ids.joinToString(prefix = "[", postfix = "]") {
        """{"NORAD_CAT_ID":$it,"EPOCH":"2026-01-01T00:00:00","MEAN_MOTION":2.0,"ECCENTRICITY":0.01,"INCLINATION":55.0,
        "RA_OF_ASC_NODE":1.0,"ARG_OF_PERICENTER":1.0,"MEAN_ANOMALY":1.0,"BSTAR":0.0001}"""
    }

    private fun ingest(result: (OrbitFetchRequest) -> OrbitFetchResult, store: FakeStore = FakeStore()) =
        runBlocking { OrbitCatalogIngestor(FakeFetcher(result), store).ingest(request) } to store

    @Test
    fun `success stores records with retrieval provenance`() {
        val (report, store) = ingest({ OrbitFetchResult.Success(it, at, 200, payload(1, 2)) })
        assertEquals(OrbitIngestOutcome.STORED, report.outcome)
        assertEquals(2, report.stored)
        assertEquals(200, report.httpStatus)
        assertEquals(42L, store.provenance.getValue(1L).retrievedAt.value)
        assertEquals("TEST", store.provenance.getValue(1L).source)
    }

    @Test
    fun `http failure never touches the store`() {
        val (report, store) = ingest({ OrbitFetchResult.HttpFailure(it, at, 500) })
        assertEquals(OrbitIngestOutcome.FETCH_HTTP_FAILURE, report.outcome)
        assertEquals(500, report.httpStatus)
        assertEquals(0, store.calls)
    }

    @Test
    fun `timeout empty and network failures are explicit and do not store`() {
        listOf(
            OrbitIngestOutcome.FETCH_TIMEOUT to { r: OrbitFetchRequest -> OrbitFetchResult.Timeout(r, at, "t") },
            OrbitIngestOutcome.FETCH_EMPTY_PAYLOAD to { r: OrbitFetchRequest -> OrbitFetchResult.EmptyPayload(r, at, 200) },
            OrbitIngestOutcome.FETCH_NETWORK_FAILURE to { r: OrbitFetchRequest -> OrbitFetchResult.NetworkFailure(r, at, "n") },
            OrbitIngestOutcome.FETCH_PAYLOAD_TOO_LARGE to { r: OrbitFetchRequest -> OrbitFetchResult.PayloadTooLarge(r, at, 10) },
            OrbitIngestOutcome.FETCH_INVALID_REQUEST to { r: OrbitFetchRequest -> OrbitFetchResult.InvalidRequest(r, at, "i") }
        ).forEach { (expected, make) ->
            val (report, store) = ingest(make)
            assertEquals(expected, report.outcome)
            assertEquals(0, store.calls)
            assertNotNull(report.failureReason)
        }
    }

    @Test
    fun `non-json payload is PARSE_PAYLOAD_REJECTED and does not store`() {
        val (report, store) = ingest({ OrbitFetchResult.Success(it, at, 200, "No GP data found") })
        assertEquals(OrbitIngestOutcome.PARSE_PAYLOAD_REJECTED, report.outcome)
        assertEquals(0, store.calls)
    }

    @Test
    fun `payload with only bad records is PARSE_NO_RECORDS_ACCEPTED`() {
        val (report, store) = ingest({ OrbitFetchResult.Success(it, at, 200, """[{"NORAD_CAT_ID":1}]""") })
        assertEquals(OrbitIngestOutcome.PARSE_NO_RECORDS_ACCEPTED, report.outcome)
        assertEquals(1, report.parsedSkipped)
        assertEquals(0, store.calls)
    }

    @Test
    fun `empty json array is PARSE_NO_RECORDS_ACCEPTED`() {
        val (report, store) = ingest({ OrbitFetchResult.Success(it, at, 200, "[]") })
        assertEquals(OrbitIngestOutcome.PARSE_NO_RECORDS_ACCEPTED, report.outcome)
        assertEquals(0, store.calls)
    }

    @Test
    fun `store exception becomes STORE_FAILED`() {
        val store = FakeStore().apply { failWith = IllegalStateException("disk full") }
        val (report, _) = ingest({ OrbitFetchResult.Success(it, at, 200, payload(1)) }, store)
        assertEquals(OrbitIngestOutcome.STORE_FAILED, report.outcome)
        assertTrue(report.failureReason!!.contains("disk full"))
    }

    @Test
    fun `last report is published`() {
        val store = FakeStore()
        val ingestor = OrbitCatalogIngestor(FakeFetcher { OrbitFetchResult.Success(it, at, 200, payload(1)) }, store)
        runBlocking { ingestor.ingest(request) }
        assertEquals(OrbitIngestOutcome.STORED, ingestor.lastReport.value!!.outcome)
    }

    @Test
    fun `older incoming epoch does not replace stored data and is counted`() {
        val store = FakeStore().apply { epochs[1L] = java.time.Instant.parse("2027-01-01T00:00:00Z").toEpochMilli() }
        val (report, _) = ingest({ OrbitFetchResult.Success(it, at, 200, payload(1)) }, store)
        assertEquals(0, report.stored)
        assertEquals(1, report.keptExistingNewerEpoch)
    }
}
