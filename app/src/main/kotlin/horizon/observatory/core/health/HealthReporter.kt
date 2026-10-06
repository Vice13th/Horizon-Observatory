package horizon.observatory.core.health

import android.util.Log
import horizon.observatory.domain.model.SourceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * New in this round, addressing "سیستم گزارش زنده نداریم" (no live reporting system).
 *
 * There is no Phase 9 UI yet (roadmap Section 38: UI must not own acquisition state, and
 * building it is explicitly gated behind physical acquisition/storage verification). Until
 * that UI exists, "live" here means a structured line written to Logcat on a fixed interval,
 * watchable in real time with:
 *
 *   adb logcat -s HorizonHealth
 *
 * This deliberately mirrors the roadmap's Section 47 health model (state independent from
 * observation content: GNSS AVAILABLE/DEGRADED/UNAVAILABLE, observation count, session state)
 * rather than inventing a different shape now that would have to be redone for Phase 9.
 */
class HealthReporter(private val scope: CoroutineScope) {

    fun start(intervalMs: Long = 5000L, snapshot: suspend () -> HealthSnapshot): Job =
        scope.launch {
            while (isActive) {
                val s = snapshot()
                Log.i(
                    TAG,
                    "session=${s.sessionId ?: "none"} lifecycleState=${s.lifecycleState ?: "n/a"} " +
                        "gnss=${s.gnssStatus} cellular=${s.cellularStatus} " +
                        "observationCount=${s.observationCount} acquisitionRunning=${s.acquisitionRunning}"
                )
                delay(intervalMs)
            }
        }

    companion object {
        private const val TAG = "HorizonHealth"
    }
}

data class HealthSnapshot(
    val sessionId: String?,
    val lifecycleState: String?,
    val gnssStatus: SourceStatus,
    val cellularStatus: SourceStatus,
    val observationCount: Int,
    val acquisitionRunning: Boolean
)
