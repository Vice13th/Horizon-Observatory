package horizon.observatory.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.DocumentsContract
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import horizon.observatory.HorizonApplication
import horizon.observatory.acquisition.service.ObservatoryService
import horizon.observatory.analysis.AnalysisSnapshot
import horizon.observatory.analysis.SessionAnalysisEngine
import horizon.observatory.astronomy.orientation.ControllableOrientationSource
import horizon.observatory.domain.gnss.GnssDomainSnapshot
import horizon.observatory.domain.gnss.SatelliteEvidence
import horizon.observatory.domain.gnss.SatelliteEvidenceMatch
import horizon.observatory.storage.entity.ObservationEntity
import horizon.observatory.storage.entity.SessionEntity
import horizon.observatory.live.MagneticDeclinationProvider
import horizon.observatory.live.ObservedSkyState
import horizon.observatory.live.ObservedSkyPoint
import horizon.observatory.live.SkyOrientationDecision
import horizon.observatory.live.SkyOrientationMode
import horizon.observatory.live.SkyProjection
import horizon.observatory.domain.model.IntegrityAudit
import horizon.observatory.domain.model.RawObservationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HorizonTheme { ObservatoryApp() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ObservatoryApp() {
        val container = (application as HorizonApplication).container
        var section by rememberSaveable { mutableStateOf(0) }
        var message by rememberSaveable { mutableStateOf("Ready") }
        val sessions by container.sessionRepository.observeAllSessions().collectAsStateCompat(emptyList())
        val latest = sessions.firstOrNull()
        val latestStateKey = latest?.let { "${it.sessionId}:${it.lifecycleState}" }
        val latestIsActive = latest?.lifecycleState == "RECORDING" || latest?.lifecycleState == "STOPPING"
        val observations by produceState<List<ObservationEntity>>(initialValue = emptyList(), key1 = latestStateKey) {
            value = when {
                latest == null -> emptyList()
                latestIsActive -> container.sessionRepository.getRecentObservationsSnapshot(latest.sessionId, LIVE_OBSERVATION_WINDOW)
                else -> container.sessionRepository.getObservationsSnapshot(latest.sessionId)
            }
            while (latest != null && latestIsActive) {
                delay(2000)
                value = container.sessionRepository.getRecentObservationsSnapshot(latest.sessionId, LIVE_OBSERVATION_WINDOW)
            }
        }
        val analysisEngine = remember { SessionAnalysisEngine() }
        var analysis by remember { mutableStateOf(analysisEngine.analyze(emptyList())) }
        LaunchedEffect(latest?.sessionId, latest?.lifecycleState, observations.size) {
            analysis = withContext(Dispatchers.Default) {
                analysisEngine.analyze(observations)
            }
        }
        val uiScope = androidx.compose.runtime.rememberCoroutineScope()
        var exportStatus by rememberSaveable { mutableStateOf("No export package generated yet.") }
        var exportPath by rememberSaveable { mutableStateOf<String?>(null) }
        var pendingSavePath by rememberSaveable { mutableStateOf<String?>(null) }
        var exportSessionId by rememberSaveable { mutableStateOf<String?>(null) }

        val saveExportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { treeUri ->
            val sourcePath = pendingSavePath
            pendingSavePath = null
            if (treeUri == null || sourcePath == null) return@rememberLauncherForActivityResult
            uiScope.launch(Dispatchers.IO) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
                runCatching {
                    val source = java.io.File(sourcePath)
                    check(source.isFile) { "Export ZIP no longer exists: $sourcePath" }
                    val targetName = source.name
                    check(DocumentsContract.isTreeUri(treeUri)) { "Selected URI is not a directory tree" }
                    val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        DocumentsContract.getTreeDocumentId(treeUri)
                    )
                    val targetUri = DocumentsContract.createDocument(
                        contentResolver,
                        parentDocumentUri,
                        "application/zip",
                        targetName
                    ) ?: error("Unable to create ZIP in the selected folder")
                    val output = contentResolver.openOutputStream(targetUri, "w")
                        ?: error("Unable to open ZIP output in the selected folder")
                    output.use { out ->
                        FileInputStream(source).use { input -> input.copyTo(out) }
                    }
                    val copiedBytes = contentResolver.openInputStream(targetUri)?.use { input ->
                        val buffer = ByteArray(DEFAULT_COPY_BUFFER)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                        }
                        total
                    } ?: 0L
                    check(copiedBytes == source.length() && copiedBytes > 0L) {
                        "ZIP read-back size mismatch: source=${source.length()} destination=$copiedBytes"
                    }
                    targetUri
                }.onSuccess { targetUri ->
                    withContext(Dispatchers.Main) {
                        exportStatus = "ZIP saved to selected folder: $targetUri"
                        message = "ZIP export completed."
                    }
                }.onFailure { error ->
                    withContext(Dispatchers.Main) {
                        exportStatus = "Folder export failed: ${error.message ?: error}"
                        message = "ZIP export failed."
                    }
                }
            }
        }

        val generateExport: () -> Unit = {
            section = 9
            exportStatus = "Looking for the latest completed session …"
            uiScope.launch {
                val completed = container.sessionRepository.getLatestCompletedSession()
                val id = completed?.sessionId
                if (id == null) {
                    exportStatus = if (latest == null) {
                        "No session is available for export."
                    } else {
                        "Export requires a completed session; current state is ${latest.lifecycleState}."
                    }
                    message = exportStatus
                    return@launch
                }

                exportSessionId = id
                exportStatus = "Generating package for $id …"
                when (val result = container.exportEngine.exportSession(id)) {
                    is horizon.observatory.export.ExportEngine.ExportResult.Success -> {
                        exportPath = result.zipFile.absolutePath
                        exportStatus = "Package ready: ${result.observationCount} observations."
                        message = "Export package ready."
                    }
                    is horizon.observatory.export.ExportEngine.ExportResult.Failed -> {
                        exportStatus = "Export failed at ${result.stage}: ${result.reason}"
                        message = "Export failed; see Export section for details."
                    }
                }
            }
        }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val location = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true || hasLocationPermission()
            message = if (location) "Location capability granted; optional capabilities may remain unavailable." else "Location permission is required for acquisition."
        }

        val exportSession = sessions.firstOrNull { it.sessionId == exportSessionId }
        val currentSectionTitle = sectionTitle(section)
        val selectedNav = when (section) {
            0 -> 0
            1 -> 1
            2 -> 2
            3 -> 3
            else -> 4
        }

        Scaffold(
            topBar = { TopAppBar(title = { Text(currentSectionTitle, fontWeight = FontWeight.SemiBold) }) },
            bottomBar = {
                NavigationBar {
                    listOf("Overview", "GNSS", "Sessions", "System", "More").forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selectedNav == index,
                            onClick = { section = index },
                            icon = { Text(label.take(1)) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (section) {
                    0 -> Column(Modifier.fillMaxSize()) {
                        SessionControls(
                            session = latest,
                            onPermissions = { permissionLauncher.launch(requestablePermissions()) },
                            onStart = {
                                if (latest?.lifecycleState == "RECORDING" || latest?.lifecycleState == "STOPPING") {
                                    message = "A session is already active."
                                } else if (!hasFineLocationPermission()) {
                                    message = "Grant precise (fine) location permission to start GNSS/cellular observation."
                                } else {
                                    val intent = Intent(this@MainActivity, ObservatoryService::class.java).setAction(ObservatoryService.ACTION_START)
                                    ContextCompat.startForegroundService(this@MainActivity, intent)
                                    message = "Observation start requested."
                                }
                            },
                            onStop = {
                                if (latest?.lifecycleState == "RECORDING" || latest?.lifecycleState == "STOPPING") {
                                    startService(Intent(this@MainActivity, ObservatoryService::class.java).setAction(ObservatoryService.ACTION_STOP))
                                    message = "Observation stop requested."
                                } else {
                                    message = "No active observation session."
                                }
                            }
                        )
                        if (message != "Ready") {
                            Text(message, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                        }
                        OverviewScreen(latest, analysis, gnssCn0Samples(observations))
                    }
                    1 -> GnssScreen(
                        obs = observations,
                        analysis = analysis,
                        orientationSource = container.deviceOrientationSource,
                        declinationProvider = container.magneticDeclinationProvider,
                        isLive = latestIsActive
                    )
                    2 -> SessionScreen(sessions)
                    3 -> SystemScreen(
                        session = latest,
                        capabilityJson = container.capabilityScanner.buildReport().json.toString(2)
                    )
                    4 -> MoreScreen(
                        onNavigate = { section = it },
                        hasCompletedSession = exportSession != null || sessions.any { it.lifecycleState == "COMPLETED" }
                    )
                    5 -> CellularScreen(observations, analysis)
                    6 -> SensorsScreen(observations, analysis)
                    7 -> TimelineScreen(observations, analysis)
                    8 -> DiagnosticsScreen(latest, container.capabilityScanner.buildReport().json.toString(2), analysis)
                    9 -> ExportScreen(
                        session = exportSession ?: sessions.firstOrNull { it.lifecycleState == "COMPLETED" },
                        observations = observations,
                        exportPath = exportPath,
                        exportStatus = exportStatus,
                        onGenerate = generateExport,
                        onSave = {
                            val path = exportPath
                            if (path == null) {
                                exportStatus = "Generate an export package first."
                            } else {
                                pendingSavePath = path
                                saveExportLauncher.launch(null)
                            }
                        }
                    )
                    10 -> LogsScreen(observations)
                    11 -> AntennaScreen(analysis.gnssDomain)
                }
            }
        }
    }

    private fun gnssCn0Samples(observations: List<ObservationEntity>): List<Double> =
        observations.asSequence()
            .filter { it.type == "GNSS_RAW_MEASUREMENT" }
            .mapNotNull { row -> numericField(row, "cn0DbHz") }
            .filter { it.isFinite() }
            .toList()
            .takeLast(120)

    private fun cellularRsrpSamples(observations: List<ObservationEntity>): List<Double> =
        observations.asSequence()
            .filter { it.type == "CELLULAR_INFO" }
            .mapNotNull { row ->
                numericField(row, "rsrp", "ssRsrp")
            }
            .filter { it.isFinite() }
            .toList()
            .takeLast(120)

    private fun numericField(row: ObservationEntity, vararg keys: String): Double? {
        fun read(jsonText: String): Double? = runCatching {
            val json = JSONObject(jsonText)
            keys.firstNotNullOfOrNull { key ->
                when (val value = json.opt(key)) {
                    is Number -> value.toDouble()
                    else -> null
                }
            }
        }.getOrNull()
        return read(row.rawPayloadJson) ?: read(row.normalizedPayloadJson)
    }

    companion object {
        private const val DEFAULT_COPY_BUFFER = 64 * 1024
private const val LIVE_OBSERVATION_WINDOW = 512
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasFineLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestablePermissions(): Array<String> {
        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list += Manifest.permission.POST_NOTIFICATIONS
        // Fixed during full audit: ACTIVITY_RECOGNITION was declared in the manifest but never
        // actually requested here -- SensorObservationSource.checkSelfPermission() for it would
        // always have returned DENIED on API 29+ (declaring a dangerous permission is necessary
        // but not sufficient; the user must be prompted too), silently disabling step sensors.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) list += Manifest.permission.ACTIVITY_RECOGNITION
        return list.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
    }
}

private fun sectionTitle(section: Int): String = when (section) {
    0 -> "HORIZON / OBSERVATORY"
    1 -> "GNSS OBSERVATORY"
    2 -> "SESSION HISTORY"
    3 -> "SYSTEM & CAPABILITIES"
    4 -> "MORE"
    5 -> "CELLULAR OBSERVATORY"
    6 -> "SENSOR OBSERVATORY"
    7 -> "EVIDENCE TIMELINE"
    8 -> "DIAGNOSTICS"
    9 -> "EXPORT CENTER"
    10 -> "INTERNAL LOG"
    11 -> "ANTENNA OBSERVATORY"
    else -> "HORIZON / OBSERVATORY"
}

@Composable
private fun SessionControls(
    session: SessionEntity?,
    onPermissions: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val active = session?.lifecycleState == "RECORDING" || session?.lifecycleState == "STOPPING"
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SESSION CONTROL", style = MaterialTheme.typography.labelSmall)
                    Text(session?.lifecycleState ?: "NO SESSION", fontWeight = FontWeight.Bold)
                }
                Text(
                    if (active) "ACTIVE" else "IDLE",
                    color = if (active) Color(0xFFE1A4FF) else Color(0xFFB8AEBE),
                    fontWeight = FontWeight.Bold
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, enabled = !active, modifier = Modifier.weight(1f)) { Text("START") }
                OutlinedButton(onClick = onStop, enabled = active, modifier = Modifier.weight(1f)) { Text("STOP") }
                OutlinedButton(onClick = onPermissions, modifier = Modifier.weight(1.3f)) { Text("PERMISSIONS") }
            }
        }
    }
}

