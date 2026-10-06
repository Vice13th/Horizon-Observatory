package horizon.observatory.storage.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import horizon.observatory.storage.entity.ObservationEntity
import horizon.observatory.storage.entity.PendingObservationEntity
import horizon.observatory.storage.entity.SessionEntity

@Database(
    entities = [SessionEntity::class, ObservationEntity::class, PendingObservationEntity::class, horizon.observatory.astronomy.OrbitCatalogEntity::class],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun observationDao(): ObservationDao
    abstract fun pendingObservationDao(): PendingObservationDao
    abstract fun orbitCatalogDao(): OrbitCatalogDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE observations ADD COLUMN evidenceStatus TEXT NOT NULL DEFAULT 'OBSERVED'")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_observations_sessionId_sequenceNumber ON observations(sessionId,sequenceNumber)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pending_observations (" +
                        "sessionId TEXT NOT NULL, sequenceNumber INTEGER NOT NULL, " +
                        "timestampUtcMs INTEGER NOT NULL, monotonicTimestampNs INTEGER, " +
                        "source TEXT NOT NULL, technology TEXT NOT NULL, type TEXT NOT NULL, " +
                        "provider TEXT NOT NULL, rawPayloadJson TEXT NOT NULL, " +
                        "normalizedPayloadJson TEXT NOT NULL DEFAULT '{}', capabilityState TEXT NOT NULL, " +
                        "evidenceStatus TEXT NOT NULL, provenance TEXT NOT NULL, " +
                        "PRIMARY KEY(sessionId,sequenceNumber), " +
                        "FOREIGN KEY(sessionId) REFERENCES sessions(sessionId)" +
                    ")"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_observations_sessionId ON pending_observations(sessionId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN deviceMetadataJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE sessions ADD COLUMN capabilityReportJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE sessions ADD COLUMN permissionStateJson TEXT NOT NULL DEFAULT '{}'")

                db.execSQL("ALTER TABLE observations ADD COLUMN timestampDomain TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE observations ADD COLUMN timestampUncertaintyNs INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_observations_monotonicTimestampNs ON observations(monotonicTimestampNs)")

                db.execSQL("ALTER TABLE pending_observations ADD COLUMN timestampDomain TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE pending_observations ADD COLUMN timestampUncertaintyNs INTEGER")
            }
        }


        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE sessions SET lifecycleState = 'RECORDING' WHERE lifecycleState = 'ACTIVE'")
                db.execSQL("UPDATE sessions SET lifecycleState = 'COMPLETED' WHERE lifecycleState = 'CLOSED'")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE observations ADD COLUMN ingestionMonotonicTimestampNs INTEGER")
                db.execSQL("ALTER TABLE pending_observations ADD COLUMN ingestionMonotonicTimestampNs INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_observations_ingestionMonotonicTimestampNs ON observations(ingestionMonotonicTimestampNs)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS orbit_catalog (" +
                        "catalogId TEXT NOT NULL PRIMARY KEY, objectName TEXT NOT NULL, " +
                        "epochUtcMillis INTEGER NOT NULL, semiMajorAxisKm REAL NOT NULL, " +
                        "eccentricity REAL NOT NULL, inclinationRad REAL NOT NULL, " +
                        "raanRad REAL NOT NULL, argOfPerigeeRad REAL NOT NULL, " +
                        "meanAnomalyRad REAL NOT NULL, dataSource TEXT NOT NULL, " +
                        "retrievedAtUtcMillis INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v6 -> v7: replaces the lossy v6 `orbit_catalog` (simplified Keplerian elements, no BSTAR,
         * no source epoch text, no provenance beyond a source string) with the complete OMM record.
         *
         * The v6 rows are DROPPED, not converted: they lack fields an SGP4/SDP4 implementation
         * requires (BSTAR, mean-motion convention, exact epoch text) and those values cannot be
         * reconstructed without fabricating them. The table is a re-fetchable cache of PUBLIC
         * catalog data; no session, observation or evidence table is touched.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS orbit_catalog")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS orbit_catalog (" +
                        "noradCatId INTEGER NOT NULL, objectName TEXT, objectId TEXT, classification TEXT, " +
                        "epochRaw TEXT NOT NULL, epochUtcMillis INTEGER NOT NULL, " +
                        "meanMotionRevPerDay REAL NOT NULL, eccentricity REAL NOT NULL, " +
                        "inclinationDeg REAL NOT NULL, raanDeg REAL NOT NULL, " +
                        "argOfPericenterDeg REAL NOT NULL, meanAnomalyDeg REAL NOT NULL, " +
                        "ephemerisType INTEGER, elementSetNo INTEGER, revAtEpoch INTEGER, " +
                        "bstar REAL NOT NULL, meanMotionDot REAL, meanMotionDdot REAL, " +
                        "catalogSource TEXT NOT NULL, catalogSourceIdentifier TEXT, catalogFormat TEXT NOT NULL, " +
                        "retrievedAtUtcMillis INTEGER NOT NULL, PRIMARY KEY(noradCatId))"
                )
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "horizon_observatory.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
