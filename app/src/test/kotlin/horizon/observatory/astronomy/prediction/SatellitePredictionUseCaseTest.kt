package horizon.observatory.astronomy.prediction

import horizon.observatory.astronomy.CatalogStaleness
import horizon.observatory.astronomy.CatalogedOrbit
import horizon.observatory.astronomy.CoordinateTransforms
import horizon.observatory.astronomy.ObserverLocation
import horizon.observatory.astronomy.OmmParser
import horizon.observatory.astronomy.OrbitDataSource
import horizon.observatory.astronomy.OrbitProvenance
import horizon.observatory.astronomy.StalenessPolicy
import horizon.observatory.astronomy.identity.GnssSatelliteId
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Domain-chain tests. The backend used for the PREDICTED case is a TEST DOUBLE that places a
 * satellite at a constructed point; its numbers have no orbital meaning. These tests prove the
 * chain's control flow, provenance and "never fabricate" rules, not SGP4 accuracy.
 */
class SatellitePredictionUseCaseTest {
    private val gps13 = GnssSatelliteId(1, 13)
    private val gps14 = GnssSatelliteId(1, 14)
    private val now = 1_767_225_600_000L
    private val at = PropagationTime(now, TimeBasis.DEVICE_CLOCK_UTC)
    private val clock = DeviceClockUtc { now }
    private val policy = StalenessPolicy(maxDataAgeMs = 3_600_000L, maxEpochAgeMs = 86_400_000L)
    private val observer = ObserverPosition(ObserverLocation(0.0, 0.0, 0.0), ObserverSource.USER_SUPPLIED, null)

    private val record = OmmParser.parse(
        """[{"OBJECT_NAME":"TEST SAT","NORAD_CAT_ID":4242,"EPOCH":"2025-12-31T23:00:00","MEAN_MOTION":2.0,
        "ECCENTRICITY":0.01,"INCLINATION":55.0,"RA_OF_ASC_NODE":1.0,"ARG_OF_PERICENTER":1.0,"MEAN_ANOMALY":1.0,
        "BSTAR":0.0001}]"""
    ).records.single()

    private fun orbit(retrievedAt: Long = now - 60_000L) =
        CatalogedOrbit(record, OrbitProvenance("TEST_CATALOG", "https://example.invalid", "OMM_JSON", RetrievalTimeUtcMs(retrievedAt)))

    private class FakeOrbits(private val orbits: Map<Long, CatalogedOrbit>) : OrbitDataSource {
        override suspend fun getOrbit(noradCatId: Long) = orbits[noradCatId]
        override suspend fun getAllOrbits() = orbits.values.toList()
    }

    private fun prov(conf: MappingConfidence) =
        MappingProvenance("test-table", MappingMethod.OPERATOR_SUPPLIED_TABLE, conf, "unit-test", null, null)

    private fun singleEntryResolver(conf: MappingConfidence = MappingConfidence.VERIFIED) =
        TableSatelliteIdentityResolver(listOf(SatelliteIdentityEntry(gps13, 4242L, prov(conf))))

    /** TEST DOUBLE: returns a TEME state that maps to a point 500 km above the observer. */
    private class OverheadBackend(private val atUtcMs: Long) : Sgp4Sdp4Backend {
        override val name = "TEST_DOUBLE_OVERHEAD"
        override val version: String? = null
        override val supportsDeepSpace = true
        override fun propagate(input: Sgp4Sdp4Input, minutesSinceEpoch: Double): BackendResult {
            val g = CoordinateTransforms.gmstRad(atUtcMs)
            val x = 6378.137 + 500.0
            return BackendResult.Ok(
                BackendState(x * cos(g), x * sin(g), 0.0, 0.0, 0.0, 0.0),
                PropagationModelFamily.SDP4_DEEP_SPACE
            )
        }
    }

    private fun useCase(
        orbits: Map<Long, CatalogedOrbit> = mapOf(4242L to orbit()),
        resolver: TableSatelliteIdentityResolver = singleEntryResolver(),
        backend: Sgp4Sdp4Backend = OverheadBackend(now)
    ) = SatellitePredictionUseCase(
        FakeOrbits(orbits), resolver, Sgp4Sdp4PropagationEngine(backend), clock, policy, Dispatchers.Unconfined
    )

    private fun predict(uc: SatellitePredictionUseCase, vararg ids: GnssSatelliteId, obs: ObserverPosition? = observer) =
        runBlocking { uc.predict(PredictionRequest(ids.toList(), obs, at)) }

    @Test
    fun `matched satellite with catalog, observer and engine is PREDICTED and structurally derived`() {
        val batch = predict(useCase(), gps13)
        val s = batch.states.single()
        assertEquals(PredictionStatus.PREDICTED, s.status)
        assertEquals(4242L, s.noradCatId)
        val g = s.geometry!!
        assertEquals(90.0, g.predictedElevationDeg, 1e-6)
        assertEquals(500.0, g.predictedRangeKm, 1e-6)
        assertEquals(PropagationModelFamily.SDP4_DEEP_SPACE, g.modelUsed)
        assertEquals(PredictionEvidenceClass.DERIVED_PREDICTED, s.provenance.evidenceClass)
        assertEquals("TEST_CATALOG", s.provenance.catalogSource)
        assertEquals("TEST_DOUBLE_OVERHEAD", s.provenance.engine!!.backendName)
        assertNotNull(s.provenance.identityMapping)
        assertEquals(ObserverSource.USER_SUPPLIED, s.provenance.observerSource)
        assertEquals(TimeBasis.DEVICE_CLOCK_UTC, s.provenance.timeBasis)
        assertEquals(PredictionDisplayState.PREDICTED, s.displayState)
        assertEquals(1, batch.diagnostics.predicted)
    }

