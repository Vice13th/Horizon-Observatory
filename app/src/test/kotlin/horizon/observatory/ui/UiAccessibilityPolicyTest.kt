package horizon.observatory.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiAccessibilityPolicyTest {
    @Test
    fun satelliteDescriptionIncludesObservedValuesAndFixState() {
        assertEquals(
            "G12 satellite. C/N0: 37.5 dB-Hz. Elevation: 42.0 degrees. " +
                "Azimuth: 185.0 degrees. Position-fix use: reported as used in the position fix.",
            satelliteAccessibilityDescription("G12", 37.5, 42.0, 185.0, true)
        )
    }

    @Test
    fun satelliteDescriptionLabelsUnavailableValuesWithoutInventingNumbers() {
        assertEquals(
            "E8 satellite. C/N0: unavailable. Elevation: unavailable. " +
                "Azimuth: 91.0 degrees. Position-fix use: not reported.",
            satelliteAccessibilityDescription("E8", Double.NaN, null, 91.0, null)
        )
    }
}
