package horizon.observatory.core.time

import android.os.SystemClock

data class MonotonicTimestamp(
    val utcTimestampMs: Long,
    val elapsedRealtimeNanos: Long
)

object TimestampEngine {
    private val baseUtcMs: Long = System.currentTimeMillis()
    private val baseElapsedNanos: Long = SystemClock.elapsedRealtimeNanos()

    fun now(): MonotonicTimestamp {
        val elapsed = SystemClock.elapsedRealtimeNanos()
        return MonotonicTimestamp(
            utcTimestampMs = elapsedNanosToUtcMillis(elapsed),
            elapsedRealtimeNanos = elapsed
        )
    }

    fun currentUtcMillis(): Long = now().utcTimestampMs

    fun elapsedNanosToUtcMillis(elapsedRealtimeNanos: Long): Long {
        val diffNanos = elapsedRealtimeNanos - baseElapsedNanos
        return baseUtcMs + (diffNanos / 1_000_000L)
    }
}
