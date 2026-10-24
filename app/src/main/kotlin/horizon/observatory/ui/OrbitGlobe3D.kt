package horizon.observatory.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import horizon.observatory.astronomy.ObserverLocation
import horizon.observatory.astronomy.prediction.GroundTrackResult
import horizon.observatory.astronomy.prediction.GroundTrackSampler
import horizon.observatory.astronomy.prediction.failureReason
import horizon.observatory.astronomy.prediction.readyValue
import horizon.observatory.astronomy.prediction.ObserverPosition
import horizon.observatory.astronomy.prediction.ObserverSource
import horizon.observatory.astronomy.prediction.SatellitePredictionUseCase
import horizon.observatory.live.GlobeMath
import horizon.observatory.live.ObservedSkyState
import horizon.observatory.live.SkyDomeStyle
import kotlinx.coroutines.delay
import kotlin.math.min

/** Provided at the screen root; null ⇒ prediction pipeline not available (panels say so). Results live in the holder, not in composition. */
internal val LocalPredictedViews = staticCompositionLocalOf<horizon.observatory.astronomy.prediction.PredictedViewsStateHolder?> { null }

private val PREDICTED = Color(0xFFF5B73B) // amber: predicted layer is always visually distinct from observed (cyan)
private val GRID = Color(0xFF2D6864)

/**
 * 3D globe of PREDICTED SGP4/SDP4 ground tracks (past = solid, future = dashed, dot = now) for
 * satellites the receiver observed. Never mixed into the observed sky evidence. Drag to rotate.
 */
@Composable
internal fun PredictedGlobePanel(
    observed: ObservedSkyState,
    observerLat: Double?,
    observerLon: Double?,
    observerAltM: Double?
) {
    val views = LocalPredictedViews.current
    val ids = remember(observed.points) { observed.points.map { it.satelliteId }.distinct().sortedBy { it.svid }.take(GroundTrackSampler.MAX_SATELLITES) }
    val trackState = views?.groundTracks?.collectAsState()?.value
    val result = trackState.readyValue()
    val failure = trackState.failureReason()
    val haveObserver = observerLat != null && observerLon != null && observerLat.isFinite() && observerLon.isFinite()
    // Re-sample once a minute; observer rounded to ~1 km so jitter does not restart the effect.
    val latKey = observerLat?.let { Math.round(it * 100) }
    val lonKey = observerLon?.let { Math.round(it * 100) }
    LaunchedEffect(views, ids, latKey, lonKey) {
        if (views == null || !haveObserver || ids.isEmpty()) return@LaunchedEffect
        val observer = ObserverPosition(
            ObserverLocation(observerLat!!, observerLon!!, (observerAltM ?: 0.0).takeIf { it.isFinite() }?.div(1000.0) ?: 0.0),
            ObserverSource.RECEIVER_LOCATION_FIX, null
        )
        while (true) {
            views.refreshGroundTracks(ids, observer, System.currentTimeMillis())
            delay(60_000)
        }
    }
    Card3D(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF071316)),
        border = androidx.compose.foundation.BorderStroke(2.dp, PREDICTED.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                "PREDICTED GROUND TRACKS",
                style = MaterialTheme.typography.titleMedium, color = PREDICTED,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
            )
            Text(
                "Derived · SGP4/SDP4 from catalog elements · NOT receiver-measured · solid = past 20 min, dashed = next 60 min",
                style = MaterialTheme.typography.labelSmall
            )
            val r = result
            val note = when {
                views == null -> "Prediction pipeline not available in this build."
                !haveObserver -> "No receiver position yet: cannot predict."
                ids.isEmpty() -> "No observed satellites to predict."
                failure != null -> "Prediction failed: $failure"
                r == null -> "Computing predictions…"
                r.tracks.isEmpty() -> "No predicted tracks: ${r.requested} observed satellite(s) without verified catalog match or engine unavailable " +
                    r.statusNow.entries.joinToString(prefix = "(", postfix = ")") { "${it.key}×${it.value}" }
                else -> "${r.tracks.size}/${r.requested} satellites predicted"
            }
            Text(note, style = MaterialTheme.typography.labelSmall)
            Box(Modifier.fillMaxWidth().height(260.dp).background(Color(0xFF071316))) {
                OrbitGlobe3D(
                    tracks = r?.tracks.orEmpty(),
                    observerLat = if (haveObserver) observerLat else null,
                    observerLon = if (haveObserver) observerLon else null,
                    modifier = Modifier.fillMaxWidth().height(260.dp)
                )
            }
        }
    }
}

