package horizon.observatory.astronomy.catalog

import horizon.observatory.astronomy.time.DeviceClockUtc
import horizon.observatory.astronomy.time.RetrievalTimeUtcMs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

/**
 * java.net implementation of [OrbitCatalogFetcher]. Public GET only: no cookies, no credentials, no
 * device or observation data. Redirects are not followed (a 3xx is reported as an HttpFailure).
 *
 * Only https is accepted unless [allowInsecureHttp] is set, which exists for loopback JVM tests.
 * Timeouts are per connect/read operation, not a total deadline. Cancellation is honoured between
 * blocking steps; a read already blocked is bounded by the read timeout.
 *
 * Network execution against the real endpoint is UNVERIFIED in this checkpoint.
 */
class HttpUrlConnectionOrbitCatalogFetcher(
    private val clock: DeviceClockUtc,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val allowInsecureHttp: Boolean = false
) : OrbitCatalogFetcher {

    override suspend fun fetch(request: OrbitFetchRequest): OrbitFetchResult =
        withContext(ioDispatcher) {
            val url: URL = try {
                URL(request.source.url)
            } catch (e: MalformedURLException) {
                return@withContext OrbitFetchResult.InvalidRequest(request, now(), "malformed URL: ${e.message}")
            }
            val scheme = url.protocol.lowercase()
            if (scheme != "https" && !(allowInsecureHttp && scheme == "http")) {
                return@withContext OrbitFetchResult.InvalidRequest(request, now(), "scheme '$scheme' is not allowed")
            }

            ensureActive()
            var connection: HttpURLConnection? = null
            try {
                val opened = url.openConnection() as HttpURLConnection
                connection = opened
                opened.connectTimeout = request.timeoutMs.toInt()
                opened.readTimeout = request.timeoutMs.toInt()
                opened.instanceFollowRedirects = false
                opened.requestMethod = "GET"
                opened.setRequestProperty("Accept", "application/json")

                val status = opened.responseCode
                if (status !in 200..299) {
                    return@withContext OrbitFetchResult.HttpFailure(request, now(), status)
                }
                ensureActive()
                val bytes = readBounded(opened.inputStream, request.maxPayloadBytes)
                    ?: return@withContext OrbitFetchResult.PayloadTooLarge(request, now(), request.maxPayloadBytes)
                val text = String(bytes, Charsets.UTF_8)
                if (text.isBlank()) {
                    return@withContext OrbitFetchResult.EmptyPayload(request, now(), status)
                }
                OrbitFetchResult.Success(request, now(), status, text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SocketTimeoutException) {
                OrbitFetchResult.Timeout(request, now(), e.message ?: "timed out")
            } catch (e: IOException) {
                OrbitFetchResult.NetworkFailure(request, now(), "${e.javaClass.simpleName}: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }

    private fun now(): RetrievalTimeUtcMs = RetrievalTimeUtcMs(clock.nowUtcMillis())

    /** Returns null when the payload exceeds [limit] bytes. */
    private fun readBounded(stream: InputStream, limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        stream.use { input ->
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > limit) return null
                out.write(buffer, 0, n)
            }
        }
        return out.toByteArray()
    }
}
