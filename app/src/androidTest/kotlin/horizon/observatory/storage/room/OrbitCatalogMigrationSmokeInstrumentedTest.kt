package horizon.observatory.storage.room

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real catalog migration SQL without fabricating historical Room schema JSON.
 *
 * Fixtures start from a Room-created v7 database and synthetically reset only the catalog table
 * and user_version to v5 or v6. This tests the catalog migration SQL and Room's final v7 schema
 * validation, but is not a substitute for authentic v5/v6 MigrationTestHelper baselines because
 * the non-catalog tables originate from a v7 database.
 */
@RunWith(AndroidJUnit4::class)
class OrbitCatalogMigrationSmokeInstrumentedTest {
    private val migrations = arrayOf(
        AppDatabase.MIGRATION_1_2,
        AppDatabase.MIGRATION_2_3,
        AppDatabase.MIGRATION_3_4,
        AppDatabase.MIGRATION_4_5,
        AppDatabase.MIGRATION_5_6,
        AppDatabase.MIGRATION_6_7,
    )

    @Test
    fun syntheticVersion6CatalogMigratesAndRoomValidatesVersion7Schema() {
        migrateSyntheticCatalog(startVersion = 6, includeLegacyCatalogRow = true)
    }

    @Test
    fun syntheticVersion5DatabaseRunsCatalogMigrationsThroughVersion7Schema() {
        migrateSyntheticCatalog(startVersion = 5, includeLegacyCatalogRow = false)
    }

    private fun migrateSyntheticCatalog(startVersion: Int, includeLegacyCatalogRow: Boolean) {
        require(startVersion == 5 || startVersion == 6)
        val databaseName = "synthetic-v" + startVersion + "-migration-smoke.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)

        val initial = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(*migrations)
            .build()
        try {
            val sql = initial.openHelper.writableDatabase
            sql.execSQL(
                "INSERT INTO sessions (sessionId, startTimeMs, endTimeMs, lifecycleState, " +
                    "deviceManufacturer, deviceModel, androidVersion, sdkVersion, applicationVersion, " +
                    "schemaVersion, deviceMetadataJson, capabilityReportJson, permissionStateJson) " +
                    "VALUES ('migration-preservation', 1, NULL, 'RECORDING', 'test', 'fixture', " +
                    "'test-os', 36, 'test', 7, '{}', '{}', '{}')"
            )
        } finally {
            initial.close()
        }

        val path = context.getDatabasePath(databaseName).absolutePath
        val legacy = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            legacy.execSQL("DROP TABLE IF EXISTS orbit_catalog")
            if (includeLegacyCatalogRow) {
                legacy.execSQL(
                    "CREATE TABLE orbit_catalog (" +
                        "catalogId TEXT NOT NULL PRIMARY KEY, objectName TEXT NOT NULL, " +
                        "epochUtcMillis INTEGER NOT NULL, semiMajorAxisKm REAL NOT NULL, " +
                        "eccentricity REAL NOT NULL, inclinationRad REAL NOT NULL, " +
                        "raanRad REAL NOT NULL, argOfPerigeeRad REAL NOT NULL, " +
                        "meanAnomalyRad REAL NOT NULL, dataSource TEXT NOT NULL, " +
                        "retrievedAtUtcMillis INTEGER NOT NULL)"
                )
                legacy.execSQL(
                    "INSERT INTO orbit_catalog (catalogId, objectName, epochUtcMillis, semiMajorAxisKm, " +
                        "eccentricity, inclinationRad, raanRad, argOfPerigeeRad, meanAnomalyRad, " +
                        "dataSource, retrievedAtUtcMillis) " +
                        "VALUES ('99999', 'OLD', 1, 7000.0, 0.001, 0.9, 0.1, 0.2, 0.3, 'TEST', 1000)"
                )
            }
            // v5 has no catalog table yet; v6 has the simplified legacy catalog table.
            legacy.version = startVersion
        } finally {
            legacy.close()
        }

        try {
            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
                .addMigrations(*migrations)
                .build()
            try {
                val sql = migrated.openHelper.writableDatabase
                assertEquals("Room must finish at schema v7", 7, sql.version)

                sql.query("SELECT COUNT(*) FROM orbit_catalog").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("legacy catalog data is not convertible and is intentionally dropped", 0, cursor.getInt(0))
                }

                val columns = mutableSetOf<String>()
                sql.query("PRAGMA table_info(orbit_catalog)").use { cursor ->
                    while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
                }
                listOf(
                    "noradCatId",
                    "epochRaw",
                    "bstar",
                    "meanMotionRevPerDay",
                    "catalogSource",
                    "retrievedAtUtcMillis",
                ).forEach { assertTrue("missing v7 column " + it, columns.contains(it)) }
                assertFalse("legacy v6 column must not remain", columns.contains("catalogId"))

                sql.query("SELECT COUNT(*) FROM sessions WHERE sessionId = 'migration-preservation'").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("non-catalog session data must survive", 1, cursor.getInt(0))
                }
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
