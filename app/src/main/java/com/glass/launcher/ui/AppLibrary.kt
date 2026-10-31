package com.glass.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glass.launcher.LauncherState
import com.glass.launcher.PickPurpose
import com.glass.launcher.Sheet
import com.glass.launcher.data.AppEntry

/** Fondo desenfocado + tinte oscuro, para pantallas completas de cristal. */
@Composable
fun FrostedBackground(dim: Float = 0.5f, sharp: Boolean = false, state: LauncherState) {
    val imgs = state.wallpaperImages
    Box(Modifier.fillMaxSize().background(Color(0xFF0D0E22))) {
        if (imgs != null) {
            Image(
                if (sharp) imgs.full else imgs.blurred,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().background(Color(0xFF0A0A20).copy(alpha = dim)))
    }
}

@Composable
fun AppLibraryScreen(state: LauncherState, pick: PickPurpose? = null) {
    val apps by state.apps.apps.collectAsState()
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) apps else apps.filter { it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q) }
    }

    Box(Modifier.fillMaxSize().consumeTouches()) {
        FrostedBackground(0.5f, state = state)
        Column(Modifier.fillMaxSize().padding(start = 44.dp, end = 44.dp, top = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlassIconButton(Glyphs.back, 56.dp, 24.dp) { state.back() }
                Txt(if (pick == null) "Apps" else "Elige una app", 34.sp, FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                SearchField(query) { query = it }
            }
            Spacer(Modifier.height(18.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                items(filtered, key = { it.key }) { app ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AppTile(
                            state, app, 100.dp, 15.sp,
                            onClick = { onPicked(state, app, pick) },
                            onLongClick = if (pick == null) ({ state.sheet = Sheet.AppMenu(app) }) else null,
                        )
                    }
                }
            }
        }
    }
}

private fun onPicked(state: LauncherState, app: AppEntry, pick: PickPurpose?) {
    when (pick) {
        null -> state.launch(app)
        is PickPurpose.DockSlot -> state.setDockSlot(pick.slot, app)
        PickPurpose.AddToHome -> {
            state.addAppToHome(app)
            state.sheet = null
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .width(440.dp)
            .height(56.dp)
            .glass(RoundedCornerShape(28.dp), elevation = 8.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Glyph(Glyphs.search, 22.dp)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Txt("Buscar", 18.sp, FontWeight.Medium, color = GlassColors.textDim)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            ControlButton(Glyphs.close, 36.dp, 18.dp) { onChange("") }
        }
    }
}

/** Evita que los toques pasen a lo que hay debajo. */
@Composable
fun Modifier.consumeTouches(): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.combinedClickableSimple(interaction) {}
}
