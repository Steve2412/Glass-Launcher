package com.glass.launcher.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glass.launcher.LauncherState
import com.glass.launcher.data.Wallpapers
import com.glass.launcher.icons.IconPackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(state: LauncherState) {
    Box(Modifier.fillMaxSize().consumeTouches()) {
        FrostedBackground(0.45f, sharp = true, state = state)
        Column(Modifier.fillMaxSize().padding(start = 44.dp, end = 44.dp, top = 28.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlassIconButton(Glyphs.back, 56.dp, 24.dp) { state.back() }
                Txt("Personalizar", 34.sp, FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                WallpaperPanel(state, Modifier.weight(1f).fillMaxHeight())
                OptionsPanel(state, Modifier.width(460.dp).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Txt(text.uppercase(), 13.sp, FontWeight.SemiBold, color = GlassColors.textDim, letterSpacing = 1.sp, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp, top = 6.dp))
}

// ---------------------------------------------------------------- Fondos

@Composable
private fun WallpaperPanel(state: LauncherState, modifier: Modifier) {
    val context = LocalContext.current
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) state.setPhotoWallpaper(uri)
    }
    val thumbs by produceState<Map<String, ImageBitmap>>(emptyMap()) {
        value = withContext(Dispatchers.Default) {
            Wallpapers.all.associate { it.id to Wallpapers.renderSpec(it, 320, 180).asImageBitmap() }
        }
    }
    val photoThumb by produceState<ImageBitmap?>(null, state.wallpaperVersion) {
        value = withContext(Dispatchers.IO) {
            val f = Wallpapers.photoFile(context)
            if (!f.exists()) null else try {
                BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    val shape = RoundedCornerShape(36.dp)
    Column(
        modifier
            .glass(shape)
            .clip(shape)
            .verticalScroll(rememberScrollState())
            .padding(26.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Fondo de pantalla")
            val current = if (state.wallpaperId == Wallpapers.PHOTO) "Tu foto" else Wallpapers.spec(state.wallpaperId).name
            Txt(current, 15.sp, FontWeight.SemiBold)
        }
        val tiles: List<Pair<String, String>> = Wallpapers.all.map { it.id to it.name } + (Wallpapers.PHOTO to "Tu foto")
        tiles.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { (id, name) ->
                    val selected = state.wallpaperId == id
                    val interaction = remember(id) { MutableInteractionSource() }
                    val scale = rememberPressScale(interaction)
                    Column(
                        Modifier
                            .weight(1f)
                            .scaled(scale)
                            .combinedClickableSimple(interaction) {
                                if (id == Wallpapers.PHOTO) pickPhoto.launch("image/*") else state.setWallpaper(id)
                            },
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val tileShape = RoundedCornerShape(20.dp)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .shadow(if (selected) 10.dp else 6.dp, tileShape, ambientColor = GlassColors.shadow, spotColor = GlassColors.shadow)
                                .clip(tileShape)
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(if (selected) 3.dp else 1.dp, Color.White.copy(alpha = if (selected) 1f else 0.25f), tileShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            val img = if (id == Wallpapers.PHOTO) photoThumb else thumbs[id]
                            if (img != null) {
                                Image(img, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            if (id == Wallpapers.PHOTO) {
                                Box(Modifier.size(54.dp).background(Color(0x66000000), RoundedCornerShape(27.dp)), contentAlignment = Alignment.Center) {
                                    Glyph(Glyphs.photo, 30.dp)
                                }
                            }
                        }
                        Txt(name, 15.sp, FontWeight.SemiBold, align = TextAlign.Center)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------------------------------------------------------- Iconos, música y sistema

@Composable
private fun OptionsPanel(state: LauncherState, modifier: Modifier) {
    val packs by produceState<List<IconPackInfo>>(emptyList(), state.icons.version) {
        value = withContext(Dispatchers.IO) { state.packs.installedPacks() }
    }
    val access by state.media.hasAccess.collectAsState()
    val shape = RoundedCornerShape(36.dp)
    Column(
        modifier
            .glass(shape)
            .clip(shape)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ProfileSection(state)

        Spacer(Modifier.height(10.dp))
        SectionTitle("Barra del sistema (atrás, inicio, multitarea)")
        listOf(
            Triple("transparent", "Transparente", "Los botones quedan sobre el fondo, sin franja negra"),
            Triple("hidden", "Oculta (pantalla completa)", "Desliza desde el borde superior para verla un momento"),
            Triple("normal", "Normal", "Franja negra como el launcher original"),
        ).forEach { (id, title, sub) ->
            SettingRow(
                title, sub,
                selected = state.barMode == id,
                onClick = { state.chooseBarMode(id) },
                trailing = { RadioDot(state.barMode == id) },
            )
        }

        Spacer(Modifier.height(10.dp))
        SectionTitle("Paquete de iconos")
        SettingRow(
            "Glass (integrado)", "Estilo iOS con brillo y cristal",
            selected = state.iconStyle == "glass",
            onClick = { state.chooseIconStyle("glass") },
            leading = { GlyphIcon(Glyphs.music, Color(0xFFFF8FA8), Color(0xFFF0284F), 48.dp) },
            trailing = { RadioDot(state.iconStyle == "glass") },
        )
        SettingRow(
            "Iconos originales", "El icono de cada app, sin cambios",
            selected = state.iconStyle == "original",
            onClick = { state.chooseIconStyle("original") },
            leading = { GlyphIcon(Glyphs.music, Color(0xFF3C4458), Color(0xFF1F2433), 48.dp) },
            trailing = { RadioDot(state.iconStyle == "original") },
        )
        packs.forEach { p ->
            SettingRow(
                p.label, "Pack de iconos instalado",
                selected = state.iconStyle == p.pkg,
                onClick = { state.chooseIconStyle(p.pkg) },
                leading = { GlyphIcon(Glyphs.image, Color(0xFFFFD36B), Color(0xFFFF8A3D), 48.dp) },
                trailing = { RadioDot(state.iconStyle == p.pkg) },
            )
        }
        if (packs.isEmpty()) {
            Txt(
                "Instala packs compatibles con Nova, ADW o Apex y aparecerán aquí.",
                13.sp, color = GlassColors.textDim, maxLines = 3,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }
        SettingRow(
            "Marco Glass en packs", "Da brillo y forma iOS a los iconos de terceros",
            onClick = { state.togglePackFrame() },
            trailing = { GlassSwitch(state.packFrame) },
        )
        SettingRow("Buscar packs en Play Store", onClick = { state.searchIconPacks() }, leading = { Glyph(Glyphs.search, 22.dp) })
        Txt(
            "Para cambiar el icono de una sola app: mantenla pulsada en la lista de apps › Cambiar icono.",
            13.sp, color = GlassColors.textDim, maxLines = 3,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )

        Spacer(Modifier.height(10.dp))
        SectionTitle("Música y carátulas")
        SettingRow(
            "Acceso a la música",
            if (access) "Activado: el launcher ve lo que suena" else "Necesario para ver canción y carátula",
            onClick = { state.media.openAccessSettings() },
            trailing = { if (access) Glyph(Glyphs.check, 20.dp, GlassColors.green) else PillButton("Activar") { state.media.openAccessSettings() } },
        )
        SettingRow(
            "Descargar carátulas de internet",
            "Si el teléfono no envía la portada, se busca por título y artista",
            onClick = { state.toggleArtworkOnline() },
            trailing = { GlassSwitch(state.artworkOnline) },
        )
        SettingRow(
            "Diagnóstico de música y Bluetooth",
            "Muestra por dónde llegan la canción y la batería",
            onClick = { state.screen = com.glass.launcher.Screen.DIAG },
            leading = { Glyph(Glyphs.info, 22.dp) },
        )
        if (state.artworkOnline) {
            SettingRow(
                "Solo con WiFi",
                "No descarga carátulas con datos móviles",
                onClick = { state.toggleArtworkWifiOnly() },
                trailing = { GlassSwitch(state.artworkWifiOnly) },
            )
        }

        Spacer(Modifier.height(10.dp))
        SectionTitle("Inicio y sistema")
        SettingRow("Añadir widget", "Widgets del launcher o de tus apps", onClick = { state.sheet = com.glass.launcher.Sheet.WidgetPicker }, leading = { Glyph(Glyphs.widget, 22.dp) })
        SettingRow("Añadir app al inicio", onClick = { state.sheet = com.glass.launcher.Sheet.PickApp(com.glass.launcher.PickPurpose.AddToHome) }, leading = { Glyph(Glyphs.plus, 22.dp) })
        SettingRow("Launcher predeterminado", "Elegir Glass Launcher como pantalla de inicio", onClick = { state.openDefaultLauncherSettings() }, leading = { Glyph(Glyphs.home, 22.dp) })
    }
}

// ---------------------------------------------------------------- Perfil

@Composable
private fun ProfileSection(state: LauncherState) {
    val context = LocalContext.current
    val pickVehicle = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) state.setVehiclePhoto(uri)
    }
    val photo by produceState<ImageBitmap?>(null, state.vehicleVersion) {
        value = withContext(Dispatchers.IO) { com.glass.launcher.data.Vehicle.load(context)?.asImageBitmap() }
    }
    SectionTitle("Perfil")
    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassTextField("Tu nombre", state.userName) { state.changeUserName(it) }
        Txt(
            "${com.glass.launcher.data.Vehicle.greeting()}, ${state.userName.ifBlank { "Conductor" }} · el saludo cambia solo con la hora",
            13.sp, color = GlassColors.textDim, maxLines = 2,
        )
        Txt("Foto del vehículo", 15.sp, FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(200.dp)
                    .height(120.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                photo?.let { Image(it, contentDescription = "Vehículo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Cambiar foto") { pickVehicle.launch("image/*") }
                PillButton("Usar Aveo 2007") { state.resetVehiclePhoto() }
            }
        }
        Txt("Mejor una imagen PNG sin fondo, con el auto mirando a la izquierda.", 13.sp, color = GlassColors.textDim, maxLines = 2)
        GlassTextField("Nombre del vehículo", state.vehicleName) { state.changeVehicleName(it) }
    }
}

@Composable
private fun GlassTextField(label: String, value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt(label, 15.sp, FontWeight.SemiBold)
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = { onChange(it.take(30)) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}