@Composable
private fun MoreScreen(onNavigate: (Int) -> Unit, hasCompletedSession: Boolean) {
    val destinations = listOf(
        5 to ("Cellular" to "Serving and neighboring cell observations"),
        6 to ("Sensors" to "Runtime-discovered sensor observations"),
        7 to ("Timeline" to "Chronological evidence stream"),
        8 to ("Diagnostics" to "Capabilities, integrity and runtime diagnostics"),
        9 to ("Export" to if (hasCompletedSession) "Verified session package and save" else "Export requires a completed session"),
        10 to ("Internal Log" to "Persisted system and error events for the current session"),
        11 to ("Antenna" to "GNSS antenna evidence and observed receiver context")
    )
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("OBSERVATORY MODULES", style = MaterialTheme.typography.titleMedium)
                    Text("Secondary views are kept out of the primary navigation so the mobile layout remains readable.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(destinations) { (target, content) ->
            OutlinedButton(onClick = { onNavigate(target) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                    Text(content.first, fontWeight = FontWeight.SemiBold)
                    Text(content.second, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun SystemScreen(session: SessionEntity?, capabilityJson: String) {
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { MetricCard("Device", "${session?.deviceManufacturer ?: "UNKNOWN"} ${session?.deviceModel ?: "UNKNOWN"}", "Android ${session?.androidVersion ?: "UNKNOWN"} / API ${session?.sdkVersion ?: 0}") }
        item { MetricCard("Session", session?.sessionId ?: "NONE", session?.lifecycleState ?: "NO SESSION") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("CAPABILITY REPORT", style = MaterialTheme.typography.titleMedium)
                    Text(capabilityJson, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("PERMISSION STATE", style = MaterialTheme.typography.titleMedium)
                    Text(session?.permissionStateJson ?: "{}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun OverviewScreen(session: SessionEntity?, analysis: AnalysisSnapshot, cnoSamples: List<Double>) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { MetricCard("Session", session?.sessionId ?: "NONE", session?.lifecycleState ?: "NO SESSION") }
        item { MetricGrid(analysis) }
        item { SignalCard("GNSS C/N0 history", "dB-Hz", analysis.averageCn0DbHz, cnoSamples) }
        item { SignalSummary(analysis) }
        item { EvidenceCard(session) }
    }
}

@Composable
private fun MetricGrid(a: AnalysisSnapshot) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("OBSERVATION MATRIX", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("RAW GNSS", a.gnssRawCount.toString(), Modifier.weight(1f))
                Metric("GNSS STATUS", a.gnssStatusCount.toString(), Modifier.weight(1f))
                Metric("FIX", a.gnssFixCount.toString(), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("CELLULAR", a.cellularCount.toString(), Modifier.weight(1f))
                Metric("SENSORS", a.sensorCount.toString(), Modifier.weight(1f))
                Metric("TOTAL", a.observationCount.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun MetricCard(title: String, value: String, state: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF211526))) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace, maxLines = 2)
            Text(state, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SignalCard(title: String, unit: String, value: Double?, samples: List<Double> = emptyList()) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "HISTORY SAMPLES: ${samples.size}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text(
                    value?.let { "%.1f $unit".format(it) } ?: "UNAVAILABLE",
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            MiniLineChart(samples)
        }
    }
}

@Composable
private fun SignalSummary(a: AnalysisSnapshot) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("DERIVED ANALYSIS", style = MaterialTheme.typography.titleMedium)
            Text("Average C/N0: ${a.averageCn0DbHz?.let { "%.2f dB-Hz".format(it) } ?: "UNAVAILABLE"}")
            Text("Average LTE/NR RSRP: ${a.averageRsrpDbm?.let { "%.2f dBm".format(it) } ?: "UNAVAILABLE"}")
            Text("Average visible satellites: ${a.averageVisibleSatellites?.let { "%.1f".format(it) } ?: "UNAVAILABLE"}")
            Text("Navigation messages: ${a.navigationMessageCount}")
            Text("AGC: ${a.agcState} · samples=${a.agcSampleCount} · spread=${a.agcSpreadDb?.let { "%.4f dB".format(it) } ?: "UNAVAILABLE"}")
            Text("Sequence gaps: ${a.sequenceGapCount}")
            Text("Source-time regressions: ${a.timestampMonotonicViolations}")
        }
    }
}

@Composable
private fun EvidenceCard(session: SessionEntity?) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("EVIDENCE", style = MaterialTheme.typography.titleMedium)
            Text("Device: ${session?.deviceManufacturer ?: "UNKNOWN"} ${session?.deviceModel ?: "UNKNOWN"}")
            Text("Android: ${session?.androidVersion ?: "UNKNOWN"} / API ${session?.sdkVersion ?: 0}")
            Text("Schema: ${session?.schemaVersion ?: 0}")
            Text("Source measurement time and HORIZON ingress time are preserved separately; derived values remain separate from raw observations.")
        }
    }
}

