package horizon.observatory.domain.model

import java.util.concurrent.atomic.AtomicLong

class SessionSequencer(startAt: Long = 1L) {
    private val next = AtomicLong(startAt)

    fun nextSequenceNumber(): Long {
        val value = next.getAndIncrement()
        check(value > 0) { "Session sequence overflow" }
        return value
    }
}
