package horizon.observatory.storage.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room migration tests for the orbit catalog. NOT RUN in the authoring sandbox: UNVERIFIED.
 *
 * Prerequisites (see docs/ORBIT_PREDICTION_ARCHITECTURE.md, "Schema baselines"):
 *  - app/schemas/horizon.observatory.storage.room.AppDatabase/{5,6,7}.json must exist. 7.json is
 *    produced by building this source; 5.json and 6.json can only be produced by building the
 *    earlier checkpoints (MERGED3 = v5, MERGED__4_ = v6) with schema export enabled.
 *  - A test that skips because a baseline is missing has verified NOTHING. A skip is not a pass.
 */
@RunWith(AndroidJUnit4::class)
class OrbitCatalogMigrationTest {
    private val dbName = "migration-test"
    private val schemaDir = "horizon.observatory.storage.room.AppDatabase"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private fun baselineAvailable(version: Int): Boolean {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        return assets.list(schemaDir)?.contains("$version.json") == true
    }

    @Test
    fun migration_6_to_7_replaces_lossy_orbit_catalog_table() {
        assumeTrue("BASELINE MISSING: 6.json (build MERGED__4_ with schema export)", baselineAvailable(6))
        assumeTrue("BASELINE MISSING: 7.json (build this source)", baselineAvailable(7))

        helper.createDatabase(dbName, 6).apply {
            execSQL(
                "INSERT INTO orbit_catalog (catalogId, objectName, epochUtcMillis, semiMajorAxisKm, eccentricity, " +
                    "inclinationRad, raanRad, argOfPerigeeRad, meanAnomalyRad, dataSource, retrievedAtUtcMillis) " +
                    "VALUES ('99999','OLD',1,7000.0,0.001,0.9,0.1,0.2,0.3,'TEST',1000)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 7, true, AppDatabase.MIGRATION_6_7)

        db.query("SELECT COUNT(*) FROM orbit_catalog").use {
            assertTrue(it.moveToFirst())
            assertEquals("lossy v6 rows are dropped, not converted", 0, it.getInt(0))
        }
        val columns = HashSet<String>()
        db.query("PRAGMA table_info(orbit_catalog)").use {
            while (it.moveToNext()) columns.add(it.getString(it.getColumnIndexOrThrow("name")))
        }
        listOf("noradCatId", "epochRaw", "bstar", "meanMotionRevPerDay", "catalogSource", "retrievedAtUtcMillis")
            .forEach { assertTrue("missing column $it", columns.contains(it)) }
    }

    @Test
    fun full_chain_5_to_7_validates() {
        assumeTrue("BASELINE MISSING: 5.json (build MERGED3 with schema export)", baselineAvailable(5))
        assumeTrue("BASELINE MISSING: 6.json", baselineAvailable(6))
        assumeTrue("BASELINE MISSING: 7.json", baselineAvailable(7))

        helper.createDatabase(dbName, 5).close()
        helper.runMigrationsAndValidate(dbName, 7, true, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7).close()
    }
}
