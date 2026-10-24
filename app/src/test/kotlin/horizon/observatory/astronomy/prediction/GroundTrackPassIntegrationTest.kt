package horizon.observatory.astronomy.prediction

import horizon.observatory.astronomy.CatalogStaleness
import horizon.observatory.astronomy.CatalogedOrbit
import horizon.observatory.astronomy.CoordinateTransforms
import horizon.observatory.astronomy.ObserverLocation
import horizon.observatory.astronomy.OmmParser
import horizon.observatory.astronomy.OrbitDataSource
import horizon.observatory.astronomy.OrbitProvenance
import horizon.observatory.astronomy.StalenessPolicy
import horizon.observatory.astronomy.geo.Geodetic
import horizon.observatory.astronomy.identity.GeometricIdentityAssistant
import horizon.observatory.astronomy.identity.GnssSatelliteId
import horizon.observatory.astronomy.identity.HintStatus
import horizon.observatory.astronomy.identity.ObservedDirection
import horizon.observatory.astronomy.identity.MappingConfidence
import horizon.observatory.astronomy.identity.MappingMethod
import horizon.observatory.astronomy.identity.MappingProvenance
import horizon.observatory.astronomy.identity.SatelliteIdentityEntry
import horizon.observatory.astronomy.identity.TableSatelliteIdentityResolver
import horizon.observatory.astronomy.propagation.BackendResult
import horizon.observatory.astronomy.propagation.BackendState
import horizon.observatory.astronomy.propagation.PropagationModelFamily
import horizon.observatory.astronomy.propagation.Sgp4Sdp4Backend
import horizon.observatory.astronomy.propagation.Sgp4Sdp4Input
import horizon.observatory.astronomy.propagation.Sgp4Sdp4PropagationEngine
import horizon.observatory.astronomy.propagation.UnavailableSgp4Sdp4Backend
import horizon.observatory.astronomy.time.DeviceClockUtc
import horizon.observatory.astronomy.time.PropagationTime
import horizon.observatory.astronomy.time.RetrievalTimeUtcMs
import horizon.observatory.astronomy.time.TimeBasis
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chain integration test (use case -> GroundTrackSampler / PassSampler) against a TEST DOUBLE backend
 * that flies a circular 55° orbit (R = 26 560 km) analytically. Not an SGP4 accuracy test: it proves the
 * samplers, frame chain, geodetic conversion and pass search agree with closed-form geometry.
 * A real-SGP4 run needs the orbitcore backend on the unit-test classpath (t1 flavor only today).
 */
class GroundTrackPassIntegrationTest {
    private val gps13 = GnssSatelliteId(1, 13)
    private val record = OmmParser.parse(
        """[{"OBJECT_NAME":"TEST SAT","NORAD_CAT_ID":4242,"EPOCH":"2025-12-31T23:00:00","MEAN_MOTION":2.0,
        "ECCENTRICITY":0.01,"INCLINATION":55.0,"RA_OF_ASC_NODE":1.0,"ARG_OF_PERICENTER":1.0,"MEAN_ANOMALY":1.0,
        "BSTAR":0.0001}]"""
    ).records.single()
    private val epochMs = record.epochUtc.toEpochMilli()
    private val radiusKm = 26_560.0
    private val inc = Math.toRadians(55.0)
    private val rateRadS = sqrt(398_600.4418 / radiusKm.pow(3))

    private class CircularBackend(val r: Double, val inc: Double, val n: Double) : Sgp4Sdp4Backend {
        override val name = "TEST_DOUBLE_CIRCULAR"
        override val version: String? = null
        override val supportsDeepSpace = true
        override fun propagate(input: Sgp4Sdp4Input, minutesSinceEpoch: Double): BackendResult {
            val u = n * minutesSinceEpoch * 60.0
            val v = r * n
            return BackendResult.Ok(
                BackendState(
                    r * cos(u), r * sin(u) * cos(inc), r * sin(u) * sin(inc),
                    -v * sin(u), v * cos(u) * cos(inc), v * cos(u) * sin(inc)
                ),
                PropagationModelFamily.SDP4_DEEP_SPACE
            )
        }
    }

