package horizon.observatory.acquisition.service

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Waits for a stopped source collector to finish draining its closed flow before cancelling it.
 *
 * A successful callbackFlow.trySend only means the channel accepted an element. If the collector
 * is cancelled immediately after the source closes, buffered elements may never reach persistence.
 * The timeout is a hard bound; a false result means cancellation was required and final source
 * diagnostics must not be reported as complete.
 */
internal suspend fun joinSourceJobGracefully(job: Job, timeoutMs: Long): Boolean {
    require(timeoutMs > 0L) { "timeoutMs must be positive" }
    val completed = withTimeoutOrNull(timeoutMs) {
        job.join()
        true
    } ?: false
    if (!completed) job.cancelAndJoin()
    return completed
}