@Composable
internal fun OrbitGlobe3D(
    tracks: List<horizon.observatory.astronomy.prediction.SatelliteGroundTrack>,
    observerLat: Double?,
    observerLon: Double?,
    modifier: Modifier = Modifier
) {
    var cLat by remember(observerLat) { mutableFloatStateOf((observerLat ?: 20.0).toFloat()) }
    var cLon by remember(observerLon) { mutableFloatStateOf((observerLon ?: 0.0).toFloat()) }
    val paint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(245, 183, 59); textSize = 22f
            isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    Canvas(
        modifier.pointerInput(Unit) {
            detectDragGestures { change, drag ->
                change.consume()
                cLon -= drag.x * 0.4f
                cLat = (cLat + drag.y * 0.4f).coerceIn(-85f, 85f)
            }
        }
    ) {
        val rad = min(size.width, size.height) * 0.44f
        val c = Offset(size.width / 2f, size.height / 2f)
        fun pt(lat: Double, lon: Double) =
            GlobeMath.project(lat, lon, cLat.toDouble(), cLon.toDouble())?.let {
                Offset(c.x + (it.x * rad).toFloat(), c.y - (it.y * rad).toFloat()) to it.depth
            }
        drawCircle(Color(0xFF0B2A2E), rad, c)
        drawCircle(GRID, rad, c, style = Stroke(2f))
        fun seg(a: Pair<Offset, Double>?, b: Pair<Offset, Double>?, col: Color, w: Float, dash: Boolean = false) {
            if (a == null || b == null) return
            val front = (a.second + b.second) / 2 > 0
            drawLine(
                col.copy(alpha = if (front) col.alpha else col.alpha * 0.18f), a.first, b.first, w,
                pathEffect = if (dash) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null
            )
        }
        for (lat in -60..60 step 30) {
            var prev = pt(lat.toDouble(), -180.0)
            for (lon in -170..180 step 10) { val cur = pt(lat.toDouble(), lon.toDouble()); seg(prev, cur, GRID.copy(alpha = 0.7f), 1f); prev = cur }
        }
        for (lon in -180..150 step 30) {
            var prev = pt(-90.0, lon.toDouble())
            for (lat in -80..90 step 10) { val cur = pt(lat.toDouble(), lon.toDouble()); seg(prev, cur, GRID.copy(alpha = 0.7f), 1f); prev = cur }
        }
        tracks.forEach { t ->
            val pts = t.points.sortedBy { it.offsetMin }
            for (i in 1 until pts.size) {
                val a = pt(pts[i - 1].latDeg, pts[i - 1].lonDeg); val b = pt(pts[i].latDeg, pts[i].lonDeg)
                seg(a, b, PREDICTED, 3f, dash = pts[i].offsetMin > 0)
            }
            pts.firstOrNull { it.offsetMin == 0 }?.let { now ->
                pt(now.latDeg, now.lonDeg)?.let { (o, d) ->
                    if (d > 0) {
                        drawCircle(PREDICTED, 6f, o)
                        drawIntoCanvas { cv ->
                            cv.nativeCanvas.drawText(SkyDomeStyle.label(t.id.constellationType, t.id.svid), o.x + 9f, o.y + 7f, paint)
                        }
                    }
                }
            }
        }
        if (observerLat != null && observerLon != null) {
            pt(observerLat, observerLon)?.let { (o, d) ->
                if (d > 0) { drawCircle(Color.White, 5f, o); drawCircle(Color.White, 10f, o, style = Stroke(2f)) }
            }
        }
    }
}