    @Test
    fun `freshness and catalog epoch survive into the prediction`() {
        val s = predict(useCase(), gps13).states.single()
        val f = s.freshness!!
        assertEquals(record.epochUtc.toEpochMilli(), f.catalogEpoch.value)
        assertEquals(now - 60_000L, f.retrievedAt.value)
        assertEquals(60_000L, f.dataAgeMs)
        assertEquals(CatalogStaleness.CURRENT, s.staleness)
    }

    @Test
    fun `stale catalog still predicts but displays as STALE`() {
        val s = predict(useCase(orbits = mapOf(4242L to orbit(retrievedAt = now - 10_000_000L))), gps13).states.single()
        assertEquals(PredictionStatus.PREDICTED, s.status)
        assertEquals(CatalogStaleness.STALE_RETRIEVAL, s.staleness)
        assertEquals(PredictionDisplayState.STALE, s.displayState)
    }

    @Test
    fun `unmatched satellite gets no geometry no noradId and no catalog lookup result`() {
        val batch = predict(useCase(), gps14)
        val s = batch.states.single()
        assertEquals(PredictionStatus.UNMATCHED, s.status)
        assertNull(s.geometry)
        assertNull(s.noradCatId)
        assertEquals(PredictionDisplayState.UNMATCHED, s.displayState)
        assertEquals(1, batch.diagnostics.unmatched)
    }

    @Test
    fun `unverified identity is not predicted`() {
        val s = predict(useCase(resolver = singleEntryResolver(MappingConfidence.UNVERIFIED)), gps13).states.single()
        assertEquals(PredictionStatus.IDENTITY_UNVERIFIED, s.status)
        assertNull(s.geometry)
        assertNull(s.noradCatId)
    }

    @Test
    fun `ambiguous identity is not predicted`() {
        val r = TableSatelliteIdentityResolver(
            listOf(
                SatelliteIdentityEntry(gps13, 1L, prov(MappingConfidence.VERIFIED)),
                SatelliteIdentityEntry(gps13, 2L, prov(MappingConfidence.VERIFIED))
            )
        )
        val s = predict(useCase(resolver = r), gps13).states.single()
        assertEquals(PredictionStatus.IDENTITY_AMBIGUOUS, s.status)
        assertNull(s.geometry)
    }

    @Test
    fun `matched identity without a catalog record is NO_CATALOG_RECORD`() {
        val s = predict(useCase(orbits = emptyMap()), gps13).states.single()
        assertEquals(PredictionStatus.NO_CATALOG_RECORD, s.status)
        assertNull(s.geometry)
        assertEquals(PredictionDisplayState.UNAVAILABLE, s.displayState)
    }

    @Test
    fun `missing observer yields OBSERVER_UNAVAILABLE but keeps freshness`() {
        val s = predict(useCase(), gps13, obs = null).states.single()
        assertEquals(PredictionStatus.OBSERVER_UNAVAILABLE, s.status)
        assertNull(s.geometry)
        assertNotNull(s.freshness)
    }

    @Test
    fun `default unavailable backend yields ENGINE_UNAVAILABLE not a fabricated prediction`() {
        val batch = predict(useCase(backend = UnavailableSgp4Sdp4Backend), gps13)
        val s = batch.states.single()
        assertEquals(PredictionStatus.ENGINE_UNAVAILABLE, s.status)
        assertNull(s.geometry)
        assertEquals(1, batch.diagnostics.engineUnavailable)
    }

    @Test
    fun `backend error yields PROPAGATION_FAILED`() {
        val failing = object : Sgp4Sdp4Backend {
            override val name = "FAILING_DOUBLE"
            override val version: String? = null
            override val supportsDeepSpace = false
            override fun propagate(input: Sgp4Sdp4Input, minutesSinceEpoch: Double) = BackendResult.Error(1, "boom")
        }
        val s = predict(useCase(backend = failing), gps13).states.single()
        assertEquals(PredictionStatus.PROPAGATION_FAILED, s.status)
        assertNull(s.geometry)
    }

    @Test
    fun `duplicate requested ids are predicted once and diagnostics count distinct satellites`() {
        val batch = predict(useCase(), gps13, gps13, gps14)
        assertEquals(2, batch.states.size)
        assertEquals(2, batch.diagnostics.requested)
        assertEquals(1, batch.diagnostics.predicted)
        assertEquals(1, batch.diagnostics.unmatched)
    }

    @Test
    fun `predicted state cannot be constructed with geometry and a non-predicted status`() {
        val s = predict(useCase(), gps13).states.single()
        var rejected = false
        try { s.copy(status = PredictionStatus.UNMATCHED) } catch (e: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }

    @Test
    fun `layer holder publishes Ready and starts NotRun`() {
        val holder = PredictionLayerStateHolder(useCase())
        assertEquals(PredictionLayerState.NotRun, holder.state.value)
        runBlocking { holder.refresh(PredictionRequest(listOf(gps13), observer, at)) }
        assertTrue(holder.state.value is PredictionLayerState.Ready)
    }
}