@Composable
private fun GnssScreen(
    obs: List<ObservationEntity>,
    analysis: AnalysisSnapshot,
    orientationSource: ControllableOrientationSource,
    declinationProvider: MagneticDeclinationProvider,
    isLive: Boolean
) {
    val domain = analysis.gnssDomain
    val rawRows = obs.filter { it.type == "GNSS_RAW_MEASUREMENT" }.takeLast(160)
    val cnoHistory = rawRows.mapNotNull { numericFieldLocal(it, "cn0DbHz") }.filter { it.isFinite() }
    val agcHistory = rawRows.mapNotNull { numericFieldLocal(it, "automaticGainControlLevelDb") }.filter { it.isFinite() }
    val navigationMessages = obs.filter { it.type == "GNSS_NAVIGATION_MESSAGE" }.takeLast(40)
    val matrix = domain.latestSatelliteEvidence
        .filter { it.svid >= 0 && (it.elevationDegrees?.isFinite() != false) && (it.azimuthDegrees?.isFinite() != false) }
        .sortedWith(compareByDescending<SatelliteEvidence> { it.cn0DbHz ?: Double.NEGATIVE_INFINITY }.thenBy { it.constellationType }.thenBy { it.svid })
        .take(18)
    val observedSky = remember(matrix) { ObservedSkyState.from(matrix) }
    val fixRow = obs.lastOrNull { it.type == "GNSS_FIX" }
    val observerLat = fixRow?.let { numericFieldLocal(it, "latitude") }
    val observerLon = fixRow?.let { numericFieldLocal(it, "longitude") }
    val observerAlt = fixRow?.let { numericFieldLocal(it, "altitude") }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { SignalCard("GNSS signal history", "dB-Hz", analysis.averageCn0DbHz, cnoHistory) }
        item { SignalCard("GNSS AGC history", "dB", null, agcHistory) }
        item {
            MetricCard(
                "GNSS DOMAIN",
                domain.latestSatelliteEvidence.size.toString(),
                "latest satellite evidence · correlated=${domain.correlatedMeasurementCount} · nav=${domain.navigationMessageCount}"
            )
        }
        item {
            MetricCard(
                "AGC DIAGNOSTIC",
                analysis.agcState,
                if (analysis.agcSampleCount == 0) "NO AGC MEASUREMENTS OBSERVED"
                else "samples=${analysis.agcSampleCount} distinct=${analysis.agcDistinctLevels} spread=${analysis.agcSpreadDb?.let { "%.4f dB".format(it) } ?: "UNAVAILABLE"}"
            )
        }
        item { LiveSkyObservatory(observedSky, observerLat, observerLon, observerAlt, orientationSource, declinationProvider, isLive) }
        item { CnoElevationPlot(domain) }
        item { ConstellationSummaryPanel(domain) }
        item { AntennaEvidencePanel(domain) }
        item { NavigationMessagePanel(count = analysis.navigationMessageCount, latest = navigationMessages.lastOrNull()) }
        item { MetricCard("Raw measurements", analysis.gnssRawCount.toString(), if (rawRows.isEmpty()) "NOT OBSERVED IN THIS SESSION" else "MEASURED — RAW EVIDENCE PRESERVED") }
        if (rawRows.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("GNSS RAW MEASUREMENTS", style = MaterialTheme.typography.titleMedium)
                        Text("No persisted GNSS_RAW_MEASUREMENT records are available for this session.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        items(rawRows.takeLast(40)) { item -> ObservationRow(item) }
    }
}

/**
 * Owns the live-pose side effects for the OBSERVED sky plot so only this card recomposes at sensor
 * rate. The pose is a presentation input: it is read here, turned into an orientation decision, and
 * never written into any observation or evidence.
 */
@Composable
private fun LiveSkyObservatory(
    observed: ObservedSkyState,
    observerLat: Double?,
    observerLon: Double?,
    observerAlt: Double?,
    orientationSource: ControllableOrientationSource,
    declinationProvider: MagneticDeclinationProvider,
    isLive: Boolean
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, orientationSource, isLive) {
        if (!isLive) {
            return@DisposableEffect onDispose { }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> orientationSource.start()
                Lifecycle.Event.ON_PAUSE -> orientationSource.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            orientationSource.stop()
        }
    }
    val pose by orientationSource.orientation.collectAsState()
    val declinationDeg = remember(observerLat, observerLon, observerAlt) {
        if (observerLat != null && observerLon != null) {
            declinationProvider.declinationDeg(observerLat, observerLon, observerAlt, System.currentTimeMillis())
        } else {
            null
        }
    }
    val decision = SkyProjection.decide(pose, declinationDeg, isLive)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CorrelatedSkyPlot(observed, decision)
        SatellitePanelField(observed, decision)
    }
}

