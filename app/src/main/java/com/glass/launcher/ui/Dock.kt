package com.glass.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.glass.launcher.LauncherState
import com.glass.launcher.PickPurpose
import com.glass.launcher.Screen
import com.glass.launcher.Sheet
import com.glass.launcher.icons.SquircleShape

private val ICON = 96.dp

@Composable
fun Dock(state: LauncherState) {
    val apps by state.apps.apps.collectAsState()
    val shape = RoundedCornerShape(46.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(130.dp)
            .glass(shape, elevation = 18.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Botón de la lista de apps (como el de CarPlay)
        val libInteraction = remember { MutableInteractionSource() }
        val libScale = rememberPressScale(libInteraction)
        Box(
            Modifier
                .size(ICON)
                .scaled(libScale)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.18f))
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                .combinedClickableSimple(libInteraction) { state.screen = Screen.LIBRARY },
            contentAlignment = Alignment.Center,
        ) {
            Glyph(Glyphs.grid, 42.dp)
        }

        Box(Modifier.width(1.dp).height(68.dp).background(Color.White.copy(alpha = 0.3f)))

        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            state.dock.forEachIndexed { slot, key ->
                val app = apps.firstOrNull { it.key == key }
                if (app != null) {
                    val interaction = remember(slot) { MutableInteractionSource() }
                    val scale = rememberPressScale(interaction)
                    AppIcon(
                        state, app, ICON,
                        Modifier
                            .scaled(scale)
                            .combinedClickableLong(
                                interaction,
                                onClick = { state.launch(app) },
                                onLongClick = { state.sheet = Sheet.DockMenu(slot) },
                            ),
                    )
                } else {
                    val interaction = remember(slot) { MutableInteractionSource() }
                    Box(
                        Modifier
                            .size(ICON)
                            .clip(SquircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.5.dp, Color.White.copy(alpha = 0.3f), SquircleShape)
                            .combinedClickableSimple(interaction) { state.sheet = Sheet.PickApp(PickPurpose.DockSlot(slot)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Glyph(Glyphs.plus, 34.dp, Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }

        // Volumen
        Row(
            Modifier
                .height(78.dp)
                .clip(RoundedCornerShape(39.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(39.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ControlButton(Glyphs.minus, 58.dp, 28.dp) { state.volume(false) }
            Glyph(Glyphs.volume, 28.dp)
            ControlButton(Glyphs.plus, 58.dp, 28.dp) { state.volume(true) }
        }
    }
}
