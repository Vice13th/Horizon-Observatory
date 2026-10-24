package horizon.observatory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import horizon.observatory.astronomy.ObserverLocation
import horizon.observatory.astronomy.prediction.ObserverPosition
import horizon.observatory.astronomy.prediction.ObserverSource
import horizon.observatory.astronomy.prediction.PassSampleResult
import horizon.observatory.astronomy.prediction.PassSampler
import horizon.observatory.astronomy.prediction.PredictedPass
import horizon.observatory.astronomy.prediction.computedAtUtcMs
import horizon.observatory.astronomy.prediction.failureReason
import horizon.observatory.astronomy.prediction.readyValue
import horizon.observatory.live.ObservedSkyState
import horizon.observatory.live.SkyDomeStyle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PASS_AMBER = Color(0xFFF5B73B)

/** Next visibility passes (≥ mask elevation, next 12 h) for observed satellites. DERIVED_PREDICTED, geometry only. */
@Composable
internal fun NextPassesPanel(
    observed: ObservedSkyState,
    observerLat: Double?,
    observerLon: Double?,
    observerAltM: Double?
) {
    val views = LocalPredictedViews.current
    val ids = remember(observed.points) { observed.points.map { it.satelliteId }.distinct().sortedBy { it.svid } }
    val passState = views?.nextPasses?.collectAsState()?.value
    val result = passState.readyValue()
    val failure = passState.failureReason()
    val computedAt = passState.computedAtUtcMs() ?: 0L
    val haveObserver = observerLat != null && observerLon != null && observerLat.isFinite() && observerLon.isFinite()
    val latKey = observerLat?.let { Math.round(it * 100) }
    val lonKey = observerLon?.let { Math.round(it * 100) }
    LaunchedEffect(views, ids, latKey, lonKey) {
        if (views == null || !haveObserver || ids.isEmpty()) return@LaunchedEffect
        val observer = ObserverPosition(
            ObserverLocation(observerLat!!, observerLon!!, (observerAltM ?: 0.0).takeIf { it.isFinite() }?.div(1000.0) ?: 0.0),
            ObserverSource.RECEIVER_LOCATION_FIX, null
        )
        while (true) {
            views.refreshPasses(ids, observer, System.currentTimeMillis())
            delay(600_000)
        }
    }
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    fun hm(ms: Long?) = ms?.let { fmt.format(Date(it)) } ?: "—"
    fun dur(p: PredictedPass) =
        if (p.riseMs != null && p.setMs != null) "${(p.setMs - p.riseMs) / 60_000} min" else "open"
    Card3D(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF071316)),
        border = androidx.compose.foundation.BorderStroke(2.dp, PASS_AMBER.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                "NEXT PASSES · PREDICTED",
                style = MaterialTheme.typography.titleMedium, color = PASS_AMBER,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
            )
            val r = result
            Text(
                "Geometry only (no refraction/obstruction) · elevation ≥ ${r?.maskDeg?.toInt() ?: 10}° · next 12 h · local time · NOT receiver-measured",
                style = MaterialTheme.typography.labelSmall
            )
            val note = when {
                views == null -> "Prediction pipeline not available in this build."
                !haveObserver -> "No receiver position yet: cannot predict."
                ids.isEmpty() -> "No observed satellites to predict."
                failure != null -> "Prediction failed: $failure"
                r == null -> "Computing passes…"
                r.satellites.isEmpty() -> "No passes found for ${r.requested} satellite(s): no verified catalog match, engine unavailable, or none above the mask."
                else -> "${r.satellites.size}/${r.requested} satellites with passes · updated ${hm(computedAt)}"
            }
            Text(note, style = MaterialTheme.typography.labelSmall)
            val now = System.currentTimeMillis()
            r?.satellites
                ?.mapNotNull { s -> s.passes.firstOrNull()?.let { s to it } }
                ?.sortedBy { (_, p) -> p.riseMs ?: now }
                ?.take(8)
                ?.forEach { (s, p) ->
                    Text(
                        "${SkyDomeStyle.label(s.id.constellationType, s.id.svid).padEnd(4)} " +
                            "${if (p.riseMs == null) "UP NOW " else hm(p.riseMs)} → ${if (p.setMs == null) ">12h   " else hm(p.setMs)} " +
                            "max ${"%.0f".format(Locale.US, p.maxElDeg)}° ${dur(p)}" + if (s.stale) " · STALE CATALOG" else "",
                        style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace
                    )
                }
        }
    }
}
