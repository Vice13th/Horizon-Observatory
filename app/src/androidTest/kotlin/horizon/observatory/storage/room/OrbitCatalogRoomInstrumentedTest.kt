package horizon.observatory.storage.room

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import horizon.observatory.astronomy.OmmParser
import horizon.observatory.astronomy.OrbitProvenance
import horizon.observatory.astronomy.RoomOrbitDataSource
import horizon.observatory.astronomy.time.RetrievalTimeUtcMs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room persistence round-trip for the orbit catalog. NOT RUN in the authoring sandbox: UNVERIFIED. */
@RunWith(AndroidJUnit4::class)
class OrbitCatalogRoomInstrumentedTest {
    private lateinit var db: AppDatabase
    private lateinit var source: RoomOrbitDataSource

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        source = RoomOrbitDataSource(db.orbitCatalogDao())
    }

    @After
    fun close() = db.close()

    private fun records(norad: Long, epoch: String) = OmmParser.parse(
        """[{"OBJECT_NAME":"SAT","NORAD_CAT_ID":$norad,"EPOCH":"$epoch","MEAN_MOTION":2.0056,"ECCENTRICITY":0.01,
        "INCLINATION":55.0,"RA_OF_ASC_NODE":10.0,"ARG_OF_PERICENTER":20.0,"MEAN_ANOMALY":30.0,"BSTAR":0.0002,
        "MEAN_MOTION_DOT":0.0000001}]"""
    ).records

    private fun prov(retrievedAt: Long) = OrbitProvenance("CELESTRAK_GP", "https://example.invalid/gp", "OMM_JSON", RetrievalTimeUtcMs(retrievedAt))

    @Test
    fun persist_and_reload_preserves_record_epoch_text_and_provenance() = runBlocking {
        source.upsert(records(1, "2026-01-01T00:00:00.123456"), prov(5_000L))
        val o = source.getOrbit(1)!!
        assertEquals("2026-01-01T00:00:00.123456", o.record.epochRaw)
        assertEquals(0.0002, o.record.bstar, 0.0)
        assertEquals(0.0000001, o.record.meanMotionDot!!, 0.0)
        assertNull(o.record.meanMotionDdot)
        assertEquals("CELESTRAK_GP", o.provenance.source)
        assertEquals(5_000L, o.provenance.retrievedAt.value)
    }

    @Test
    fun reading_does_not_change_retrieval_time() = runBlocking {
        source.upsert(records(1, "2026-01-01T00:00:00"), prov(5_000L))
        source.getOrbit(1)
        source.getAllOrbits()
        assertEquals(5_000L, source.getOrbit(1)!!.provenance.retrievedAt.value)
    }

    @Test
    fun newer_epoch_replaces_and_older_epoch_is_rejected() = runBlocking {
        source.upsert(records(1, "2026-01-02T00:00:00"), prov(1_000L))
        val older = source.upsert(records(1, "2026-01-01T00:00:00"), prov(2_000L))
        assertEquals(0, older.written)
        assertEquals(1, older.keptExistingNewerEpoch)
        assertEquals("2026-01-02T00:00:00", source.getOrbit(1)!!.record.epochRaw)
        val newer = source.upsert(records(1, "2026-01-03T00:00:00"), prov(3_000L))
        assertEquals(1, newer.written)
        assertEquals("2026-01-03T00:00:00", source.getOrbit(1)!!.record.epochRaw)
        assertEquals(3_000L, source.getOrbit(1)!!.provenance.retrievedAt.value)
    }

    @Test
    fun same_epoch_refetch_updates_retrieval_time_only() = runBlocking {
        source.upsert(records(1, "2026-01-01T00:00:00"), prov(1_000L))
        source.upsert(records(1, "2026-01-01T00:00:00"), prov(9_000L))
        assertEquals(9_000L, source.getOrbit(1)!!.provenance.retrievedAt.value)
        assertEquals(1, db.orbitCatalogDao().count())
    }

    @Test
    fun missing_object_returns_null_not_a_default() = runBlocking {
        assertNull(source.getOrbit(404))
        assertEquals(0, source.getAllOrbits().size)
        assertNotNull(db.orbitCatalogDao())
    }
}
