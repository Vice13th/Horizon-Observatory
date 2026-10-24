package horizon.observatory.resilience

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Contract v2 on a real ContentResolver. Written for the device/emulator gate; NOT executed in the authoring environment. */
@RunWith(AndroidJUnit4::class)
class ResilientLocationProviderInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: ResilientLocationStore
    private lateinit var authority: String
    private lateinit var readPermission: String
    private lateinit var uri: Uri

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = ResilientLocationStore(context)
        authority = ResilientLocationStore.authorityForPackage(context.packageName)
        readPermission = ResilientLocationStore.readPermissionForPackage(context.packageName)
        uri = Uri.parse("content://$authority/${ResilientLocationStore.PATH_LATEST}")
        store.clear()
    }
    @After fun tearDown() { store.clear() }

    private fun snapshot(ageNs: Long, vertical: Double? = 7.0, altitude: Double? = 100.0, lat: Double = 50.0) = ResilientLocationSnapshot(
        lat, 8.0, altitude, 4.0, vertical, SystemClock.elapsedRealtimeNanos() - ageNs,
        NavigationState.FULL_GNSS, "GNSS_OBSERVED", fixKind = ResilientFixKind.OBSERVED_GNSS
    )

    private fun <T> query(block: (Cursor) -> T): T {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        assertNotNull(cursor)
        return cursor!!.use { assertTrue(it.moveToFirst()); block(it) }
    }
    private fun Cursor.str(c: String) = getString(getColumnIndexOrThrow(c))
    private fun Cursor.nul(c: String) = isNull(getColumnIndexOrThrow(c))
    private fun Cursor.dbl(c: String) = getDouble(getColumnIndexOrThrow(c))

    @Test fun freshSnapshot_isReturnedWithProvenanceAndV2Columns() {
        assertTrue(store.publish(snapshot(ageNs = 1_000_000_000L)))
        query {
            assertEquals("FRESH", it.str("status")); assertEquals(2, it.getInt(it.getColumnIndexOrThrow("contractVersion")))
            assertEquals(50.0, it.dbl("latitudeDeg"), 0.0)
            assertEquals("GNSS_OBSERVED", it.str("provenance")); assertEquals("OBSERVED_GNSS", it.str("fixKind"))
            assertFalse(it.nul("ageMs"))
        }
    }
    @Test fun staleSnapshot_withholdsPosition_andIsNeverReportedFresh() {
        assertTrue(store.publish(snapshot(ageNs = 60_000_000_000L)))
        query {
            assertEquals("STALE", it.str("status")); assertEquals("MAX_AGE_EXCEEDED", it.str("statusReason"))
            assertTrue(it.nul("latitudeDeg")); assertTrue(it.nul("longitudeDeg")); assertTrue(it.nul("horizontalUncertaintyM"))
        }
    }
    @Test fun noSnapshot_reportsNoSnapshot_withNullPosition() = query {
        assertEquals("NO_SNAPSHOT", it.str("status")); assertTrue(it.nul("latitudeDeg"))
    }
    @Test fun corruptPayload_reportsCorrupt_neverACoordinate() {
        store.writeRawForTest("{not json")
        query { assertEquals("CORRUPT", it.str("status")); assertTrue(it.nul("latitudeDeg")) }
    }
    @Test fun invalidSnapshot_isRefusedAtPublish_andNothingIsStored() {
        assertFalse(store.publish(snapshot(ageNs = 1_000_000_000L, lat = 91.0)))
        query { assertEquals("NO_SNAPSHOT", it.str("status")) }
    }
    @Test fun unknownOptionalValues_areSqlNull_notZero() {
        assertTrue(store.publish(snapshot(ageNs = 1_000_000_000L, vertical = null, altitude = null)))
        query { assertEquals("FRESH", it.str("status")); assertTrue(it.nul("verticalUncertaintyM")); assertTrue(it.nul("altitudeM")); assertTrue(it.nul("speedMps")) }
    }
    @Test fun readPermission_isDeclaredOnTheProvider() {
        val info = context.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA)
        assertNotNull(info)
        assertEquals(readPermission, info!!.readPermission)
    }
}
