package horizon.observatory.acquisition.service

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceShutdownTest {
    @Test
    fun closedSourceCollectorDrainsBufferedItemsBeforeSuccessfulJoin() = runBlocking {
        val channel = Channel<Int>(capacity = 16)
        val received = mutableListOf<Int>()
        val collector = launch {
            for (item in channel) {
                // Keep the collector slower than producer to make buffered work deterministic.
                delay(2)
                received += item
            }
        }
        repeat(12) { index ->
            assertTrue(channel.trySend(index).isSuccess)
        }
        channel.close()

        assertTrue(joinSourceJobGracefully(collector, timeoutMs = 2_000))

        assertEquals((0 until 12).toList(), received)
        assertFalse(collector.isActive)
    }

    @Test
    fun collectorTimeoutCancelsJobAndReportsIncompleteShutdown() = runBlocking {
        val collector = launch { awaitCancellation() }

        assertFalse(joinSourceJobGracefully(collector, timeoutMs = 10))
        assertFalse(collector.isActive)
    }
}
