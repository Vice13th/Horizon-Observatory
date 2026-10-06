package horizon.observatory.astronomy.frames

import horizon.observatory.astronomy.CoordinateTransforms
import horizon.observatory.astronomy.EcefState
import horizon.observatory.astronomy.ObserverLocation
import horizon.observatory.astronomy.propagation.TemeState
import horizon.observatory.astronomy.time.PropagationTime
import horizon.observatory.astronomy.time.TimeBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Geometric-identity tests: expected values follow from construction (a satellite placed exactly
 * above / beside the observer), not from any published ephemeris. They verify the three frame
 * stages are wired consistently; they do not validate orbit prediction.
 */
class PredictionFramePipelineTest {
    private val t = PropagationTime(1_767_225_600_000L, TimeBasis.DEVICE_CLOCK_UTC)

    /** Inverse of the GMST rotation applied by temeToEcef, used only to build test inputs. */
    private fun temeFromEcef(ecef: EcefState): TemeState {
        val g = CoordinateTransforms.gmstRad(t.utcMillis)
        return TemeState(
            xKm = ecef.xKm * cos(g) - ecef.yKm * sin(g),
            yKm = ecef.xKm * sin(g) + ecef.yKm * cos(g),
            zKm = ecef.zKm,
            vxKmS = 0.0, vyKmS = 0.0, vzKmS = 0.0,
            atTime = t
        )
    }

    @Test
    fun `temeToEcef inverts the rotation used to build the input`() {
        val ecef = EcefState(7000.0, 1200.0, -800.0, 0.0, 0.0, 0.0)
        val back = PredictionFramePipeline.temeToEcef(temeFromEcef(ecef))
        assertEquals(ecef.xKm, back.xKm, 1e-9)
        assertEquals(ecef.yKm, back.yKm, 1e-9)
        assertEquals(ecef.zKm, back.zKm, 1e-9)
    }

    @Test
    fun `satellite directly above the observer is at elevation 90 with range equal to height`() {
        val observer = ObserverLocation(0.0, 0.0, 0.0)
        val obsEcef = observer.toEcef()
        val sat = EcefState(obsEcef.xKm + 500.0, obsEcef.yKm, obsEcef.zKm, 0.0, 0.0, 0.0)
        val ecef = PredictionFramePipeline.temeToEcef(temeFromEcef(sat))
        val angles = PredictionFramePipeline.enuToAzElRange(PredictionFramePipeline.ecefToEnu(observer, ecef))
        assertEquals(90.0, angles.elevationDeg, 1e-6)
        assertEquals(500.0, angles.rangeKm, 1e-6)
    }

    @Test
    fun `satellite on the far side of the Earth is below the horizon`() {
        val observer = ObserverLocation(0.0, 0.0, 0.0)
        val sat = EcefState(-(6378.137 + 500.0), 0.0, 0.0, 0.0, 0.0, 0.0)
        val ecef = PredictionFramePipeline.temeToEcef(temeFromEcef(sat))
        val angles = PredictionFramePipeline.enuToAzElRange(PredictionFramePipeline.ecefToEnu(observer, ecef))
        assertTrue(angles.elevationDeg < 0.0)
    }

    @Test
    fun `enu axes map to the cardinal azimuths`() {
        assertEquals(0.0, PredictionFramePipeline.enuToAzElRange(EnuVectorKm(0.0, 1.0, 0.0)).azimuthDeg, 1e-9)
        assertEquals(90.0, PredictionFramePipeline.enuToAzElRange(EnuVectorKm(1.0, 0.0, 0.0)).azimuthDeg, 1e-9)
        assertEquals(180.0, PredictionFramePipeline.enuToAzElRange(EnuVectorKm(0.0, -1.0, 0.0)).azimuthDeg, 1e-9)
        assertEquals(270.0, PredictionFramePipeline.enuToAzElRange(EnuVectorKm(-1.0, 0.0, 0.0)).azimuthDeg, 1e-9)
    }

    @Test
    fun `enu angles carry elevation and range`() {
        val a = PredictionFramePipeline.enuToAzElRange(EnuVectorKm(0.0, 3.0, 4.0))
        assertEquals(5.0, a.rangeKm, 1e-12)
        assertEquals(Math.toDegrees(Math.asin(0.8)), a.elevationDeg, 1e-9)
        assertEquals(0.0, a.azimuthDeg, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero range is rejected instead of inventing angles`() {
        PredictionFramePipeline.enuToAzElRange(EnuVectorKm(0.0, 0.0, 0.0))
    }
}
