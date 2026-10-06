package horizon.observatory.resilience

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

class ResilienceModeControllersInstrumentedTest {
    @Test fun emergencyNavigation_persistsAcrossControllerInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = EmergencyNavigationController(context)
        first.updateEnabled(true)
        assertTrue(EmergencyNavigationController(context).enabled)
        first.updateEnabled(false)
        assertFalse(EmergencyNavigationController(context).enabled)
    }

    @Test fun receptionOptimization_isIndependentFromEmergencyNavigation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val emergency = EmergencyNavigationController(context)
        val reception = ReceptionOptimizationController(context)
        emergency.updateEnabled(true)
        reception.updateEnabled(false)
        assertTrue(emergency.enabled)
        assertFalse(reception.enabled)
        emergency.updateEnabled(false)
    }
}