    private class FakeOrbits(private val orbits: Map<Long, CatalogedOrbit>) : OrbitDataSource {
        override suspend fun getOrbit(noradCatId: Long) = orbits[noradCatId]
        override suspend fun getAllOrbits() = orbits.values.toList()
    }

    private fun orbitSource(): OrbitDataSource {
        val orbit = CatalogedOrbit(record, OrbitProvenance("TEST_CATALOG", "https://example.invalid", "OMM_JSON", RetrievalTimeUtcMs(epochMs - 60_000L)))
        return FakeOrbits(mapOf(4242L to orbit))
    }

    private fun useCaseOver(source: OrbitDataSource): SatellitePredictionUseCase {
        val prov = MappingProvenance("test-table", MappingMethod.OPERATOR_SUPPLIED_TABLE, MappingConfidence.VERIFIED, "unit-test", null, null)
        return SatellitePredictionUseCase(
            source,
            TableSatelliteIdentityResolver(listOf(SatelliteIdentityEntry(gps13, 4242L, prov))),
            Sgp4Sdp4PropagationEngine(CircularBackend(radiusKm, inc, rateRadS)),
            DeviceClockUtc { epochMs },
            StalenessPolicy(maxDataAgeMs = 3_600_000L, maxEpochAgeMs = 86_400_000L),
            Dispatchers.Unconfined
        )
    }

    private fun useCase(): SatellitePredictionUseCase {
        val orbit = CatalogedOrbit(record, OrbitProvenance("TEST_CATALOG", "https://example.invalid", "OMM_JSON", RetrievalTimeUtcMs(epochMs - 60_000L)))
        val prov = MappingProvenance("test-table", MappingMethod.OPERATOR_SUPPLIED_TABLE, MappingConfidence.VERIFIED, "unit-test", null, null)
        return SatellitePredictionUseCase(
            FakeOrbits(mapOf(4242L to orbit)),
            TableSatelliteIdentityResolver(listOf(SatelliteIdentityEntry(gps13, 4242L, prov))),
            Sgp4Sdp4PropagationEngine(CircularBackend(radiusKm, inc, rateRadS)),
            DeviceClockUtc { epochMs },
            StalenessPolicy(maxDataAgeMs = 3_600_000L, maxEpochAgeMs = 86_400_000L),
            Dispatchers.Unconfined
        )
    }

    private val observer0 = ObserverPosition(ObserverLocation(0.0, 0.0, 0.0), ObserverSource.USER_SUPPLIED, null)

    private fun angularSeparationDeg(a: TrackPoint, b: TrackPoint): Double {
        val la = Math.toRadians(a.latDeg); val lb = Math.toRadians(b.latDeg)
        val c = sin(la) * sin(lb) + cos(la) * cos(lb) * cos(Math.toRadians(a.lonDeg - b.lonDeg))
        return Math.toDegrees(acos(c.coerceIn(-1.0, 1.0)))
    }

    @Test
    fun `ground track follows closed-form geometry`() = runBlocking {
        val res = GroundTrackSampler.sample(useCase(), listOf(gps13), observer0, epochMs)
        val pts = res.tracks.single().points
        assertEquals(GroundTrackSampler.DEFAULT_OFFSETS_MIN, pts.map { it.offsetMin })
        assertEquals(1, res.statusNow[PredictionStatus.PREDICTED])
        pts.forEach {
            assertTrue("lat ${it.latDeg}", abs(it.latDeg) <= 55.3)            // geodetic ≤ inclination + flattening effect
            assertTrue("alt ${it.altKm}", it.altKm in 20_150.0..20_230.0)     // R − Earth radius
        }
        pts.zipWithNext().forEach { (a, b) -> assertTrue(angularSeparationDeg(a, b) < 4.0) } // 5 min ≈ 2.5° orbit + 1.25° Earth rotation
    }

