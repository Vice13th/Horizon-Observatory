package horizon.observatory.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private fun DrawScope.drawPanelOutline(outline: Outline, brush: Brush) {
    val path = when (outline) {
        is Outline.Generic -> outline.path
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
    }
    drawPath(path, brush)
}

/**
 * Presentation-only 3D panel (drop-in replacement for Material3 [Card]).
 *
 * Depth cues: perspective tilt (rotationX/Y with camera distance) that follows the touch point and
 * springs back on release, an extruded edge layer, a bevel sheen and a real elevation shadow.
 * Pointer events are observed in the Initial pass and never consumed, so scrolling and child
 * controls behave exactly as before. It changes no data, only how a panel is drawn.
 */
@Composable
internal fun Card3D(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    colors: CardColors = CardDefaults.cardColors(),
    border: BorderStroke? = null,
    maxTiltDeg: Float = 7f,
    content: @Composable ColumnScope.() -> Unit
) {
    // Honour system "remove animations": no tilt when animator scale is 0.
    val context = LocalContext.current
    val tilt = remember {
        if (android.provider.Settings.Global.getFloat(
                context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) == 0f
        ) 0f else maxTiltDeg
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var targetX by remember { mutableFloatStateOf(0f) }
    var targetY by remember { mutableFloatStateOf(0f) }
    val spec = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)
    val rotX by animateFloatAsState(targetX, spec, label = "panelRotX")
    val rotY by animateFloatAsState(targetY, spec, label = "panelRotY")

    Card(
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val p = event.changes.firstOrNull() ?: break
                        if (size.width > 0 && size.height > 0) {
                            val nx = ((p.position.x / size.width) - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
                            val ny = ((p.position.y / size.height) - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
                            targetY = nx * tilt
                            targetX = -ny * tilt
                        }
                    } while (event.changes.any { it.pressed })
                    targetX = 0f
                    targetY = 0f
                }
            }
            .graphicsLayer {
                rotationX = rotX
                rotationY = rotY
                cameraDistance = 14f * density
            }
            .drawBehind {
                val outline = shape.createOutline(this.size, layoutDirection, this)
                translate(top = 4.dp.toPx()) { drawPanelOutline(outline, SolidColor(Color.Black.copy(alpha = 0.55f))) }
            }
            .drawWithContent {
                drawContent()
                val outline = shape.createOutline(this.size, layoutDirection, this)
                drawPanelOutline(
                    outline,
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Black.copy(alpha = 0.18f))
                    )
                )
            },
        shape = shape,
        colors = colors,
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        content = content
    )
}
