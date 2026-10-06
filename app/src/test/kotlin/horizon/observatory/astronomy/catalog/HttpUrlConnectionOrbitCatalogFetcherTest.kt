package horizon.observatory.astronomy.catalog

import horizon.observatory.astronomy.time.DeviceClockUtc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Loopback tests using a minimal raw-socket HTTP responder (java.net only). Exercises the real
 * HttpURLConnection code path on the JVM; it does NOT touch the real catalog endpoint.
 */
class HttpUrlConnectionOrbitCatalogFetcherTest {

    private class TinyServer(private val handler: (Socket) -> Unit) {
        val server = ServerSocket(0, 10, InetAddress.getLoopbackAddress())
        @Volatile private var running = true
        private val worker = thread(isDaemon = true) {
            while (running) {
                val socket = try { server.accept() } catch (e: Exception) { break }
                thread(isDaemon = true) { try { handler(socket) } catch (e: Exception) { } }
            }
        }
        val url: String get() = "http://127.0.0.1:${server.localPort}/gp"
        fun close() { running = false; try { server.close() } catch (e: Exception) { } }
    }

    private fun readRequest(socket: Socket) {
        val input = socket.getInputStream()
        val buf = ByteArrayOutputStream()
        var last4 = 0
        while (true) {
            val b = input.read()
            if (b < 0) break
            buf.write(b)
            last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) break
        }
    }

    private fun respond(socket: Socket, status: String, body: String) {
        readRequest(socket)
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply { write(head.toByteArray()); write(bytes); flush() }
        socket.close()
    }

    private var tiny: TinyServer? = null
    private val clock = DeviceClockUtc { 1_234_567L }
    private val fetcher = HttpUrlConnectionOrbitCatalogFetcher(clock, Dispatchers.IO, allowInsecureHttp = true)

    @Before fun setUp() { }
    @After fun tearDown() { tiny?.close() }

    private fun start(handler: (Socket) -> Unit): OrbitFetchRequest {
        val s = TinyServer(handler); tiny = s
        return OrbitFetchRequest(OrbitCatalogSource("TEST", s.url), timeoutMs = 400L)
    }

    @Test
    fun `http 200 returns payload status and retrieval time`() = runBlocking {
        val req = start { respond(it, "200 OK", "[{\"a\":1}]") }
        val r = fetcher.fetch(req)
        assertTrue(r is OrbitFetchResult.Success)
        r as OrbitFetchResult.Success
        assertEquals(200, r.httpStatus)
        assertEquals("[{\"a\":1}]", r.payload)
        assertEquals(1_234_567L, r.completedAt.value)
    }

    @Test
    fun `non-success http status is an explicit HttpFailure`() = runBlocking {
        val req = start { respond(it, "503 Service Unavailable", "busy") }
        val r = fetcher.fetch(req)
        assertTrue(r is OrbitFetchResult.HttpFailure)
        assertEquals(503, (r as OrbitFetchResult.HttpFailure).httpStatus)
    }

    @Test
    fun `redirect is reported not followed`() = runBlocking {
        val req = start { respond(it, "302 Found", "") }
        val r = fetcher.fetch(req)
        assertTrue(r is OrbitFetchResult.HttpFailure)
        assertEquals(302, (r as OrbitFetchResult.HttpFailure).httpStatus)
    }

    @Test
    fun `empty body is an explicit EmptyPayload`() = runBlocking {
        val req = start { respond(it, "200 OK", "") }
        assertTrue(fetcher.fetch(req) is OrbitFetchResult.EmptyPayload)
    }

    @Test
    fun `server that never answers yields Timeout`() = runBlocking {
        val req = start { readRequest(it); Thread.sleep(2_000) }
        assertTrue(fetcher.fetch(req) is OrbitFetchResult.Timeout)
    }

    @Test
    fun `connection refused yields NetworkFailure`() = runBlocking {
        val s = TinyServer { it.close() }
        val url = s.url
        s.close()
        val r = fetcher.fetch(OrbitFetchRequest(OrbitCatalogSource("TEST", url), timeoutMs = 400L))
        assertTrue(r is OrbitFetchResult.NetworkFailure || r is OrbitFetchResult.Timeout)
    }

    @Test
    fun `oversized payload is PayloadTooLarge`() = runBlocking {
        val req = start { respond(it, "200 OK", "x".repeat(5_000)) }.copy(maxPayloadBytes = 1_000)
        assertTrue(fetcher.fetch(req) is OrbitFetchResult.PayloadTooLarge)
    }

    @Test
    fun `http scheme is rejected unless insecure mode is enabled`() = runBlocking {
        val strict = HttpUrlConnectionOrbitCatalogFetcher(clock, Dispatchers.IO)
        val r = strict.fetch(OrbitFetchRequest(OrbitCatalogSource("TEST", "http://127.0.0.1:1/gp")))
        assertTrue(r is OrbitFetchResult.InvalidRequest)
    }

    @Test
    fun `malformed url is InvalidRequest`() = runBlocking {
        val r = fetcher.fetch(OrbitFetchRequest(OrbitCatalogSource("TEST", "not a url")))
        assertTrue(r is OrbitFetchResult.InvalidRequest)
    }

    @Test
    fun `cancellation propagates as CancellationException and is not swallowed`() = runBlocking {
        val req = start { readRequest(it); Thread.sleep(1_000); it.close() }.copy(timeoutMs = 10_000L)
        val job = async { fetcher.fetch(req) }
        delay(200)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        var thrown: Throwable? = null
        try { job.await() } catch (e: CancellationException) { thrown = e }
        assertTrue(thrown is CancellationException)
    }
}
