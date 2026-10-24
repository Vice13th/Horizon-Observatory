package horizon.observatory.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiLayoutPolicyTest {
    @Test
    fun metricColumns_adaptsToWidthAndFontScale() {
        assertEquals(3, metricColumnsForWidth(widthDp = 600f, fontScale = 1f))
        assertEquals(2, metricColumnsForWidth(widthDp = 393f, fontScale = 1f))
        assertEquals(2, metricColumnsForWidth(widthDp = 320f, fontScale = 1f))
        assertEquals(1, metricColumnsForWidth(widthDp = 299f, fontScale = 1f))
        assertEquals(2, metricColumnsForWidth(widthDp = 600f, fontScale = 1.5f))
        assertEquals(1, metricColumnsForWidth(widthDp = 600f, fontScale = 2f))
    }

    @Test
    fun satelliteColumns_adaptsToWidthAndFontScale() {
        assertEquals(3, satelliteColumnsForWidth(widthDp = 600f, fontScale = 1f))
        assertEquals(2, satelliteColumnsForWidth(widthDp = 393f, fontScale = 1f))
        assertEquals(2, satelliteColumnsForWidth(widthDp = 320f, fontScale = 1f))
        assertEquals(1, satelliteColumnsForWidth(widthDp = 299f, fontScale = 1f))
        assertEquals(2, satelliteColumnsForWidth(widthDp = 600f, fontScale = 1.5f))
        assertEquals(1, satelliteColumnsForWidth(widthDp = 600f, fontScale = 2f))
    }

    @Test
    fun layoutPolicies_areConservativeForInvalidInputs() {
        assertEquals(1, metricColumnsForWidth(widthDp = 0f, fontScale = 1f))
        assertEquals(1, metricColumnsForWidth(widthDp = 600f, fontScale = Float.NaN))
        assertEquals(1, satelliteColumnsForWidth(widthDp = Float.NaN, fontScale = 1f))
    }

    @Test
    fun deviceIdentity_usesRuntimeValuesBeforeFirstSession() {
        assertEquals(
            DeviceIdentityDisplay("samsung", "SM-A075F", "16", 36),
            deviceIdentityDisplay(
                recordedManufacturer = null,
                recordedModel = null,
                recordedAndroidRelease = null,
                recordedApiLevel = null,
                runtimeManufacturer = "samsung",
                runtimeModel = "SM-A075F",
                runtimeAndroidRelease = "16",
                runtimeApiLevel = 36
            )
        )
    }

    @Test
    fun deviceIdentity_prefersRecordedSnapshotButRejectsUnknownPlaceholders() {
        assertEquals(
            DeviceIdentityDisplay("Google", "Pixel", "15", 35),
            deviceIdentityDisplay(
                recordedManufacturer = "Google",
                recordedModel = "Pixel",
                recordedAndroidRelease = "15",
                recordedApiLevel = 35,
                runtimeManufacturer = "samsung",
                runtimeModel = "SM-A075F",
                runtimeAndroidRelease = "16",
                runtimeApiLevel = 36
            )
        )
        assertEquals(
            DeviceIdentityDisplay("samsung", "SM-A075F", "16", 36),
            deviceIdentityDisplay(
                recordedManufacturer = "UNKNOWN",
                recordedModel = " ",
                recordedAndroidRelease = null,
                recordedApiLevel = 0,
                runtimeManufacturer = "samsung",
                runtimeModel = "SM-A075F",
                runtimeAndroidRelease = "16",
                runtimeApiLevel = 36
            )
        )
    }
}
