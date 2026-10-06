package horizon.observatory.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executors

class SessionSequencerTest {
    @Test
    fun everySessionStartsAtOne() {
        val firstSession = SessionSequencer()
        val secondSession = SessionSequencer()
        assertEquals(1L, firstSession.nextSequenceNumber())
        assertEquals(1L, secondSession.nextSequenceNumber())
    }

    @Test
    fun oneSessionHasStrictlyIncreasingSequenceNumbers() {
        val sequencer = SessionSequencer()
        assertArrayEquals(longArrayOf(1, 2, 3, 4), longArrayOf(
            sequencer.nextSequenceNumber(),
            sequencer.nextSequenceNumber(),
            sequencer.nextSequenceNumber(),
            sequencer.nextSequenceNumber()
        ))
    }

    @Test
    fun sequenceAllocationIsThreadSafe() {
        val sequencer = SessionSequencer()
        val executor = Executors.newFixedThreadPool(8)
        try {
            val futures = (1..1000).map {
                executor.submit<Long> { sequencer.nextSequenceNumber() }
            }
            val values = futures.map { it.get() }.sorted()
            assertEquals((1L..1000L).toList(), values)
        } finally {
            executor.shutdownNow()
        }
    }
}
