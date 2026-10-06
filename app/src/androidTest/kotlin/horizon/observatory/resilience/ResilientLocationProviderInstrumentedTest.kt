package horizon.observatory.resilience

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResilientLocationProviderInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: ResilientLocationStore
    @Before fun setUp() { context = ApplicationProvider.getApplicationContext(); store = ResilientLocationStore(context); store.clear() }
    @After fun tearDown() { store.clear() }
    @Test fun published_snapshot_is_returned_with_provenance() {
        store.publish(ResilientLocationSnapshot(50.0, 8.0, 100.0, 4.0, 7.0, 123L, NavigationState.GNSS_LOST, "DEAD_RECKONED_FROM_LAST_TRUSTED_PVT"))
        val uri = Uri.parse("content://${ResilientLocationStore.AUTHORITY}/${ResilientLocationStore.PATH_LATEST}")
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        assertNotNull(cursor)
        cursor!!.use {
            assertTrue(it.moveToFirst())
            assertEquals(50.0, it.getDouble(it.getColumnIndexOrThrow("latitudeDeg")), 0.0)
            assertEquals("GNSS_LOST", it.getString(it.getColumnIndexOrThrow("navigationState")))
            assertEquals("DEAD_RECKONED_FROM_LAST_TRUSTED_PVT", it.getString(it.getColumnIndexOrThrow("provenance")))
        }
    }
}
