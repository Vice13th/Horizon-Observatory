package horizon.observatory.resilience

enum class ContinuityServiceStatus { STOPPED, STARTING, ACTIVE, FAILED, STOPPING }

enum class ContinuityFailure { PERMISSION_MISSING, LOCATION_SERVICES_OFF, FOREGROUND_START_REJECTED, SESSION_START_FAILED }

/** What the foreground service reports about itself. No coordinates: status only. */
data class ContinuityServiceSnapshot(
    val status: ContinuityServiceStatus = ContinuityServiceStatus.STOPPED,
    val failure: ContinuityFailure? = null,
    /** Last runtime navigation state, or null before the first evaluation. */
    val navigationState: NavigationState? = null
)

data class ContinuityInputs(
    val requested: Boolean,
    val service: ContinuityServiceSnapshot,
    val hasLocationPermission: Boolean,
    val locationServicesEnabled: Boolean,
    /** Time since the user turned the mode on; lets a not-yet-started service read as STARTING. */
    val msSinceRequest: Long? = null
)

enum class ContinuityUiState { OFF, STARTING, ACTIVE, DEGRADED, PERMISSION_REQUIRED, LOCATION_OFF, FAILED, STOPPING }

object ContinuityUiReducer {
    const val START_GRACE_MS = 3_000L

    fun reduce(i: ContinuityInputs): ContinuityUiState {
        val status = i.service.status
        if (status == ContinuityServiceStatus.STOPPING) return ContinuityUiState.STOPPING
        if (!i.requested) return if (status == ContinuityServiceStatus.STOPPED) ContinuityUiState.OFF else ContinuityUiState.STOPPING
        if (!i.hasLocationPermission) return ContinuityUiState.PERMISSION_REQUIRED
        if (!i.locationServicesEnabled) return ContinuityUiState.LOCATION_OFF
        return when (status) {
            ContinuityServiceStatus.FAILED -> ContinuityUiState.FAILED
            ContinuityServiceStatus.STARTING -> ContinuityUiState.STARTING
            ContinuityServiceStatus.STOPPED ->
                if ((i.msSinceRequest ?: Long.MAX_VALUE) < START_GRACE_MS) ContinuityUiState.STARTING else ContinuityUiState.FAILED
            ContinuityServiceStatus.ACTIVE -> when (i.service.navigationState) {
                null, NavigationState.FULL_GNSS -> ContinuityUiState.ACTIVE
                else -> ContinuityUiState.DEGRADED // includes RECOVERY: GNSS is back but not yet trusted
            }
            ContinuityServiceStatus.STOPPING -> ContinuityUiState.STOPPING
        }
    }
}

/** Damps ACTIVE <-> DEGRADED flapping only; every other change (errors, permissions, off) shows at once. */
class ContinuityUiDebouncer(private val holdMs: Long = 2_000L) {
    var shown: ContinuityUiState = ContinuityUiState.OFF
        private set
    private var pending: ContinuityUiState? = null
    private var pendingSinceMs = 0L

    fun update(target: ContinuityUiState, nowMs: Long): ContinuityUiState {
        if (target == shown) { pending = null; return shown }
        val flap = (shown == ContinuityUiState.ACTIVE && target == ContinuityUiState.DEGRADED) ||
            (shown == ContinuityUiState.DEGRADED && target == ContinuityUiState.ACTIVE)
        if (!flap) { shown = target; pending = null; return shown }
        if (pending != target) { pending = target; pendingSinceMs = nowMs }
        if (nowMs - pendingSinceMs >= holdMs) { shown = target; pending = null }
        return shown
    }
}

object ContinuityCopy {
    /** Shown whenever the mode is on. Must stay honest about what this feature cannot do. */
    const val LIMITS =
        "Keeps Horizon's GNSS acquisition running in the foreground. It cannot defeat radio interference " +
            "and does not make other apps use Horizon's data. Short gaps are bridged only by a provisional " +
            "constant-velocity estimate that is labelled as an estimate."

    fun headline(state: ContinuityUiState): String = when (state) {
        ContinuityUiState.OFF -> "Off"
        ContinuityUiState.STARTING -> "Starting"
        ContinuityUiState.ACTIVE -> "Active - GNSS fixes flowing"
        ContinuityUiState.DEGRADED -> "Active - GNSS degraded or lost"
        ContinuityUiState.PERMISSION_REQUIRED -> "Location permission required"
        ContinuityUiState.LOCATION_OFF -> "Location services are off"
        ContinuityUiState.FAILED -> "Could not keep running"
        ContinuityUiState.STOPPING -> "Stopping"
    }
}
