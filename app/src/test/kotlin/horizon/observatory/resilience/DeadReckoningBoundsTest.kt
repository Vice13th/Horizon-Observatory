package horizon.observatory.resilience

import horizon.observatory.domain.model.ObservationType
import org.junit.Assert.*
import org.junit.Test

/** G4: coasting estimate bounds. Constants are provisional (uncalibrated). */
class DeadReckoningBoundsTest {
    private val bridge = DeadReckoningBridge()
    private fun pvt(lat: Double = 50.0, lon: Double = 8.0, alt: Double? = null, speed: Double? = 10.0, heading: Double? = 0.0, hu: Double = 5.0, vu: Double? = null, ts: Long = 1 * S) =
        TrustedPvt(lat, lon, alt, speed, heading, ts, hu, vu)
    private fun est(p: TrustedPvt, t: Double, imu: Boolean = true, accel: Double? = null) = bridge.estimate(BridgeInput(p, t, accelerationMps2 = accel, imuAvailable = imu))
    private fun ok(r: CoastingResult) = (r as CoastingResult.Estimate).estimate
    private fun why(r: CoastingResult) = (r as CoastingResult.Rejected).reason

    @Test fun validAnchorSpeedAndCourse_projectsAlongTheCourse() {
        val a = ok(est(pvt(speed = 10.0, heading = 0.0), 10.0))
        assertEquals(100.0, PositionConsistencyChecker.distanceM(50.0, 8.0, a.latitudeDeg, a.longitudeDeg), 0.5)
        assertTrue(a.latitudeDeg > 50.0); assertEquals(8.0, a.longitudeDeg, 1e-9)
        assertEquals(1 * S + 10 * S, a.timestampMonotonicNs)
        assertEquals("DEAD_RECKONED_FROM_LAST_TRUSTED_PVT", a.provenance); assertEquals(CoastingPolicy.ESTIMATOR_ID, a.estimator)
        assertEquals(NavigationState.INERTIAL_BRIDGING, a.navigationState)
        val east = ok(est(pvt(speed = 10.0, heading = 90.0), 10.0))
        assertEquals(100.0, PositionConsistencyChecker.distanceM(50.0, 8.0, east.latitudeDeg, east.longitudeDeg), 0.5)
        assertTrue(east.longitudeDeg > 8.0)
    }
    @Test fun withoutImuTheStateLabelIsGnssLost_notInertialBridging() {
        assertEquals(NavigationState.GNSS_LOST, ok(est(pvt(), 5.0, imu = false)).navigationState)
    }
    @Test fun absentSpeed_isRejected() { assertEquals(CoastingRejection.SPEED_UNAVAILABLE, why(est(pvt(speed = null), 5.0))) }
    @Test fun absentOrInvalidHeading_whileMoving_isRejected_neverInvented() {
        assertEquals(CoastingRejection.HEADING_UNAVAILABLE_WHILE_MOVING, why(est(pvt(speed = 10.0, heading = null), 5.0)))
        assertEquals(CoastingRejection.HEADING_UNAVAILABLE_WHILE_MOVING, why(est(pvt(speed = 10.0, heading = Double.NaN), 5.0)))
    }
    @Test fun stationaryWithoutHeading_holdsPosition_andWidensUncertainty_withoutABearing() {
        val a = ok(est(pvt(speed = 0.2, heading = null), 10.0))
        assertEquals(50.0, a.latitudeDeg, 0.0); assertEquals(8.0, a.longitudeDeg, 0.0)
        assertNull(a.bearingDeg); assertNull(a.speedMps)
        val moving = ok(est(pvt(speed = 0.2, heading = 0.0), 10.0))
        assertTrue(a.horizontalUncertaintyM > moving.horizontalUncertaintyM)   // possible displacement is accounted for
    }
    @Test fun invalidElapsedTime_isRejected() {
        for (t in listOf(-0.001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) assertEquals("t=$t", CoastingRejection.INVALID_ELAPSED_TIME, why(est(pvt(), t)))
    }
    @Test fun invalidSpeedAndAcceleration_areRejected() {
        assertEquals(CoastingRejection.INVALID_SPEED, why(est(pvt(speed = -1.0), 5.0)))
        assertEquals(CoastingRejection.INVALID_SPEED, why(est(pvt(speed = Double.NaN), 5.0)))
        assertEquals(CoastingRejection.INVALID_ACCELERATION, why(est(pvt(), 5.0, accel = Double.NaN)))
    }
    @Test fun uncertaintyGrowsMonotonically_andTimeAdvances() {
        var lastU = -1.0; var lastTs = 0L
        for (i in 0..200) {
            val t = i * 0.1
            val e = ok(est(pvt(vu = 3.0, alt = 100.0), t))
            assertTrue("u at t=$t", e.horizontalUncertaintyM >= lastU); assertTrue(e.timestampMonotonicNs >= lastTs)
            assertTrue(e.horizontalUncertaintyM >= 5.0)
            lastU = e.horizontalUncertaintyM; lastTs = e.timestampMonotonicNs
        }
        assertTrue(lastU > 5.0)
    }
    @Test fun maximumAgeExceeded_isRejected() {
        assertTrue(est(pvt(), 20.0) is CoastingResult.Estimate)
        assertEquals(CoastingRejection.MAX_AGE_EXCEEDED, why(est(pvt(), 20.001)))
    }
    @Test fun uncertaintyCeilingExceeded_isRejected() {
        assertEquals(CoastingRejection.UNCERTAINTY_CEILING_EXCEEDED, why(est(pvt(hu = 140.0), 8.0)))
        assertTrue(est(pvt(hu = 5.0), 8.0) is CoastingResult.Estimate)
    }
    @Test fun crossingPlusMinus180Longitude_staysInBounds() {
        val e = ok(est(pvt(lon = 179.9999, speed = 20.0, heading = 90.0), 10.0))
        assertTrue(e.longitudeDeg >= -180.0 && e.longitudeDeg <= 180.0); assertTrue("crossed to the western hemisphere: ${e.longitudeDeg}", e.longitudeDeg < 0.0)
        assertEquals(200.0, PositionConsistencyChecker.distanceM(50.0, 179.9999, e.latitudeDeg, e.longitudeDeg), 1.0)
        val w = ok(est(pvt(lon = -179.9999, speed = 20.0, heading = 270.0), 10.0))
        assertTrue(w.longitudeDeg > 0.0 && w.longitudeDeg <= 180.0)
    }
    @Test fun highLatitudeAndPoleCrossing_staysInBounds() {
        val high = ok(est(pvt(lat = 89.99, speed = 10.0, heading = 0.0), 10.0))
        assertTrue(high.latitudeDeg <= 90.0 && high.longitudeDeg >= -180.0 && high.longitudeDeg <= 180.0)
        assertEquals(100.0, PositionConsistencyChecker.distanceM(89.99, 8.0, high.latitudeDeg, high.longitudeDeg), 1.0)
        val over = ok(est(pvt(lat = 89.9999, speed = 20.0, heading = 0.0), 10.0))
        assertTrue(over.latitudeDeg <= 90.0 && over.latitudeDeg >= -90.0)
        assertEquals(200.0, PositionConsistencyChecker.distanceM(89.9999, 8.0, over.latitudeDeg, over.longitudeDeg), 2.0)
    }
    @Test fun invalidAnchorCoordinates_areRejected() {
        for (p in listOf(pvt(lat = 91.0), pvt(lat = Double.NaN), pvt(lon = 181.0), pvt(hu = -1.0), pvt(hu = Double.POSITIVE_INFINITY), pvt(ts = 0L), pvt(vu = -1.0), pvt(alt = Double.NaN))) {
            assertEquals(CoastingRejection.INVALID_ANCHOR, why(est(p, 1.0)))
        }
    }
    @Test fun noFakeAltitude_carriedOnlyWhenItsUncertaintyIsKnown() {
        assertNull(ok(est(pvt(alt = 100.0, vu = null), 5.0)).altitudeM)
        assertNull(ok(est(pvt(alt = null, vu = 3.0), 5.0)).altitudeM)
        val e = ok(est(pvt(alt = 100.0, vu = 3.0), 5.0))
        assertEquals(100.0, e.altitudeM!!, 0.0); assertEquals(3.0 + 5.0, e.verticalUncertaintyM!!, 1e-9)
        assertNull(ok(est(pvt(alt = 100.0, vu = null), 5.0)).verticalUncertaintyM)
    }
    @Test fun speedAndBearing_areTheCarriedAssumption_notNewData() {
        val e = ok(est(pvt(speed = 7.0, heading = 365.0), 3.0))
        assertEquals(7.0, e.speedMps!!, 1e-9); assertEquals(5.0, e.bearingDeg!!, 1e-9)
    }
    @Test fun deceleration_stopsAndNeverReverses() {
        val e = ok(est(pvt(speed = 10.0, heading = 0.0), 15.0, accel = -2.0))
        assertEquals(25.0, PositionConsistencyChecker.distanceM(50.0, 8.0, e.latitudeDeg, e.longitudeDeg), 0.5)   // v^2 / 2a
        assertEquals(0.0, e.speedMps!!, 1e-9)
    }
    @Test fun policyIsMarkedProvisional() { assertFalse(CoastingPolicy().calibrated) }

    // --- runtime integration ---
    private fun accel(r: ResilienceRuntime, ns: Long) = r.accept(rawObs(ObservationType.SENSOR_ACCEL, "{}", ns))

    @Test fun runtime_refreshesEstimatesAtMostOncePerPeriod_andStopsAtTheAgeCeiling() {
        val r = ResilienceRuntime()
        r.accept(rawObs(ObservationType.GNSS_FIX, fixJson(speed = 2.0, bearing = 0.0), 1 * S))
        val updates = (0..390).mapNotNull { accel(r, 2 * S + it * 100_000_000L) }       // IMU at 10 Hz until t = 41 s
        val estimates = updates.mapNotNull { it.estimate }
        assertTrue("estimate count=${estimates.size}", estimates.size in 10..16)
        for (i in 1 until estimates.size) assertTrue(estimates[i].timestampMonotonicNs - estimates[i - 1].timestampMonotonicNs >= 1 * S)
        assertTrue(estimates.all { it.timestampMonotonicNs - 1 * S <= 20 * S })
        assertTrue(estimates.all { it.horizontalUncertaintyM <= 150.0 && it.estimator == CoastingPolicy.ESTIMATOR_ID })
        assertEquals(listOf(CoastingRejection.MAX_AGE_EXCEEDED), updates.mapNotNull { it.coastingRejection })
        assertEquals(NavigationState.GNSS_LOST, r.navigationState)           // no estimate -> no bridging label
    }
    @Test fun runtime_stopsCoastingWhenGnssReturns_andEmitsNoEstimateWhileCurrent() {
        val r = ResilienceRuntime()
        r.accept(rawObs(ObservationType.GNSS_FIX, fixJson(speed = 2.0, bearing = 0.0), 1 * S))
        for (i in 0..100) accel(r, 2 * S + i * 100_000_000L)                       // loss + coasting until 12 s
        assertEquals(NavigationState.INERTIAL_BRIDGING, r.navigationState)
        r.accept(rawObs(ObservationType.GNSS_FIX, fixJson(speed = 2.0, bearing = 0.0), 13 * S))
        val after = (1..20).mapNotNull { accel(r, 13 * S + it * 100_000_000L) }
        assertTrue(after.none { it.estimate != null })
    }
}
