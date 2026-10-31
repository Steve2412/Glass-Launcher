package com.glass.launcher.ui

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glass.launcher.LauncherState
import kotlinx.coroutines.delay

@Composable
fun MusicWidget(state: LauncherState, onLongPress: () -> Unit) {
    val track by state.media.track.collectAsState()
    val art by state.media.art.collectAsState()
    val access by state.media.hasAccess.collectAsState()
    val phone by state.phone.state.collectAsState()

    val shape = RoundedCornerShape(38.dp)
    Box(
        Modifier
            .fillMaxSize()
            .glass(shape)
            .clip(shape)
            .longPressOnly(onLongPress)
            .padding(20.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            // Carátula
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = GlassColors.shadow, spotColor = GlassColors.shadow)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Crossfade(targetState = art, label = "art") { img ->
                    if (img != null) {
                        Image(img, contentDescription = "Carátula", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        ArtPlaceholder()
                    }
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val t = track
                if (!access) {
                    Txt("Música del teléfono", 22.sp, FontWeight.Bold)
                    Txt("Activa el acceso a notificaciones para ver la canción y la carátula.", 14.sp, color = GlassColors.textDim, maxLines = 3)
                    PillButton("Activar") { state.media.openAccessSettings() }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(9.dp).background(sourceColor(t?.sourcePkg), CircleShape))
                        Txt(
                            if (t == null) "Sin reproducción" else state.media.sourceLabel(t.sourcePkg, phone.name),
                            14.sp, FontWeight.SemiBold,
                        )
                    }
                    Column {
                        Txt(t?.title ?: "Conecta tu teléfono", 26.sp, FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Txt(t?.artist?.ifBlank { " " } ?: "por Bluetooth y pon música", 18.sp, FontWeight.Medium, color = GlassColors.textDim)
                    }
                    Progress(state)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ControlButton(Glyphs.prev, 56.dp, 32.dp) { state.media.previous() }
                        PlayButton(t?.playing == true) { state.media.playPause() }
                        ControlButton(Glyphs.next, 56.dp, 32.dp) { state.media.next() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Progress(state: LauncherState) {
    val track by state.media.track.collectAsState()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(track?.playing) {
        while (track?.playing == true) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
        now = SystemClock.elapsedRealtime()
    }
    val t = track
    val duration = t?.durationMs ?: 0L
    val pos = if (t == null) 0L else {
        val elapsed = if (t.playing) ((now - t.positionUpdatedAt) * t.speed).toLong() else 0L
        (t.positionMs + elapsed).coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
    }
    val fraction = if (duration > 0) (pos.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt(if (duration > 0) fmt(pos) else "", 13.sp, FontWeight.Medium, color = GlassColors.textDim)
            Txt(if (duration > 0) "-" + fmt(duration - pos) else "", 13.sp, FontWeight.Medium, color = GlassColors.textDim)
        }
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

private fun sourceColor(pkg: String?): Color = when {
    pkg == null -> Color(0xFF5AA9FF)
    pkg == "com.spotify.music" -> Color(0xFF3DDC84)
    pkg.contains("youtube") -> Color(0xFFFF4D4D)
    pkg.contains("bluetooth", true) -> Color(0xFF5AA9FF)
    else -> Color.White
}

@Composable
private fun ArtPlaceholder() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(listOf(Color(0xFFFF5F8A), Color(0xFF8A3CC7), Color(0xFF4B2BD6)))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(Glyphs.music, 64.dp, Color.White.copy(alpha = 0.85f))
    }
}

@Composable
fun ControlButton(glyph: androidx.compose.ui.graphics.vector.ImageVector, size: Dp, glyphSize: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Box(
        Modifier
            .size(size)
            .scaled(scale)
            .clip(CircleShape)
            .combinedClickableSimple(interaction, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(glyph, glyphSize)
    }
}

@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Box(
        Modifier
            .size(68.dp)
            .scaled(scale)
            .shadow(10.dp, CircleShape, ambientColor = GlassColors.shadow, spotColor = GlassColors.shadow)
            .background(Color.White, CircleShape)
            .clip(CircleShape)
            .combinedClickableSimple(interaction, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(if (playing) Glyphs.pause else Glyphs.play, 28.dp, Color(0xFF1B1838))
    }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Box(
        Modifier
            .scaled(scale)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .combinedClickableSimple(interaction, onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp)
    ) {
        Txt(text, 16.sp, FontWeight.SemiBold, color = Color(0xFF1B1838))
    }
}
