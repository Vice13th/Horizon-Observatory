package horizon.observatory.acquisition.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import horizon.observatory.HorizonApplication
import horizon.observatory.acquisition.cellular.CellInfoSource
import horizon.observatory.acquisition.gnss.GnssObservationSource
import horizon.observatory.acquisition.gnss.LocationFixSource
import horizon.observatory.acquisition.sensor.SensorObservationSource
import horizon.observatory.core.health.HealthReporter
import horizon.observatory.core.health.HealthSnapshot
import horizon.observatory.domain.model.CapabilityState
import horizon.observatory.domain.model.EvidenceStatus
import horizon.observatory.domain.model.ObservationProvenance
import horizon.observatory.domain.model.ObservationType
import horizon.observatory.domain.model.IntegrityAudit
import horizon.observatory.domain.model.RawObservationView
import horizon.observatory.domain.model.RawObservation
import horizon.observatory.domain.model.SessionSequencer
import horizon.observatory.domain.model.SessionLifecycleState
import horizon.observatory.domain.model.TimestampDomain
import horizon.observatory.core.time.TimestampEngine
import horizon.observatory.storage.queue.ObservationPersistenceQueue
import horizon.observatory.storage.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class ObservatoryService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var repository: SessionRepository
    private lateinit var observationQueue: ObservationPersistenceQueue
    private lateinit var gnssFixSource: LocationFixSource
    private lateinit var gnssRawSource: GnssObservationSource
    private lateinit var cellularSource: CellInfoSource
    private lateinit var sensorSource: SensorObservationSource
    private lateinit var healthReporter: HealthReporter
    private lateinit var capabilityScanner: horizon.observatory.core.capability.CapabilityScanner

    private var currentSessionId: String? = null
    private var lastClosedSessionId: String? = null
    private var sequencer: SessionSequencer? = null
    private var sourceJobs = mutableListOf<Job>()
    private var healthJob: Job? = null
    /** Serializes sequence allocation with the HORIZON ingress timestamp. */
    private val ingressMutex = Mutex()
    /** Serializes STOP requests so duplicate stop intents cannot race the session state machine. */
    private val stopMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        val container = (application as HorizonApplication).container
        repository = container.sessionRepository
        observationQueue = container.observationQueue
        gnssFixSource = container.gnssFixSource
        gnssRawSource = container.gnssObservationSource
        cellularSource = container.cellularSource
        sensorSource = container.sensorSource
        capabilityScanner = container.capabilityScanner
        healthReporter = HealthReporter(serviceScope)

        serviceScope.launch {
            repository.recoverUnclosedSessions().forEach { stale ->
                observationQueue.drain(stale.sessionId)
                repository.markCrashClosed(stale.sessionId)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleActionStart()
            ACTION_STOP -> if (currentSessionId == null) stopSelf() else stopObserving()
            ACTION_EXPORT -> exportLastClosedSession()
        }
        return START_NOT_STICKY
    }

    private fun handleActionStart() {
        if (!canStartLocationForegroundService()) {
            stopSelf(); return
        }
        if (!startForegroundServiceNotificationSafely()) {
            stopSelf(); return
        }
        startObserving()
    }

    private fun startObserving() {
        if (currentSessionId != null) return
        serviceScope.launch {
            val sessionId = repository.createSession()
            currentSessionId = sessionId
            sequencer = SessionSequencer()

            val report = capabilityScanner.buildReport()
            val device = report.json.optJSONObject("device")?.toString() ?: "{}"
            val permissions = report.json.optJSONObject("permissions")?.toString() ?: "{}"
            repository.updateSessionMetadata(sessionId, device, report.json.toString(), permissions)

            persistSystemEvent(sessionId, "SESSION_STARTED", JSONObject().put("capabilityReport", report.json))

            sourceJobs += launchSource(sessionId, gnssFixSource)
            sourceJobs += launchSource(sessionId, gnssRawSource)
            sourceJobs += launchSource(sessionId, cellularSource)
            sourceJobs += launchSource(sessionId, sensorSource)

            healthJob?.cancel()
            healthJob = healthReporter.start {
                HealthSnapshot(
                    sessionId = currentSessionId,
                    lifecycleState = repository.getSession(sessionId)?.lifecycleState,
                    gnssStatus = if (gnssRawSource.status == horizon.observatory.domain.model.SourceStatus.AVAILABLE) gnssFixSource.status else gnssRawSource.status,
                    cellularStatus = cellularSource.status,
                    observationCount = repository.countObservations(sessionId),
                    acquisitionRunning = sourceJobs.any { it.isActive }
                )
            }
        }
    }

    private fun <T : horizon.observatory.acquisition.base.ObservationSource> launchSource(
        sessionId: String,
        source: T
    ): Job = serviceScope.launch {
        try {
            source.start()
            source.observationFlow.collect { observation ->
                ingressMutex.withLock {
                    if (currentSessionId != sessionId) return@withLock
                    val seq = sequencer?.nextSequenceNumber() ?: return@withLock
                    val ingressNs = android.os.SystemClock.elapsedRealtimeNanos()
                    val stamped = observation.copy(
                        sequenceNumber = seq,
                        ingestionMonotonicTimestampNs = ingressNs
                    )
                    // A sequence number is not reusable. Once allocated, the observation must
                    // survive collector cancellation long enough to reach the durable queue.
                    // Otherwise a normal stop can create an artificial gap such as 1..2281
                    // with only 2280 persisted rows.
                    withContext(NonCancellable) {
                        observationQueue.enqueue(sessionId, stamped)
                    }
                    observationQueue.drain(sessionId)
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.e(SERVICE_TAG, "Source ${source::class.java.simpleName} failed for session=$sessionId", t)
            if (currentSessionId == sessionId) {
                runCatching {
                    persistErrorEvent(sessionId, source::class.java.simpleName, t)
                }.onFailure { Log.e(SERVICE_TAG, "Unable to persist source error", it) }
            }
        }
    }

    private fun stopObserving() {
        val requestedSessionId = currentSessionId ?: return
        serviceScope.launch {
            stopMutex.withLock {
                // A second STOP intent may arrive while the first stop is draining the queue.
                // Serialize STOP handling and re-read lifecycle state before mutating it.
                val sessionId = currentSessionId ?: return@withLock
                if (sessionId != requestedSessionId) return@withLock
                val state = repository.getSession(sessionId)?.lifecycleState ?: return@withLock
                if (state != SessionLifecycleState.RECORDING.name) {
                    Log.w(SERVICE_TAG, "Ignoring duplicate/non-recording STOP for session=$sessionId state=$state")
                    return@withLock
                }

                healthJob?.cancel()
                listOf(gnssRawSource, gnssFixSource, cellularSource, sensorSource).forEach { it.stop() }
                sourceJobs.forEach { job -> withTimeoutOrNull(SOURCE_STOP_TIMEOUT_MS) { job.cancelAndJoin() } }
                sourceJobs.clear()

                repository.beginStopping(sessionId)
                persistSystemEvent(sessionId, "SESSION_STOPPING", JSONObject())

                val drained = withTimeoutOrNull(DRAIN_TIMEOUT_MS) {
                while (observationQueue.pendingCount(sessionId) > 0) observationQueue.drain(sessionId)
                true
            } ?: false

            if (!drained || observationQueue.pendingCount(sessionId) != 0) {
                Log.e(SERVICE_TAG, "Unable to fully drain session=$sessionId; leaving STOPPING for recovery")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            val persisted = repository.getObservationsSnapshot(sessionId)
            val audit = IntegrityAudit.audit(
                persisted.map {
                    RawObservationView(
                        sequenceNumber = it.sequenceNumber,
                        sourceMonotonicTimestampNs = it.monotonicTimestampNs,
                        ingestionMonotonicTimestampNs = it.ingestionMonotonicTimestampNs,
                        provider = it.provider
                    )
                }
            )
            if (!audit.isClean) {
                Log.e(SERVICE_TAG, "Integrity gate failed session=$sessionId audit=$audit")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            repository.closeSession(sessionId)
            lastClosedSessionId = sessionId
            currentSessionId = null
            sequencer = null
            Log.i(SERVICE_TAG, "Session COMPLETED session=$sessionId observations=${repository.countObservations(sessionId)}")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }


    private suspend fun persistErrorEvent(sessionId: String, sourceName: String, throwable: Throwable) {
        ingressMutex.withLock {
            val seq = sequencer?.nextSequenceNumber() ?: return@withLock
            val now = TimestampEngine.now()
            val monotonicNs = now.elapsedRealtimeNanos
            observationQueue.enqueue(
                sessionId,
                RawObservation(
                    type = ObservationType.ERROR_EVENT,
                    utcTimestampMs = now.utcTimestampMs,
                    monotonicTimestampNs = monotonicNs,
                    ingestionMonotonicTimestampNs = monotonicNs,
                    provider = "HorizonService",
                    payloadJson = JSONObject()
                        .put("source", sourceName)
                        .put("errorType", throwable::class.java.name)
                        .put("message", throwable.message ?: JSONObject.NULL)
                        .toString(),
                    sequenceNumber = seq,
                    technology = "SYSTEM",
                    capabilityState = CapabilityState.UNKNOWN,
                    evidenceStatus = EvidenceStatus.OBSERVED,
                    provenance = ObservationProvenance.NORMALIZED,
                    timestampDomain = TimestampDomain.ANDROID_ELAPSED_REALTIME_NANOS
                )
            )
            observationQueue.drain(sessionId)
        }
    }

    private suspend fun persistSystemEvent(sessionId: String, event: String, details: JSONObject) {
        ingressMutex.withLock {
            val seq = sequencer?.nextSequenceNumber() ?: return@withLock
            val now = TimestampEngine.now()
            val monotonicNs = now.elapsedRealtimeNanos
            observationQueue.enqueue(
                sessionId,
                RawObservation(
                    type = ObservationType.SYSTEM_EVENT,
                    utcTimestampMs = now.utcTimestampMs,
                    monotonicTimestampNs = monotonicNs,
                    ingestionMonotonicTimestampNs = monotonicNs,
                    provider = "HorizonService",
                    payloadJson = JSONObject().put("event", event).put("details", details).toString(),
                    sequenceNumber = seq,
                    technology = "SYSTEM",
                    capabilityState = CapabilityState.AVAILABLE_NOW,
                    evidenceStatus = EvidenceStatus.OBSERVED,
                    provenance = ObservationProvenance.NORMALIZED,
                    timestampDomain = TimestampDomain.ANDROID_ELAPSED_REALTIME_NANOS
                )
            )
            observationQueue.drain(sessionId)
        }
    }

    private fun exportLastClosedSession() {
        serviceScope.launch {
            val sessionId = lastClosedSessionId
                ?: repository.getLatestCompletedSession()?.sessionId
            if (sessionId == null) {
                Log.w(EXPORT_TAG, "ACTION_EXPORT requested but no completed session is available")
                return@launch
            }
            when (val result = (application as HorizonApplication).container.exportEngine.exportSession(sessionId)) {
                is horizon.observatory.export.ExportEngine.ExportResult.Success -> Log.i(EXPORT_TAG, "Export SUCCESS session=$sessionId file=${result.zipFile}")
                is horizon.observatory.export.ExportEngine.ExportResult.Failed -> Log.e(EXPORT_TAG, "Export FAILED session=$sessionId stage=${result.stage} reason=${result.reason}")
            }
        }
    }

    private fun canStartLocationForegroundService(): Boolean {
        val locationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val manager = getSystemService(LocationManager::class.java)
        val enabled = manager != null && LocationManagerCompat.isLocationEnabled(manager)
        return locationPermission && enabled
    }

    private fun startForegroundServiceNotificationSafely(): Boolean {
        val channelId = "observatory_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(channelId, "Horizon Background Observation", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Horizon Observatory")
            .setContentText("Collecting raw observations locally")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(this, 1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else startForeground(1001, notification)
            true
        }.getOrDefault(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sourceJobs.forEach { it.cancel() }
        healthJob?.cancel()
        gnssRawSource.takeIf { ::gnssRawSource.isInitialized }?.stop()
        gnssFixSource.takeIf { ::gnssFixSource.isInitialized }?.stop()
        cellularSource.takeIf { ::cellularSource.isInitialized }?.stop()
        sensorSource.takeIf { ::sensorSource.isInitialized }?.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_EXPORT = "ACTION_EXPORT"
        private const val DRAIN_TIMEOUT_MS = 10_000L
        private const val SOURCE_STOP_TIMEOUT_MS = 3_000L
        private const val SERVICE_TAG = "HorizonService"
        private const val EXPORT_TAG = "HorizonExport"
    }
}