    @Test
    fun `overhead pass is found with culmination at the zenith time`() = runBlocking {
        val uc = useCase()
        val overhead = GroundTrackSampler.sample(uc, listOf(gps13), observer0, epochMs).tracks.single().points.first { it.offsetMin == 60 }
        val observer = ObserverPosition(ObserverLocation(overhead.latDeg, overhead.lonDeg, 0.0), ObserverSource.USER_SUPPLIED, null)
        val res = PassSampler.nextPasses(uc, listOf(gps13), observer, epochMs, windowHours = 3, maskDeg = 10.0)
        val pass = res.satellites.single().passes.first()
        assertTrue("maxEl ${pass.maxElDeg}", pass.maxElDeg > 89.5)
        assertTrue(abs(pass.culminationMs - (epochMs + 60 * 60_000L)) <= 30_000L)
    }

    @Test
    fun `unidentified satellite yields no track and says why`() = runBlocking {
        val res = GroundTrackSampler.sample(useCase(), listOf(GnssSatelliteId(1, 99)), observer0, epochMs)
        assertTrue(res.tracks.isEmpty())
        assertNotNull(res.statusNow.keys.firstOrNull())
        assertTrue(res.statusNow[PredictionStatus.PREDICTED] == null)
    }

    @Test
    fun `geometric identity assistant recovers the catalog object from its own predicted direction`() = runBlocking {
        val uc = useCase()
        val sub = GroundTrackSampler.sample(uc, listOf(gps13), observer0, epochMs).tracks.single().points.first { it.offsetMin == 0 }
        val loc = ObserverLocation(sub.latDeg - 15.0, sub.lonDeg, 0.0)   // satellite ~15° from the zenith: clearly visible
        val geo = uc.predict(
            PredictionRequest(listOf(gps13), ObserverPosition(loc, ObserverSource.USER_SUPPLIED, null), PropagationTime(epochMs, TimeBasis.DEVICE_CLOCK_UTC))
        ).states.single().geometry!!
        val engine = Sgp4Sdp4PropagationEngine(CircularBackend(radiusKm, inc, rateRadS))

        val near = listOf(ObservedDirection(gps13, geo.predictedAzimuthDeg + 0.4, geo.predictedElevationDeg - 0.3))
        val hit = GeometricIdentityAssistant.suggest(orbitSource(), engine, near, loc, epochMs).hints.single()
        assertEquals(HintStatus.CONSISTENT, hit.status)
        assertEquals(4242L, hit.noradCatId)
        assertTrue(hit.separationDeg!! < 1.0)

        val far = listOf(ObservedDirection(gps13, geo.predictedAzimuthDeg + 90.0, 5.0))
        assertEquals(HintStatus.NO_CANDIDATE, GeometricIdentityAssistant.suggest(orbitSource(), engine, far, loc, epochMs).hints.single().status)
    }

    @Test
    fun `views holder publishes Ready after refresh and Failed when the catalog throws`() = runBlocking {
        val holder = PredictedViewsStateHolder(useCase())
        assertTrue(holder.groundTracks.value is PredictedViewState.NotRun)
        holder.refreshGroundTracks(listOf(gps13), observer0, epochMs)
        assertEquals(1, holder.groundTracks.value.readyValue()!!.tracks.size)

        val broken = PredictedViewsStateHolder(useCaseOver(object : OrbitDataSource {
            override suspend fun getOrbit(noradCatId: Long): CatalogedOrbit? = error("boom")
            override suspend fun getAllOrbits(): List<CatalogedOrbit> = error("boom")
        }))
        broken.refreshPasses(listOf(gps13), observer0, epochMs)
        assertTrue(broken.nextPasses.value is PredictedViewState.Failed)
    }
}
