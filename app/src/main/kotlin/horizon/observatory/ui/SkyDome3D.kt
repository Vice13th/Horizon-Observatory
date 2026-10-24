package horizon.observatory.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import horizon.observatory.live.ObservedSkyPoint
import horizon.observatory.live.SkyDomeMath
import horizon.observatory.live.SkyDomeStyle
import androidx.compose.ui.graphics.lerp
import horizon.observatory.live.SkyOrientationMode
import kotlin.math.min

/** Drag to orbit (yaw/pitch). Observed satellites only; geometry comes from correlated az/el. */
@Composable
internal fun SkyDome3D(
    points: List<ObservedSkyPoint>,
    mode: SkyOrientationMode,
    accent: Color,
    grid: Color,
    modifier: Modifier = Modifier
) {
    var yaw by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0.62f) }
    val paint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(99, 226, 222); textSize = 28f
            textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    val satPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE; textSize = 22f
            textAlign = android.graphics.Paint.Align.LEFT; isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    val weak = Color(0xFFE5484D); val mid = Color(0xFFF5B73B); val strong = accent; val unknown = Color(0xFF7C8A8C)
    fun cn0Color(f: Double?): Color = when {
        f == null -> unknown
        f < 0.5 -> lerp(weak, mid, (f * 2).toFloat())
        else -> lerp(mid, strong, ((f - 0.5) * 2).toFloat())
    }
    val heading = (mode as? SkyOrientationMode.HeadingUp)?.headingTrueDeg ?: 0.0
    Canvas(
        modifier.pointerInput(Unit) {
            detectDragGestures { change, drag ->
                change.consume()
                yaw += drag.x * 0.01f
                pitch = (pitch + drag.y * 0.01f).coerceIn(0.05f, 1.5707f)
            }
        }
    ) {
        val r = min(size.width, size.height) * 0.40f
        val c = Offset(size.width / 2f, size.height / 2f)
        fun pt(bearing: Double, el: Double): Pair<Offset, Double>? =
            SkyDomeMath.project(bearing, el, yaw.toDouble(), pitch.toDouble())?.let {
                Offset(c.x + (it.x * it.scale * r).toFloat(), c.y - (it.y * it.scale * r).toFloat()) to it.depth
            }
        fun poly(list: List<Offset>, a: Float) {
            val p = Path()
            list.forEachIndexed { i, o -> if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
            drawPath(p, grid.copy(alpha = a), style = Stroke(1.5f))
        }
        for (el in doubleArrayOf(0.0, 30.0, 60.0)) {
            poly((0..72).mapNotNull { pt(it * 5.0, el)?.first }, if (el == 0.0) 1f else 0.55f)
        }
        for (az in doubleArrayOf(0.0, 90.0, 180.0, 270.0)) {
            poly((0..18).mapNotNull { pt(az - heading, it * 5.0)?.first }, 0.45f)
        }
        pt(0.0, 90.0)?.let { drawCircle(grid, 3f, it.first) }
        drawIntoCanvas { cv ->
            listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0).forEach { (t, az) ->
                pt(az - heading, -2.0)?.let { cv.nativeCanvas.drawText(t, it.first.x, it.first.y + 10f, paint) }
            }
        }
        points.mapNotNull { p ->
            val q = SkyDomeMath.project(p.azimuthDeg - heading, p.elevationDeg, yaw.toDouble(), pitch.toDouble())
            q?.let { Triple(p, it, it) }
        }.sortedByDescending { it.second.depth }.forEach { (p, q, _) ->
            val o = Offset(c.x + (q.x * q.scale * r).toFloat(), c.y - (q.y * q.scale * r).toFloat())
            val base = if (p.usedInFix == true) 7f else 5f
            val a = (0.55 + 0.45 * (1 - q.depth) / 2).toFloat().coerceIn(0.3f, 1f)
            val col = cn0Color(SkyDomeStyle.cn0Fraction(p.cn0DbHz))
            val rad = (base * q.scale).toFloat()
            drawCircle(col.copy(alpha = a), rad, o)
            if (p.usedInFix == true) drawCircle(Color.White.copy(alpha = a), rad + 3f, o, style = Stroke(2f))
            drawIntoCanvas { cv ->
                satPaint.alpha = (a * 255).toInt()
                cv.nativeCanvas.drawText(
                    SkyDomeStyle.label(p.satelliteId.constellationType, p.satelliteId.svid),
                    o.x + rad + 6f, o.y + 7f, satPaint
                )
            }
        }
    }
}
