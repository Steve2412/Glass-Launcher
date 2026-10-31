package com.glass.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glass.launcher.LauncherState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Muestra por dónde llegan (o no) la música y la batería en esta pantalla. */
@Composable
fun DiagnosticsScreen(state: LauncherState) {
    val sections by produceState<List<Pair<String, List<String>>>>(emptyList()) {
        while (true) {
            value = withContext(Dispatchers.IO) {
                val media = try {
                    state.media.diagnostics()
                } catch (e: Exception) {
                    listOf("Música" to listOf("Error: ${e.javaClass.simpleName}"))
                }
                val bt = try {
                    state.phone.diagnostics()
                } catch (e: Exception) {
                    listOf("Error: ${e.javaClass.simpleName}")
                }
                media.take(4) + listOf("Bluetooth de Android" to bt) + media.drop(4)
            }
            delay(2000)
        }
    }

    Box(Modifier.fillMaxSize().consumeTouches()) {
        FrostedBackground(0.6f, state = state)
        Column(Modifier.fillMaxSize().padding(start = 44.dp, end = 44.dp, top = 28.dp, bottom = 24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlassIconButton(Glyphs.back, 56.dp, 24.dp) { state.back() }
                Column {
                    Txt("Diagnóstico", 34.sp, FontWeight.Bold)
                    Txt("Pon música en el iPhone y toma una foto de esta pantalla", 15.sp, color = GlassColors.textDim)
                }
            }
            Spacer(Modifier.height(18.dp))
            val shape = RoundedCornerShape(30.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .glass(shape)
                    .clip(shape)
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                sections.forEach { (title, lines) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(title.uppercase(), 13.sp, FontWeight.Bold, color = Color(0xFF9FD3FF), letterSpacing = 1.sp)
                        lines.forEach { line ->
                            BasicText(
                                line,
                                style = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}
