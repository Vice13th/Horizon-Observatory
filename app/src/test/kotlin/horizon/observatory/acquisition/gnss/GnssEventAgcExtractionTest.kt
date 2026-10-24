package horizon.observatory.acquisition.gnss

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssEventAgcExtractionTest {
    @Test
    fun extractsEventLevelControlsAndTrackingFlag() {
        val extras = extractGnssEventExtras(
            FakeEvent(listOf(FakeAgc(-4.5, 1, 1_575_420_000.0))),
            FakeEvent::class.java
        )

        assertEquals("EVENT_LEVEL", extras.getString("agcApi"))
        assertTrue(extras.getBoolean("fullTracking"))
        val controls = extras.getJSONArray("agcEvents")
        assertEquals(1, controls.length())
        assertEquals(-4.5, controls.getJSONObject(0).getDouble("levelDb"), 0.0)
        assertEquals(1, controls.getJSONObject(0).getInt("constellationType"))
        assertEquals(1_575_420_000.0, controls.getJSONObject(0).getDouble("carrierFrequencyHz"), 0.0)
    }

    @Test
    fun emptyCollectionIsExposedButContainsNoControls() {
        val extras = extractGnssEventExtras(FakeEvent(emptyList<Any>()), FakeEvent::class.java)

        assertEquals("EVENT_LEVEL", extras.getString("agcApi"))
        assertEquals(0, extras.getJSONArray("agcEvents").length())
    }

    @Test
    fun missingAgcMethodDegradesToNotExposed() {
        val extras = extractGnssEventExtras(UnsupportedEvent(), UnsupportedEvent::class.java)

        assertEquals("NOT_EXPOSED", extras.getString("agcApi"))
        assertEquals(0, extras.getJSONArray("agcEvents").length())
        assertEquals(JSONObject.NULL, extras.get("fullTracking"))
    }

    @Test
    fun malformedControlIsSkippedWithoutDiscardingValidControls() {
        val extras = extractGnssEventExtras(
            FakeEvent(listOf(FakeAgc(-3.0, 3, 1_602_000_000.0), MalformedAgc(), null)),
            FakeEvent::class.java
        )

        assertEquals("EVENT_LEVEL", extras.getString("agcApi"))
        val controls = extras.getJSONArray("agcEvents")
        assertEquals(1, controls.length())
        assertEquals(-3.0, controls.getJSONObject(0).getDouble("levelDb"), 0.0)
    }

    @Test
    fun reflectionFailureDegradesToNotExposedWithoutThrowing() {
        val extras = extractGnssEventExtras(ThrowingEvent(), ThrowingEvent::class.java)

        assertEquals("NOT_EXPOSED", extras.getString("agcApi"))
        assertEquals(0, extras.getJSONArray("agcEvents").length())
    }

    @Test
    fun falseTrackingFlagIsPreserved() {
        val extras = extractGnssEventExtras(FakeEvent(emptyList<Any>(), false), FakeEvent::class.java)

        assertFalse(extras.getBoolean("fullTracking"))
    }

    class FakeEvent(private val controls: Collection<*>, private val tracking: Boolean = true) {
        fun getGnssAutomaticGainControls(): Collection<*> = controls
        fun isFullTracking(): Boolean = tracking
    }

    class UnsupportedEvent

    class ThrowingEvent {
        fun getGnssAutomaticGainControls(): Collection<*> = error("simulated reflection target failure")
    }

    class FakeAgc(
        private val levelDb: Double,
        private val constellationType: Int,
        private val carrierFrequencyHz: Double
    ) {
        fun getLevelDb(): Double = levelDb
        fun getConstellationType(): Int = constellationType
        fun getCarrierFrequencyHz(): Double = carrierFrequencyHz
    }

    class MalformedAgc
}
