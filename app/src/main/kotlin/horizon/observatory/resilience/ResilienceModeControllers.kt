package horizon.observatory.resilience

import android.content.Context

class EmergencyNavigationController(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        private set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    fun updateEnabled(value: Boolean) { enabled = value }

    companion object {
        private const val PREFS = "horizon_resilience"
        private const val KEY_ENABLED = "emergency_navigation_enabled"
    }
}

class ReceptionOptimizationController(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        private set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    fun updateEnabled(value: Boolean) { enabled = value }

    companion object {
        private const val PREFS = "horizon_resilience"
        private const val KEY_ENABLED = "reception_optimization_enabled"
    }
}

