package horizon.observatory.resilience

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-wide status the foreground service reports and the UI observes. Not persisted. */
object ContinuityStatusHub {
    private val _state = MutableStateFlow(ContinuityServiceSnapshot())
    val state: StateFlow<ContinuityServiceSnapshot> = _state

    fun report(status: ContinuityServiceStatus, failure: ContinuityFailure? = null) {
        _state.value = _state.value.copy(status = status, failure = failure)
    }

    fun reportNavigationState(navigationState: NavigationState?) {
        if (_state.value.navigationState != navigationState) _state.value = _state.value.copy(navigationState = navigationState)
    }
}
