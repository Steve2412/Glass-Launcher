package com.glass.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Fondo desenfocado de la pantalla completa, para simular el cristal. */
class Backdrop(val image: ImageBitmap, val rootWidth: Float, val rootHeight: Float)

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

object GlassColors {
    val text = Color.White
    val textDim = Color.White.copy(alpha = 0.82f)
    val green = Color(0xFF34C759)
    val shadow = Color(0xFF060418)
}

/**
 * Efecto "Liquid Glass": dibuja detrás del elemento la parte correspondiente del fondo
 * desenfocado, un tinte, un brillo superior y un borde de luz.
 */
fun Modifier.glass(
    shape: Shape,
    elevation: Dp = 14.dp,
    tintTop: Float = 0.20f,
    darken: Float = 0.30f,
): Modifier = composed {
    val backdrop = LocalBackdrop.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    this
        .shadow(elevation, shape, clip = false, ambientColor = GlassColors.shadow, spotColor = GlassColors.shadow)
        .onGloballyPositioned { pos = it.positionInRoot() }
        .drawWithCache {
            val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            val tint = Brush.linearGradient(
                0f to Color.White.copy(alpha = tintTop),
                0.45f to Color.White.copy(alpha = tintTop * 0.35f),
                1f to Color(0xFF1E1646).copy(alpha = darken),
                start = Offset.Zero,
                end = Offset(size.width * 0.55f, size.height),
            )
            val rim = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.55f),
                0.5f to Color.White.copy(alpha = 0.16f),
                1f to Color.White.copy(alpha = 0.22f),
            )
            val rimWidth = 1.2.dp.toPx()
            onDrawBehind {
                clipPath(path) {
                    val b = backdrop
                    if (b != null && b.rootWidth > 0f && b.rootHeight > 0f) {
                        val sx = b.image.width / b.rootWidth
                        val sy = b.image.height / b.rootHeight
                        val srcX = (pos.x * sx).roundToInt().coerceIn(0, b.image.width - 1)
                        val srcY = (pos.y * sy).roundToInt().coerceIn(0, b.image.height - 1)
                        val srcW = (size.width * sx).roundToInt().coerceIn(1, b.image.width - srcX)
                        val srcH = (size.height * sy).roundToInt().coerceIn(1, b.image.height - srcY)
                        drawImage(
                            image = b.image,
                            srcOffset = IntOffset(srcX, srcY),
                            srcSize = IntSize(srcW, srcH),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            filterQuality = FilterQuality.Low,
                        )
                    } else {
                        drawRect(Color(0xFF1A1D48).copy(alpha = 0.6f))
                    }
                    drawRect(tint)
                }
                drawPath(path, rim, style = Stroke(rimWidth))
            }
        }
}

/** Escala ligeramente el elemento mientras se presiona. */
@Composable
fun rememberPressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "press")
    return scale
}

fun Modifier.scaled(scale: Float): Modifier = graphicsLayer {
    scaleX = scale
    scaleY = scale
}
