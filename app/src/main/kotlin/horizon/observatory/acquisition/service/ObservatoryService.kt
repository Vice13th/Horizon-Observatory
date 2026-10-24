package horizon.observatory.acquisition.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import horizon.observatory.storage.repository.toObservationEntity
import horizon.observatory.resilience.EmergencyNavigationController
import horizon.observatory.resilience.NavigationState
import horizon.observatory.resilience.ResilienceRuntime
import horizon.observatory.resilience.ResilienceRuntimeUpdate
import horizon.observatory.resilience.ContinuityFailure
import horizon.observatory.resilience.ContinuityServiceStatus
import horizon.observatory.resilience.ContinuityStatusHub
import horizon.observatory.resilience.ObservedFixMapper
import horizon.observatory.resilience.ObservedFixMapping
import horizon.observatory.resilience.PublishDecision
import horizon.observatory.resilience.SnapshotPublishGate
import horizon.observatory.resilience.ResilientFixKind
import horizon.observatory.resilience.ResilientLocationSnapshot
import horizon.observatory.resilience.ResilientLocationStore
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
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

class ObservatoryService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var repository: SessionRepository
    private lateinit var observationQueue: ObservationPersistenceQueue
    private lateinit var liveObservationBus: horizon.observatory.live.LiveObservationBus
    private lateinit var gnssFixSource: LocationFixSource
    private lateinit var gnssRawSource: GnssObservationSource
    private lateinit var cellularSource: CellInfoSource
    private lateinit var sensorSource: SensorObservationSource
    private lateinit var healthReporter: HealthReporter
    private lateinit var capabilityScanner: horizon.observatory.core.capability.CapabilityScanner
    private lateinit var emergencyNavigationController: EmergencyNavigationController
    private lateinit var resilientLocationStore: ResilientLocationStore
    private var resilienceRuntime: ResilienceRuntime? = null

    private var currentSessionId: String? = null
    private var lastClosedSessionId: String? = null
    private var sequencer: SessionSequencer? = null
    private var sourceJobs = mutableListOf<Job>()
    private var healthJob: Job? = null
    /** Serializes sequence allocation with the HORIZON ingress timestamp. */
    private val ingressMutex = Mutex()
    /** Serializes STOP requests so duplicate stop intents cannot race the session state machine. */
    private val stopMutex = Mutex()
    /** Closes the window between "start requested" and `currentSessionId` being assigned on the IO thread. */
    private val startRequested = AtomicBoolean(false)
    private var lastObservedRejection: String? = null
    private var publishGate = SnapshotPublishGate()

    override fun onCreate() {
        super.onCreate()
        val container = (application as HorizonApplication).container
        repository = container.sessionRepository
        observationQueue = container.observationQueue
        liveObservationBus = container.liveObservationBus
        gnssFixSource = container.gnssFixSource
        gnssRawSource = container.gnssObservationSource
        cellularSource = container.cellularSource
        sensorSource = container.sensorSource
        capabilityScanner = container.capabilityScanner
        emergencyNavigationController = EmergencyNavigationController(this)
        resilientLocationStore = ResilientLocationStore(this)
        healthReporter = HealthReporter(serviceScope)

        serviceScope.launch {
            repository.recoverUnclosedSessions().forEach { stale ->
                observationQueue.drain(stale.sessionId)
                runCatching {
                    val session = repository.getSession(stale.sessionId) ?: return@runCatching
                    val metadata = JSONObject(session.deviceMetadataJson)
                    val diagnostics = metadata.optJSONObject("gnssDeliveryDiagnostics") ?: return@runCatching
                    if (diagnostics.optString("state") != "FINALIZED") {
                        diagnostics.put("state", "UNAVAILABLE_PROCESS_LOSS")
                            .put("countsComplete", false)
                            .put("invariantsHold", false)
                        metadata.put("gnssDeliveryDiagnostics", diagnostics)
                        repository.updateSessionDeviceMetadata(stale.sessionId, metadata.toString())
                    }
                }.onFailure { Log.e(SERVICE_TAG, "Failed to mark crash-session GNSS diagnostics incomplete session=${stale.sessionId}", it) }
                repository.markCrashClosed(stale.sessionId)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleActionStart()
            ACTION_STOP -> if (currentSessionId == null) stopSelf() else stopObserving()
            ACTION_EXPORT -> exportLastClosedSession()
            ACTION_SET_EMERGENCY_NAVIGATION -> {
                val enabled = intent.getBooleanExtra(EXTRA_ENABLED, false)
                emergencyNavigationController.updateEnabled(enabled)
                if (enabled) handleActionStart()
                else if (currentSessionId == null) stopSelf() else stopObserving()
            }
            null -> if (emergencyNavigationController.enabled) handleActionStart()
        }
        return if (emergencyNavigationController.enabled) START_STICKY else START_NOT_STICKY
    }

    private fun handleActionStart() {
        ContinuityStatusHub.report(ContinuityServiceStatus.STARTING)
        if (!canStartLocationForegroundService()) {
            ContinuityStatusHub.report(ContinuityServiceStatus.FAILED, ContinuityFailure.PERMISSION_MISSING)
            stopSelf(); return
        }
        if (!startForegroundServiceNotificationSafely()) {
            ContinuityStatusHub.report(ContinuityServiceStatus.FAILED, ContinuityFailure.FOREGROUND_START_REJECTED)
            stopSelf(); return
        }
        startObserving()
    }

    private fun startObserving() {
        if (currentSessionId != null || !startRequested.compareAndSet(false, true)) {
            if (currentSessionId != null) ContinuityStatusHub.report(ContinuityServiceStatus.ACTIVE)
            return
        }
        serviceScope.launch {
            val sessionId = try {
                repository.createSession()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(SERVICE_TAG, "createSession failed", t)
                startRequested.set(false)
                ContinuityStatusHub.report(ContinuityServiceStatus.FAILED, ContinuityFailure.SESSION_START_FAILED)
                return@launch
            }
            currentSessionId = sessionId
            gnssRawSource.resetDeliveryDiagnosticsForSession()
            sequencer = SessionSequencer()
            resilienceRuntime = ResilienceRuntime()
            publishGate = SnapshotPublishGate()

            val report = capabilityScanner.buildReport()
            val device = report.json.optJSONObject("device")?.toString() ?: "{}"
            val permissions = report.json.optJSONObject("permissions")?.toString() ?: "{}"
            val deviceMetadata = JSONObject(device).put("gnssDeliveryDiagnostics", gnssRawSource.deliveryDiagnosticsSnapshot())
            repository.updateSessionMetadata(sessionId, deviceMetadata.toString(), report.json.toString(), permissions)

            persistSystemEvent(sessionId, "SESSION_STARTED", JSONObject().put("capabilityReport", report.json))
            ContinuityStatusHub.report(ContinuityServiceStatus.ACTIVE)

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
                    val resilienceUpdate = resilienceRuntime?.accept(stamped)
                    if (stamped.type == ObservationType.GNSS_FIX) {
                        publishObservedGnssFix(stamped)
                    }
                    ContinuityStatusHub.reportNavigationState(resilienceRuntime?.navigationState)
                    // A sequence number is not reusable. Once allocated, the observation must
                    // survive collector cancellation long enough to reach the durable queue.
                    // Otherwise a normal stop can create an artificial gap such as 1..2281
                    // with only 2280 persisted rows.
                    withContext(NonCancellable) {
                        observationQueue.enqueue(sessionId, stamped)
                        if (resilienceUpdate != null) {
                            persistResilienceUpdateUnlocked(sessionId, stamped, resilienceUpdate)
                        }
                    }
                    observationQueue.drain(sessionId)
                    // Publish only after the observation has reached the durable evidence table.
                    // The bus is a live UI trigger; Room remains the authoritative data source.
                    liveObservationBus.publish(sessionId, stamped.toObservationEntity(sessionId))
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

                ContinuityStatusHub.report(ContinuityServiceStatus.STOPPING)
                healthJob?.cancel()
                listOf(gnssRawSource, gnssFixSource, cellularSource, sensorSource).forEach { it.stop() }
                var sourceShutdownComplete = true
                sourceJobs.forEach { job ->
                    // stop() closes each callbackFlow first. Join naturally so already accepted
                    // buffered channel elements can still reach launchSource's durable queue.
                    // Cancelling immediately after close can discard accepted-but-not-yet-collected
                    // observations, making source accepted counters exceed persisted rows.
                    val stopped = joinSourceJobGracefully(job, SOURCE_STOP_TIMEOUT_MS)
                    if (!stopped) sourceShutdownComplete = false
                }
                sourceJobs.clear()
                val diagnosticsFinalState = if (sourceShutdownComplete) "FINALIZED" else "FINALIZATION_INCOMPLETE"
                gnssRawSource.finalizeDeliveryDiagnostics(diagnosticsFinalState)
                if (!sourceShutdownComplete) {
                    Log.e(SERVICE_TAG, "GNSS delivery diagnostics are incomplete because one or more source jobs timed out session=$sessionId")
                }
                runCatching {
                    val session = repository.getSession(sessionId) ?: error("Session vanished during GNSS diagnostics finalization: $sessionId")
                    val metadata = JSONObject(session.deviceMetadataJson)
                    metadata.put("gnssDeliveryDiagnostics", gnssRawSource.deliveryDiagnosticsSnapshot())
                    repository.updateSessionDeviceMetadata(sessionId, metadata.toString())
                }.onFailure { Log.e(SERVICE_TAG, "Failed to persist GNSS delivery diagnostics session=$sessionId", it) }

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
            resilienceRuntime = null
            startRequested.set(false)
            ContinuityStatusHub.reportNavigationState(null)
            ContinuityStatusHub.report(ContinuityServiceStatus.STOPPED)
            Log.i(SERVICE_TAG, "Session COMPLETED session=$sessionId observations=${repository.countObservations(sessionId)}")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }


    /**
     * Publishes an OBSERVED_GNSS snapshot. A fix that lacks only OPTIONAL fields (for example vertical
     * accuracy) is still published, with those fields null. The raw observation is never modified.
     */
    private fun publishObservedGnssFix(observation: RawObservation) {
        val state = resilienceRuntime?.navigationState ?: NavigationState.FULL_GNSS
        when (val mapped = ObservedFixMapper.map(observation, state)) {
            is ObservedFixMapping.Mapped -> publishSnapshot(mapped.snapshot)
            is ObservedFixMapping.Rejected -> logRejection(mapped.reason)
        }
    }

    /** The only path to the store: the gate decides first (no time regression, no estimate over an observed fix). */
    private fun publishSnapshot(snapshot: ResilientLocationSnapshot) {
        when (val decision = publishGate.decide(snapshot)) {
            PublishDecision.Publish ->
                if (resilientLocationStore.publish(snapshot)) lastObservedRejection = null else logRejection("STORE_REFUSED")
            is PublishDecision.Skip -> logRejection("GATE_${decision.reason}")
        }
    }

    /** Logs once per distinct reason so a persistent fault cannot flood the log. */
    private fun logRejection(reason: String) {
        if (reason == lastObservedRejection) return
        lastObservedRejection = reason
        Log.w(SERVICE_TAG, "Resilient snapshot not published: $reason")
    }

    private suspend fun persistResilienceUpdateUnlocked(
        sessionId: String,
        sourceObservation: RawObservation,
        update: ResilienceRuntimeUpdate
    ) {
        val seq = sequencer?.nextSequenceNumber() ?: return
        val now = TimestampEngine.now()
        val payload = JSONObject().apply {
            put("sourceSequenceNumber", sourceObservation.sequenceNumber)
            put("navigationState", update.navigationState.name)
            put("interferenceState", update.interferenceState.name)
            put("provenance", update.provenance)
            update.interferenceAssessment?.let { assessment ->
                put("interferenceScore", assessment.score)
                put("interferenceEvidence", org.json.JSONArray().apply {
                    assessment.evidence.forEach { signal ->
                        put(JSONObject().put("name", signal.name).put("value", signal.value).put("weight", signal.weight))
                    }
                })
            }
            put("trustDecisions", org.json.JSONArray().apply {
                update.trustDecisions.filter { it.decision != horizon.observatory.resilience.TrustDecision.KEEP }.forEach { decision ->
                    put(JSONObject().put("id", decision.id).put("score", decision.score).put("decision", decision.decision.name).put("rank", decision.rank).put("reasons", org.json.JSONArray(decision.reasons)))
                }
            })
            update.transition?.let { transition ->
                put("transition", JSONObject().put("from", transition.from.name).put("to", transition.to.name))
            }
            update.estimate?.let { estimate ->
                put("estimate", JSONObject()
                    .put("latitudeDeg", estimate.latitudeDeg)
                    .put("longitudeDeg", estimate.longitudeDeg)
                    .put("altitudeM", estimate.altitudeM ?: JSONObject.NULL)
                    .put("timestampMonotonicNs", estimate.timestampMonotonicNs)
                    .put("horizontalUncertaintyM", estimate.horizontalUncertaintyM)
                    .put("verticalUncertaintyM", estimate.verticalUncertaintyM ?: JSONObject.NULL)
                    .put("navigationState", estimate.navigationState.name)
                    .put("provenance", estimate.provenance)
                    .put("fixKind", ResilientFixKind.SHORT_HORIZON_ESTIMATE.name)
                    .put("estimator", estimate.estimator)
                    .put("speedMps", estimate.speedMps ?: JSONObject.NULL)
                    .put("bearingDeg", estimate.bearingDeg ?: JSONObject.NULL))
            }
            update.coastingRejection?.let { put("coastingRejection", it.name) }
        }
        observationQueue.enqueue(
            sessionId,
            RawObservation(
                type = ObservationType.SYSTEM_EVENT,
                utcTimestampMs = sourceObservation.utcTimestampMs,
                monotonicTimestampNs = sourceObservation.monotonicTimestampNs,
                ingestionMonotonicTimestampNs = now.elapsedRealtimeNanos,
                provider = "HorizonResilienceRuntime",
                payloadJson = JSONObject().put("event", "RESILIENCE_UPDATE").put("details", payload).toString(),
                sequenceNumber = seq,
                technology = "RESILIENCE",
                capabilityState = CapabilityState.AVAILABLE_NOW,
                evidenceStatus = EvidenceStatus.DERIVED,
                provenance = ObservationProvenance.DERIVED,
                timestampDomain = sourceObservation.timestampDomain
            )
        )
        update.estimate?.let { estimate ->
            publishSnapshot(
                ResilientLocationSnapshot(
                    latitudeDeg = estimate.latitudeDeg,
                    longitudeDeg = estimate.longitudeDeg,
                    altitudeM = estimate.altitudeM,
                    horizontalUncertaintyM = estimate.horizontalUncertaintyM,
                    verticalUncertaintyM = estimate.verticalUncertaintyM,
                    timestampMonotonicNs = estimate.timestampMonotonicNs,
                    navigationState = estimate.navigationState,
                    provenance = estimate.provenance,
                    fixKind = ResilientFixKind.SHORT_HORIZON_ESTIMATE,
                    utcTimestampMs = TimestampEngine.elapsedNanosToUtcMillis(estimate.timestampMonotonicNs).takeIf { it > 0L },
                    speedMps = estimate.speedMps,
                    bearingDeg = estimate.bearingDeg,
                    estimator = estimate.estimator
                )
            )
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
        val isParallel = packageName.startsWith("horizon.observatory.parallel")
        val brand = if (isParallel) "Horizon Parallel" else when {
            packageName.startsWith("horizon.observatory.ts") -> "Horizon Observatory TS"
            packageName.startsWith("horizon.observatory.t1") -> "Horizon Observatory T1"
            else -> "Horizon Observatory"
        }
        val channelId = when {
            isParallel -> "horizon_parallel_observatory_channel"
            packageName.startsWith("horizon.observatory.ts") -> "observatory_ts_channel"
            packageName.startsWith("horizon.observatory.t1") -> "observatory_t1_channel"
            else -> "observatory_channel"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(channelId, "$brand Background Observation", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val continuity = emergencyNavigationController.enabled
        val stopIntent = Intent(this, ObservatoryService::class.java).apply {
            if (continuity) {
                action = ACTION_SET_EMERGENCY_NAVIGATION
                putExtra(EXTRA_ENABLED, false)
            } else action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, if (continuity) 2 else 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(if (continuity) "$brand - Location Continuity on" else brand)
            .setContentText(
                if (continuity) "Keeping GNSS acquisition active. Cannot defeat radio interference."
                else "Collecting raw observations locally"
            )
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .addAction(0, if (continuity) "Turn off" else "Stop", stopPending)
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
        ContinuityStatusHub.reportNavigationState(null)
        if (ContinuityStatusHub.state.value.status != ContinuityServiceStatus.FAILED) ContinuityStatusHub.report(ContinuityServiceStatus.STOPPED)
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
        const val ACTION_SET_EMERGENCY_NAVIGATION = "ACTION_SET_EMERGENCY_NAVIGATION"
        const val EXTRA_ENABLED = "enabled"
        private const val DRAIN_TIMEOUT_MS = 10_000L
        private const val SOURCE_STOP_TIMEOUT_MS = 3_000L
        private const val SERVICE_TAG = "HorizonService"
        private const val EXPORT_TAG = "HorizonExport"
    }
}