@Composable
private fun CorrelatedSkyPlot(observed: ObservedSkyState, orientation: SkyOrientationDecision) {
    val mode = orientation.mode
    val labelPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY
            textSize = 28f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("SATELLITE SKY PLOT", style = MaterialTheme.typography.titleMedium)
            Text(
                "Correlated observed azimuth/elevation · ${observed.points.size} latest satellites" +
                    if (observed.excludedWithoutGeometry > 0) " · ${observed.excludedWithoutGeometry} without az/el not plotted" else "",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                if (mode is SkyOrientationMode.HeadingUp) "Orientation: device-top-up · ${orientation.detail}"
                else "Orientation: north-up · ${orientation.detail}",
                style = MaterialTheme.typography.labelSmall
            )
            Box(Modifier.fillMaxWidth().height(250.dp).background(Color(0xFF0D0B10))) {
                Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                    val diameter = min(size.width, size.height) * 0.82f
                    val radius = diameter / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val ringFractions = floatArrayOf(1f, 2f / 3f, 1f / 3f)
                    ringFractions.forEach { fraction ->
                        drawCircle(Color(0xFF3A3440), radius * fraction, center, style = Stroke(width = 1f))
                    }
                    observed.points.forEach { point ->
                        val p = SkyProjection.project(point.azimuthDeg, point.elevationDeg, mode) ?: return@forEach
                        val x = center.x + (p.x * radius).toFloat()
                        val y = center.y - (p.y * radius).toFloat()
                        val r = if (point.usedInFix == true) 6f else 4f
                        drawCircle(Color(0xFFC875FF), r, Offset(x, y))
                    }
                    if (mode is SkyOrientationMode.HeadingUp) {
                        drawIntoCanvas { canvas ->
                            listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0).forEach { (label, az) ->
                                val c = SkyProjection.cardinal(az, mode)
                                canvas.nativeCanvas.drawText(
                                    label,
                                    center.x + (c.x * (radius + 18f)).toFloat(),
                                    center.y - (c.y * (radius + 18f)).toFloat() + 10f,
                                    labelPaint
                                )
                            }
                        }
                    }
                }
                if (mode !is SkyOrientationMode.HeadingUp) {
                    Text("N", Modifier.align(Alignment.TopCenter).padding(top = 4.dp), style = MaterialTheme.typography.labelSmall)
                    Text("S", Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), style = MaterialTheme.typography.labelSmall)
                    Text("W", Modifier.align(Alignment.CenterStart).padding(start = 5.dp), style = MaterialTheme.typography.labelSmall)
                    Text("E", Modifier.align(Alignment.CenterEnd).padding(end = 5.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun SatellitePanelField(
    observed: ObservedSkyState,
    orientation: SkyOrientationDecision
) {
    val mode = orientation.mode
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("SATELLITE PANELS", style = MaterialTheme.typography.titleMedium)
            Text(
                orientation.detail,
                style = MaterialTheme.typography.labelSmall
            )
            if (observed.points.isEmpty()) {
                Text(
                    "NO OBSERVED SATELLITES WITH VALID AZIMUTH/ELEVATION",
                    style = MaterialTheme.typography.bodySmall
                )
                return@Column
            }

            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(Color(0xFF0D0B10))
            ) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }
                val heightPx = with(density) { maxHeight.toPx() }
                val centerX = widthPx / 2f
                val centerY = heightPx / 2f
                val radiusPx = min(widthPx, heightPx) * 0.38f
                val panelWidthPx = with(density) { 96.dp.toPx() }
                val panelHeightPx = with(density) { 64.dp.toPx() }

                observed.points.take(14).forEach { point ->
                    val projected = SkyProjection.project(
                        point.azimuthDeg,
                        point.elevationDeg,
                        mode
                    ) ?: return@forEach
                    val xDp = with(density) {
                        (centerX + (projected.x.toFloat() * radiusPx) - panelWidthPx / 2f).toDp()
                    }
                    val yDp = with(density) {
                        (centerY - (projected.y.toFloat() * radiusPx) - panelHeightPx / 2f).toDp()
                    }

                    SatellitePanelCard(
                        point = point,
                        modifier = Modifier.offset(x = xDp, y = yDp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SatellitePanelCard(
    point: ObservedSkyPoint,
    modifier: Modifier = Modifier
) {
    val name = "${constellationLabel(point.satelliteId.constellationType)} ${point.satelliteId.svid}"
    Card(
        modifier = modifier.size(width = 96.dp, height = 64.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF211A28)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (point.usedInFix == true) Color(0xFFC875FF) else Color(0xFF4A4050)
        )
    ) {
        Column(
            Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                "C/N0 ${point.cn0DbHz?.let { "%.1f".format(it) } ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "EL ${"%.0f".format(point.elevationDeg)}° AZ ${"%.0f".format(point.azimuthDeg)}°",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun CnoElevationPlot(domain: GnssDomainSnapshot) {
    val points = domain.historyPoints.filter {
        it.cn0DbHz?.isFinite() == true && it.elevationDegrees?.isFinite() == true
    }.takeLast(220)
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("C/N0 VS ELEVATION", style = MaterialTheme.typography.titleMedium)
            Text("Observed correlated points · n=${points.size}", style = MaterialTheme.typography.labelSmall)
            Box(Modifier.fillMaxWidth().height(210.dp).background(Color(0xFF0D0B10))) {
                Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                    if (points.isNotEmpty()) {
                        val minCno = points.mapNotNull { it.cn0DbHz }.minOrNull() ?: 0.0
                        val maxCno = points.mapNotNull { it.cn0DbHz }.maxOrNull() ?: 1.0
                        val cnoRange = (maxCno - minCno).takeIf { it > 0.0 } ?: 1.0
                        points.forEach { point ->
                            val e = (point.elevationDegrees ?: return@forEach).coerceIn(0.0, 90.0)
                            val c = point.cn0DbHz ?: return@forEach
                            val x = (e / 90.0 * size.width).toFloat()
                            val y = (1.0 - ((c - minCno) / cnoRange)).toFloat() * size.height
                            drawCircle(Color(0xFFE1A4FF), 3.5f, Offset(x, y))
                        }
                    }
                }
            }
            Text("X: elevation (0°→90°) · Y: observed C/N0", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ConstellationSummaryPanel(domain: GnssDomainSnapshot) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("CONSTELLATION SUMMARY", style = MaterialTheme.typography.titleMedium)
            if (domain.constellationSummaries.isEmpty()) {
                Text("NO CORRELATED CONSTELLATION DATA", style = MaterialTheme.typography.bodySmall)
            } else {
                domain.constellationSummaries.forEach { s ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(constellationLabel(s.constellationType), fontWeight = FontWeight.SemiBold)
                        Text("sat=${s.distinctSatelliteCount} match=${s.matchedCount} raw=${s.rawOnlyCount} status=${s.statusOnlyCount}", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        "C/N0=${s.averageCn0DbHz?.let { "%.1f dB-Hz".format(it) } ?: "UNAVAILABLE"} · " +
                            "elev=${s.averageElevationDegrees?.let { "%.1f°".format(it) } ?: "UNAVAILABLE"} · " +
                            "nav=${s.navigationMessageCount}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun SatelliteEvidenceMatrix(points: List<SatelliteEvidence>) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("CORRELATED SATELLITE EVIDENCE", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("SAT", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp))
                Text("CN0", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(52.dp))
                Text("EL", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(44.dp))
                Text("AZ", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(48.dp))
                Text("MATCH", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(76.dp))
            }
            points.forEach { point ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${constellationLabel(point.constellationType)} ${point.svid}", modifier = Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    Text(point.cn0DbHz?.let { "%.1f".format(it) } ?: "—", modifier = Modifier.width(52.dp), style = MaterialTheme.typography.labelSmall)
                    Text(point.elevationDegrees?.let { "%.0f°".format(it) } ?: "—", modifier = Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall)
                    Text(point.azimuthDegrees?.let { "%.0f°".format(it) } ?: "—", modifier = Modifier.width(48.dp), style = MaterialTheme.typography.labelSmall)
                    Text(
                        when (point.match) {
                            SatelliteEvidenceMatch.STATUS_AND_RAW_MATCH -> "STATUS+RAW"
                            SatelliteEvidenceMatch.STATUS_ONLY -> "STATUS"
                            SatelliteEvidenceMatch.RAW_ONLY -> "RAW"
                        },
                        modifier = Modifier.width(76.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(if (point.usedInFix == true) "FIX" else "TRACK", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (points.isEmpty()) Text("NO CORRELATED SATELLITE DATA", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AntennaEvidencePanel(domain: GnssDomainSnapshot) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("GNSS ANTENNA EVIDENCE", style = MaterialTheme.typography.titleMedium)
            if (domain.antennaEvidence.isEmpty()) {
                Text("NO GNSS_ANTENNA_INFO OBSERVATIONS", style = MaterialTheme.typography.bodySmall)
                Text("Antenna hardware properties remain unobserved; no gain or radiation model is inferred.", style = MaterialTheme.typography.labelSmall)
            } else {
                Text("Observed antenna records: ${domain.antennaEvidence.size}", style = MaterialTheme.typography.labelMedium)
                domain.antennaEvidence.takeLast(8).forEach { a ->
                    Text(
                        "freq=${a.carrierFrequencyMHz?.let { "%.3f MHz".format(it) } ?: "UNAVAILABLE"} · capability=${a.capabilityState}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text("PCO=${a.phaseCenterOffset ?: "UNAVAILABLE"}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

private fun constellationLabel(type: Int): String = when (type) {
    1 -> "GPS"
    2 -> "SBAS"
    3 -> "GLO"
    4 -> "QZSS"
    5 -> "BDS"
    6 -> "GAL"
    7 -> "IRNSS"
    else -> "UNK"
}

@Composable
private fun NavigationMessagePanel(count: Int, latest: ObservationEntity?) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("NAVIGATION MESSAGE STREAM", style = MaterialTheme.typography.titleMedium)
            Text("Observed messages: $count", style = MaterialTheme.typography.labelMedium)
            if (latest == null) {
                Text("NOT OBSERVED IN THIS SESSION", style = MaterialTheme.typography.bodySmall)
            } else {
                val json = runCatching { JSONObject(latest.rawPayloadJson) }.getOrNull()
                Text("SVID ${json?.optInt("svid", -1)} · type ${json?.optInt("type", -1)} · status ${json?.optInt("status", -1)}", style = MaterialTheme.typography.bodySmall)
                Text("messageId=${json?.optInt("messageId", -1)} submessageId=${json?.optInt("submessageId", -1)} length=${json?.optInt("dataLength", -1)} bytes", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                Text("Raw navigation payload is preserved in the observation record.", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun cellularRsrpSamples(observations: List<ObservationEntity>): List<Double> {
    val serving = observations.filter { it.type == "CELLULAR_INFO" && isServingCellRow(it) }
    val source = if (serving.isNotEmpty()) serving else observations.filter { it.type == "CELLULAR_INFO" }
    return source.asSequence()
        .mapNotNull { numericFieldLocal(it, "rsrp", "ssRsrp") }
        .filter { it.isFinite() }
        .toList()
        .takeLast(120)
}

private fun isServingCellRow(row: ObservationEntity): Boolean = runCatching {
    val json = JSONObject(row.rawPayloadJson)
    json.optBoolean("registered", false) || json.optInt("connectionStatus", 0) == 1 || json.optInt("connectionStatus", 0) == 2
}.getOrDefault(false)

private fun latestServingCellRow(observations: List<ObservationEntity>): ObservationEntity? =
    observations.filter { it.type == "CELLULAR_INFO" && isServingCellRow(it) }
        .maxByOrNull { it.timestampUtcMs }
        ?: observations.filter { it.type == "CELLULAR_INFO" }.maxByOrNull { it.timestampUtcMs }

@Composable
private fun CellularScreen(obs: List<ObservationEntity>, analysis: AnalysisSnapshot) {
    val rows = obs.filter { it.type == "CELLULAR_INFO" }.takeLast(100)
    val samples = cellularRsrpSamples(obs)
    val latest = latestServingCellRow(obs)
    val latestJson = latest?.let { runCatching { JSONObject(it.rawPayloadJson) }.getOrNull() }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            MetricCard(
                "LIVE SERVING CELL",
                analysis.latestServingTechnology ?: latest?.technology ?: "UNAVAILABLE",
                "RSRP=${analysis.latestServingRsrpDbm?.let { "%.1f dBm".format(it) } ?: "UNAVAILABLE"} · age=${analysis.latestCellAgeMs?.let { "${it} ms" } ?: "UNAVAILABLE"}"
            )
        }
        item { SignalCard("Cellular RSRP history", "dBm", analysis.latestServingRsrpDbm ?: analysis.averageRsrpDbm, samples) }
        item {
            MetricCard(
                "LIVE CELLULAR FEED",
                samples.size.toString(),
                "explicit refresh=${analysis.freshCellRequestSamples} · callbacks=${analysis.cellularCallbackSamples} · total observations=${analysis.cellularCount}"
            )
        }
        if (latestJson != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("SERVING CELL", style = MaterialTheme.typography.titleMedium)
                        Text("${latest?.technology ?: "UNKNOWN"}  MCC ${latestJson.optString("mcc", "—")}  MNC ${latestJson.optString("mnc", "—")}")
                        Text("PCI ${latestJson.opt("pci")}  TAC ${latestJson.opt("tac")}  EARFCN ${latestJson.opt("earfcn")}", style = MaterialTheme.typography.labelMedium)
                        Text("RSRP ${latestJson.opt("rsrp")} · RSRQ ${latestJson.opt("rsrq")} · RSSNR ${latestJson.opt("rssnr")}", style = MaterialTheme.typography.labelMedium)
                        Text("Trigger ${latestJson.optString("acquisitionTrigger", "UNKNOWN")} · freshness ${latestJson.optString("freshness", "UNKNOWN")}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        items(rows.takeLast(40)) { ObservationRow(it) }
    }
}

private fun numericFieldLocal(row: ObservationEntity, vararg keys: String): Double? {
    fun read(jsonText: String): Double? = runCatching {
        val json = JSONObject(jsonText)
        keys.firstNotNullOfOrNull { key ->
            when (val value = json.opt(key)) {
                is Number -> value.toDouble()
                else -> null
            }
        }
    }.getOrNull()
    return read(row.rawPayloadJson) ?: read(row.normalizedPayloadJson)
}


@Composable
private fun SensorsScreen(obs: List<ObservationEntity>, analysis: AnalysisSnapshot) {
    val rows = obs.filter { it.source == "SENSOR" }.takeLast(60)
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { MetricCard("Sensor observations", analysis.sensorCount.toString(), analysis.estimatedSensorMagnitude?.let { "Mean vector magnitude %.3f".format(it) } ?: "UNAVAILABLE") }
        items(rows.takeLast(50)) { ObservationRow(it) }
    }
}

@Composable
private fun TimelineScreen(obs: List<ObservationEntity>, analysis: AnalysisSnapshot) {
    val timeline = SessionAnalysisEngine().timeline(obs).takeLast(100)
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { MetricCard("Unified timeline", timeline.size.toString(), "monotonic ordering preferred") }
        items(timeline) { p ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("#${p.sequence} ${p.type}", fontFamily = FontFamily.Monospace)
                    Text(p.technology)
                }
            }
        }
    }
}

@Composable
private fun SessionScreen(sessions: List<SessionEntity>) {
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(sessions) { session ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
                Column(Modifier.padding(12.dp)) {
                    Text(session.sessionId, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text(session.lifecycleState)
                    Text("Started ${formatUtc(session.startTimeMs)}")
                    session.endTimeMs?.let { Text("Ended ${formatUtc(it)}") }
                }
            }
        }
    }
}

@Composable
private fun AntennaScreen(domain: GnssDomainSnapshot) {
    val stats = domain.signalStatistics
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            MetricCard(
                "GNSS ANTENNA CAPABILITY",
                if (domain.antennaEvidence.isEmpty()) "UNAVAILABLE / NOT OBSERVED" else "OBSERVED",
                if (domain.antennaEvidence.isEmpty()) "No GnssAntennaInfo observation was persisted in this session."
                else "${domain.antennaEvidence.size} raw antenna-info observations preserved."
            )
        }
        item {
            MetricCard(
                "OBSERVED RECEIVER CONTEXT",
                stats.sampleCount.toString(),
                "C/N0 samples · avg=${stats.averageCn0DbHz?.let { "%.2f dB-Hz".format(it) } ?: "UNAVAILABLE"} · " +
                    "elevation avg=${stats.averageElevationDegrees?.let { "%.1f°".format(it) } ?: "UNAVAILABLE"}"
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("OBSERVED FREQUENCIES", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (stats.distinctFrequenciesHz.isEmpty()) "UNAVAILABLE"
                        else stats.distinctFrequenciesHz.joinToString(" · ") { "%.0f MHz".format(it / 1_000_000.0) },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text("These are receiver-observed GNSS frequencies, not antenna gain measurements.", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ANTENNA HARDWARE EVIDENCE", style = MaterialTheme.typography.titleMedium)
                    if (domain.antennaEvidence.isEmpty()) {
                        Text("Phase center / gain-correction hardware metadata is not observed in this session.", style = MaterialTheme.typography.bodySmall)
                        Text("No radiation pattern, gain, efficiency or phase-center calibration is inferred.", style = MaterialTheme.typography.labelSmall)
                    } else {
                        domain.antennaEvidence.takeLast(12).forEachIndexed { index, a ->
                            Text("#${index + 1} freq=${a.carrierFrequencyMHz?.let { "%.3f MHz".format(it) } ?: "UNAVAILABLE"}", fontWeight = FontWeight.SemiBold)
                            Text("PCO: ${a.phaseCenterOffset ?: "UNAVAILABLE"}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                            Text("PCV: ${a.phaseCenterVariationCorrections ?: "UNAVAILABLE"}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                            Text("Gain corrections: ${a.signalGainCorrections ?: "UNAVAILABLE"}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogsScreen(obs: List<ObservationEntity>) {
    val entries = obs
        .filter { it.type == "SYSTEM_EVENT" || it.type == "ERROR_EVENT" }
        .takeLast(200)

    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            MetricCard(
                "Internal Log",
                entries.size.toString(),
                if (entries.isEmpty()) {
                    "NO PERSISTED SYSTEM/ERROR EVENTS FOR THIS SESSION"
                } else {
                    "Persisted HORIZON application events"
                }
            )
        }

        if (entries.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("LOG STREAM EMPTY", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Start a session to create SESSION_STARTED and lifecycle events. " +
                                "Source failures are recorded as ERROR_EVENT records.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            items(entries) { entry ->
                val payload = runCatching { JSONObject(entry.rawPayloadJson) }.getOrNull()
                val isError = entry.type == "ERROR_EVENT"
                val title = if (isError) {
                    "ERROR · ${payload?.optString("source", entry.provider) ?: entry.provider}"
                } else {
                    payload?.optString("event", "SYSTEM_EVENT") ?: "SYSTEM_EVENT"
                }
                val details = if (isError) {
                    payload?.optString("message", "")?.takeIf { it.isNotBlank() } ?: "No error message recorded"
                } else {
                    payload?.optJSONObject("details")?.toString() ?: payload?.optString("details", "") ?: ""
                }

                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "#${entry.sequenceNumber}  $title",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            formatUtc(entry.timestampUtcMs),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace
                        )
                        if (details.isNotBlank()) {
                            Text(
                                details,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = if (isError) FontFamily.Monospace else FontFamily.Default
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportScreen(
    session: SessionEntity?,
    observations: List<ObservationEntity>,
    exportPath: String?,
    exportStatus: String,
    onGenerate: () -> Unit,
    onSave: () -> Unit
) {
    val audit = remember(observations) {
        IntegrityAudit.audit(
            observations.map { row ->
                RawObservationView(
                    sequenceNumber = row.sequenceNumber,
                    sourceMonotonicTimestampNs = row.monotonicTimestampNs,
                    ingestionMonotonicTimestampNs = row.ingestionMonotonicTimestampNs,
                    provider = row.provider
                )
            }
        )
    }
    val eventCount = observations.count { it.type == "SYSTEM_EVENT" }
    val errorCount = observations.count { it.type == "ERROR_EVENT" }
    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricCard("Export", if (exportPath != null) "READY" else "NOT GENERATED", exportStatus)
        MetricCard("Session", session?.sessionId ?: "NONE", session?.lifecycleState ?: "NO SESSION")
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("EXPORT INTEGRITY", style = MaterialTheme.typography.titleMedium)
                Text("Integrity clean: ${audit.isClean}")
                Text("Sequence contiguous: ${audit.sequenceContiguous}")
                Text("Ingress timestamp complete: ${audit.ingestionTimestampedObservationCount == audit.observationCount}")
                Text("Ingress time monotonic: ${audit.ingestionTimestampsNonDecreasing}")
                Text("Source-time regressions: ${audit.sourceTimestampRegressions} (diagnostic, not a failure by itself)")
                Text("Observations: ${observations.size}  |  System events: $eventCount  |  Errors: $errorCount")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onGenerate, modifier = Modifier.weight(1f)) { Text("GENERATE PACKAGE") }
            OutlinedButton(onClick = onSave, enabled = exportPath != null, modifier = Modifier.weight(1f)) { Text("CHOOSE FOLDER & SAVE ZIP") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0B10))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("PACKAGE CONTENTS", style = MaterialTheme.typography.titleMedium)
                Text("dataset_manifest.json")
                Text("observations.json")
                Text("observations.csv")
                Text("horizon_session.sqlite")
                Text("session_events.json")
                Text("session_events.csv")
                Text("session_log.txt")
                Text("capabilities.json")
                Text("permissions.json")
                Text("integrity_audit.json")
                Text("checksums.sha256")
                Text("Choose a folder, then HORIZON writes the verified ZIP there. Package data comes from persisted Room data and HORIZON session logs; Android logcat is not silently represented as application data.", style = MaterialTheme.typography.labelSmall)
            }
        }
        exportPath?.let { path ->
            Text("Local package: $path", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DiagnosticsScreen(session: SessionEntity?, capabilityJson: String, analysis: AnalysisSnapshot) {
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { MetricCard("Diagnostics", session?.lifecycleState ?: "NONE", "Sequence gaps ${analysis.sequenceGapCount}; timestamp violations ${analysis.timestampMonotonicViolations}") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0B10))) {
                Text(capabilityJson, Modifier.padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ObservationRow(item: ObservationEntity) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17121D))) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("#${item.sequenceNumber}  ${item.type}", fontWeight = FontWeight.SemiBold)
            Text("${item.technology} / ${item.provider}", style = MaterialTheme.typography.labelSmall)
            Text("${item.capabilityState} / ${item.evidenceStatus} / ${item.timestampDomain}", style = MaterialTheme.typography.labelSmall)
            Text("sourceT=${item.monotonicTimestampNs ?: "UNAVAILABLE"}  ingressT=${item.ingestionMonotonicTimestampNs ?: "UNAVAILABLE"}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun MiniLineChart(samples: List<Double>) {
    Box(
        Modifier.fillMaxWidth().height(104.dp).background(Color(0xFF0D0B10)),
        contentAlignment = Alignment.Center
    ) {
        when {
            samples.isEmpty() -> {
                Text("NO HISTORY DATA", style = MaterialTheme.typography.labelSmall)
            }
            else -> {
                Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                    val min = samples.minOrNull() ?: return@Canvas
                    val max = samples.maxOrNull() ?: return@Canvas
                    val range = (max - min).takeIf { it > 0.0001 } ?: 1.0
                    val lastIndex = maxOf(1, samples.lastIndex)
                    val path = Path()

                    samples.forEachIndexed { index, v ->
                        val x = size.width * index / lastIndex.toFloat()
                        val y = size.height * (1f - ((v - min) / range).toFloat())
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    // A single measured sample is still rendered as evidence.
                    if (samples.size == 1) {
                        val y = size.height * 0.5f
                        drawCircle(
                            color = Color(0xFFE1A4FF),
                            radius = 5.5f,
                            center = Offset(size.width * 0.5f, y)
                        )
                    } else {
                        drawPath(path, color = Color(0xFFE1A4FF), style = Stroke(width = 3f))
                    }
                }
            }
        }
    }
}

private fun formatUtc(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

@Composable
private fun HorizonTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFFC875FF),
        secondary = Color(0xFF9C4D83),
        background = Color(0xFF0D0B10),
        surface = Color(0xFF151218)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun <T> kotlinx.coroutines.flow.Flow<T>.collectAsStateCompat(initial: T): androidx.compose.runtime.State<T> {
    return produceState(initialValue = initial, key1 = this) {
        collectLatest { value = it }
    }
}
