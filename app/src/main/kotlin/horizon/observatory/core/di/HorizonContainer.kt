package horizon.observatory.core.di

import android.content.Context
import horizon.observatory.acquisition.cellular.CellInfoSource
import horizon.observatory.acquisition.gnss.GnssObservationSource
import horizon.observatory.acquisition.gnss.LocationFixSource
import horizon.observatory.acquisition.sensor.SensorObservationSource
import horizon.observatory.analysis.SessionAnalysisEngine
import horizon.observatory.core.capability.CapabilityScanner
import horizon.observatory.astronomy.RoomOrbitDataSource
import horizon.observatory.astronomy.StalenessPolicy
import horizon.observatory.astronomy.catalog.HttpUrlConnectionOrbitCatalogFetcher
import horizon.observatory.astronomy.catalog.OrbitCatalogFetcher
import horizon.observatory.astronomy.catalog.OrbitCatalogIngestor
import horizon.observatory.astronomy.identity.SatelliteIdentityResolver
import horizon.observatory.astronomy.identity.TableSatelliteIdentityResolver
import horizon.observatory.astronomy.orientation.ControllableOrientationSource
import horizon.observatory.live.GeomagneticFieldDeclinationProvider
import horizon.observatory.live.MagneticDeclinationProvider
import horizon.observatory.live.RotationVectorOrientationSource
import horizon.observatory.astronomy.prediction.PredictionLayerStateHolder
import horizon.observatory.astronomy.prediction.SatellitePredictionUseCase
import horizon.observatory.astronomy.propagation.OrbitPropagationEngine
import horizon.observatory.astronomy.propagation.Sgp4Sdp4PropagationEngine
import horizon.observatory.astronomy.propagation.UnavailableSgp4Sdp4Backend
import horizon.observatory.astronomy.propagation.Sgp4Sdp4Backend
import horizon.observatory.astronomy.time.DeviceClockUtc
import horizon.observatory.astronomy.time.TimestampEngineDeviceClock
import horizon.observatory.export.ExportEngine
import horizon.observatory.storage.queue.ObservationPersistenceQueue
import horizon.observatory.storage.queue.RoomObservationPersistenceQueue
import horizon.observatory.storage.repository.SessionRepository
import horizon.observatory.storage.repository.SessionRepositoryImpl
import horizon.observatory.storage.room.AppDatabase

class HorizonContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase = AppDatabase.getInstance(appContext)
    val sessionRepository: SessionRepository = SessionRepositoryImpl(database.sessionDao(), database.observationDao())
    val observationQueue: ObservationPersistenceQueue = RoomObservationPersistenceQueue(database)
    val exportEngine: ExportEngine = ExportEngine(appContext, sessionRepository)
    val capabilityScanner = CapabilityScanner(appContext)
    val gnssFixSource = LocationFixSource(appContext)
    val gnssObservationSource = GnssObservationSource(appContext)
    val cellularSource = CellInfoSource(appContext)
    val sensorSource = SensorObservationSource(appContext)
    val analysisEngine = SessionAnalysisEngine()

    // --- Orbit prediction foundation (PREDICTED/DERIVED domain; separate from observed evidence) ---
    // Nothing here fetches automatically: the catalog is only retrieved when a caller invokes
    // orbitCatalogIngestor.ingest(...). No observation or user data is ever sent.
    val deviceClock: DeviceClockUtc = TimestampEngineDeviceClock
    val orbitCatalogDao = database.orbitCatalogDao()
    val orbitDataSource = RoomOrbitDataSource(orbitCatalogDao)
    val orbitCatalogFetcher: OrbitCatalogFetcher = HttpUrlConnectionOrbitCatalogFetcher(deviceClock)
    val orbitCatalogIngestor = OrbitCatalogIngestor(orbitCatalogFetcher, orbitDataSource)

    // Empty table => every satellite resolves to UNMATCHED. No identity is guessed.
    val satelliteIdentityResolver: SatelliteIdentityResolver = TableSatelliteIdentityResolver(emptyList())

    // T1 loads the OrbitCore bridge reflectively so primary/ts do not gain the bridge dependency.
    // The bridge itself has OrbitCore as runtimeOnly, keeping OrbitCore metadata out of app KAPT.
    val orbitPropagationEngine: OrbitPropagationEngine = Sgp4Sdp4PropagationEngine(loadOrbitBackend())

    private fun loadOrbitBackend(): Sgp4Sdp4Backend {
        return try {
            val type = Class.forName("horizon.observatory.orbitcore.OrbitCoreSgp4Sdp4Backend")
            type.getDeclaredConstructor().newInstance() as Sgp4Sdp4Backend
        } catch (_: ClassNotFoundException) {
            UnavailableSgp4Sdp4Backend
        } catch (_: LinkageError) {
            UnavailableSgp4Sdp4Backend
        } catch (_: ReflectiveOperationException) {
            UnavailableSgp4Sdp4Backend
        }
    }

    // Policy thresholds (configuration, not physical accuracy limits): 24 h since retrieval, 7 d since epoch.
    private val orbitStalenessPolicy = StalenessPolicy(
        maxDataAgeMs = 24L * 60L * 60L * 1000L,
        maxEpochAgeMs = 7L * 24L * 60L * 60L * 1000L
    )
    val satellitePredictionUseCase = SatellitePredictionUseCase(
        orbits = orbitDataSource,
        identity = satelliteIdentityResolver,
        engine = orbitPropagationEngine,
        clock = deviceClock,
        stalenessPolicy = orbitStalenessPolicy
    )
    val predictionLayer = PredictionLayerStateHolder(satellitePredictionUseCase)

    // Live device pose (presentation input only). The listener is NOT registered until the UI starts it.
    val deviceOrientationSource: ControllableOrientationSource = RotationVectorOrientationSource(appContext)
    val magneticDeclinationProvider: MagneticDeclinationProvider = GeomagneticFieldDeclinationProvider()
}
