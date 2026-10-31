package com.glass.launcher.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.text.BasicText
import com.glass.launcher.LauncherState
import com.glass.launcher.data.AppEntry
import com.glass.launcher.icons.SquircleShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val textShadow = Shadow(Color(0x99060418), Offset(0f, 1f), 8f)

@Composable
fun Txt(
    text: String,
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Color.White,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    shadow: Boolean = false,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            textAlign = align,
            letterSpacing = letterSpacing,
            shadow = if (shadow) textShadow else null,
            lineHeight = lineHeight,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Icono de una app (Glass, original o de un pack), generado en segundo plano. */
@Composable
fun AppIcon(state: LauncherState, app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val version = state.icons.version
    val bmp by produceState<ImageBitmap?>(null, app.key, version) {
        value = withContext(Dispatchers.Default) {
            try {
                state.icons.icon(app)
            } catch (e: Exception) {
                null
            }
        }
    }
    val shadowed = state.icons.usesSquircle()
    Box(
        modifier
            .size(size)
            .then(if (shadowed) Modifier.shadow(size * 0.09f, SquircleShape, clip = false, ambientColor = GlassColors.shadow, spotColor = GlassColors.shadow) else Modifier),
    ) {
        val b = bmp
        if (b != null) {
            Image(b, contentDescription = app.label, modifier = Modifier.size(size))
        } else {
            Box(Modifier.size(size).clip(SquircleShape).background(Color.White.copy(alpha = 0.15f)))
        }
    }
}

/** Icono + nombre, con pulsación y pulsación larga. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    state: LauncherState,
    app: AppEntry,
    iconSize: Dp,
    labelSize: TextUnit = 14.sp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Column(
        modifier
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick)
            .scaled(scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppIcon(state, app, iconSize)
        Txt(app.label, labelSize, FontWeight.Medium, shadow = true, align = TextAlign.Center, modifier = Modifier.width(iconSize + 34.dp))
    }
}

/** Icono hecho con un glifo sobre fondo de color (para accesos propios del launcher). */
@Composable
fun GlyphIcon(glyph: androidx.compose.ui.graphics.vector.ImageVector, top: Color, bottom: Color, size: Dp, glyphScale: Float = 0.48f) {
    Box(
        Modifier
            .size(size)
            .shadow(size * 0.09f, SquircleShape, clip = false, ambientColor = GlassColors.shadow, spotColor = GlassColors.shadow)
            .clip(SquircleShape)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.36f),
                        0.52f to Color.Transparent,
                    )
                )
        )
        Glyph(glyph, size * glyphScale)
    }
}

/** Interruptor estilo iOS. */
@Composable
fun GlassSwitch(on: Boolean) {
    val track by animateColorAsState(if (on) GlassColors.green else Color.White.copy(alpha = 0.25f), label = "track")
    val knob by animateDpAsState(if (on) 27.dp else 3.dp, label = "knob")
    Box(
        Modifier
            .width(58.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(track)
    ) {
        Box(
            Modifier
                .offset(x = knob, y = 3.dp)
                .size(28.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

/** Fila de ajustes con título, subtítulo y algo a la derecha. */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Row(
        Modifier
            .fillMaxWidth()
            .scaled(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .combinedClickableSimple(interaction, onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Txt(title, 17.sp, FontWeight.SemiBold)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Txt(subtitle, 13.sp, color = GlassColors.textDim, maxLines = 2)
            }
        }
        trailing?.invoke()
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableSimple(interaction: MutableInteractionSource, onClick: () -> Unit): Modifier =
    this.combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)

/** Círculo de selección con check. */
@Composable
fun RadioDot(on: Boolean) {
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (on) Color.White else Color.Transparent)
            .then(
                if (on) Modifier else Modifier.background(Color.White.copy(alpha = 0.12f))
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Glyph(Glyphs.check, 15.dp, Color(0xFF1B1838))
    }
}

/** Botón redondo de cristal con un glifo. */
@Composable
fun GlassIconButton(glyph: androidx.compose.ui.graphics.vector.ImageVector, size: Dp, glyphSize: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Box(
        Modifier
            .size(size)
            .scaled(scale)
            .glass(CircleShape, elevation = 8.dp)
            .clip(CircleShape)
            .combinedClickableSimple(interaction, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(glyph, glyphSize)
    }
}
