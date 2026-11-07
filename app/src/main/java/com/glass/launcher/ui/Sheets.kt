package com.glass.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glass.launcher.LauncherState
import com.glass.launcher.PickPurpose
import com.glass.launcher.Sheet
import com.glass.launcher.data.AppEntry
import com.glass.launcher.data.HomeType
import com.glass.launcher.icons.IconProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

class SheetAction(val label: String, val glyph: ImageVector, val destructive: Boolean = false, val onClick: () -> Unit)

@Composable
fun SheetHost(state: LauncherState) {
    val sheet = state.sheet
    // Selector de apps: pantalla completa
    AnimatedVisibility(sheet is Sheet.PickApp, enter = fadeIn(), exit = fadeOut()) {
        val p = (state.sheet as? Sheet.PickApp)?.purpose
        if (p != null) AppLibraryScreen(state, p)
    }
    AnimatedVisibility(
        sheet != null && sheet !is Sheet.PickApp,
        enter = fadeIn() + scaleIn(initialScale = 0.94f),
        exit = fadeOut() + scaleOut(targetScale = 0.96f),
    ) {
        val s = state.sheet ?: return@AnimatedVisibility
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99050414))
                .combinedClickableSimple(remember { MutableInteractionSource() }) { state.sheet = null },
            contentAlignment = Alignment.Center,
        ) {
            when (s) {
                is Sheet.ItemMenu -> ItemMenu(state, s)
                is Sheet.DockMenu -> DockMenu(state, s.slot)
                is Sheet.AppMenu -> AppMenu(state, s.app)
                Sheet.HomeMenu -> HomeMenu(state)
                Sheet.WidgetPicker -> WidgetPicker(state)
                is Sheet.IconPicker -> IconPicker(state, s.app)
                is Sheet.WidgetSize -> WidgetSizeMenu(state, s)
                is Sheet.PickApp -> Unit
            }
        }
    }
}

@Composable
private fun Panel(width: Dp, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(36.dp)
    Box(
        Modifier
            .width(width)
            .glass(shape, elevation = 24.dp, tintTop = 0.22f, darken = 0.55f)
            .clip(shape)
            .combinedClickableSimple(remember { MutableInteractionSource() }) {}
            .padding(20.dp)
    ) {
        content()
    }
}

@Composable
private fun ActionList(title: String, subtitle: String? = null, actions: List<SheetAction>, header: (@Composable () -> Unit)? = null) {
    Panel(440.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(start = 8.dp, bottom = 10.dp, top = 4.dp)) {
                header?.invoke()
                Column(Modifier.weight(1f)) {
                    Txt(title, 22.sp, FontWeight.Bold)
                    if (subtitle != null) Txt(subtitle, 14.sp, color = GlassColors.textDim)
                }
            }
            actions.forEach { a ->
                SettingRow(
                    a.label,
                    onClick = a.onClick,
                    leading = { Glyph(a.glyph, 24.dp, if (a.destructive) Color(0xFFFF8A80) else Color.White) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Menús

@Composable
private fun ItemMenu(state: LauncherState, s: Sheet.ItemMenu) {
    val item = state.home.firstOrNull { it.id == s.item.id } ?: s.item
    val apps = state.apps.apps.value
    val title = when (item.type) {
        HomeType.MUSIC -> "Widget de música"
        HomeType.PHONE -> "Widget del teléfono"
        HomeType.SETTINGS -> "Personalizar"
        HomeType.VEHICLE -> "Mi auto"
        HomeType.APP -> apps.firstOrNull { it.key == item.appKey }?.label ?: "App"
        HomeType.WIDGET -> state.widgets.info(item.widgetId)?.loadLabel(LocalContext.current.packageManager) ?: "Widget"
    }
    val actions = buildList {
        add(SheetAction("Mover a la izquierda", Glyphs.left) { state.moveItem(item, -1) })
        add(SheetAction("Mover a la derecha", Glyphs.right) { state.moveItem(item, 1) })
        if (item.type == HomeType.WIDGET) add(SheetAction("Cambiar tamaño", Glyphs.resize) { state.sheet = Sheet.WidgetSize(item) })
        if (item.type == HomeType.APP) {
            val app = apps.firstOrNull { it.key == item.appKey }
            if (app != null) add(SheetAction("Cambiar icono", Glyphs.image) { state.sheet = Sheet.IconPicker(app) })
        }
        add(SheetAction("Añadir widget", Glyphs.widget) { state.sheet = Sheet.WidgetPicker })
        add(SheetAction("Quitar del inicio", Glyphs.trash, destructive = true) { state.removeItem(item) })
    }
    ActionList(title, "Inicio", actions)
}

@Composable
private fun WidgetSizeMenu(state: LauncherState, s: Sheet.WidgetSize) {
    val sizes = listOf(1 to 1, 2 to 1, 2 to 2, 3 to 2, 4 to 1, 4 to 2, 5 to 2, 6 to 2)
    Panel(520.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Txt("Tamaño del widget", 22.sp, FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            sizes.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (c, r) ->
                        val selected = s.item.cols == c && s.item.rows == r
                        Box(
                            Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = if (selected) 0.3f else 0.1f))
                                .combinedClickableSimple(remember(c, r) { MutableInteractionSource() }) { state.resizeItem(s.item, c, r) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Txt("$c × $r", 20.sp, FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockMenu(state: LauncherState, slot: Int) {
    val app = state.apps.find(state.dock.getOrNull(slot))
    ActionList(
        app?.label ?: "Espacio vacío", "Dock",
        listOf(
            SheetAction("Cambiar app", Glyphs.swap) { state.sheet = Sheet.PickApp(PickPurpose.DockSlot(slot)) },
            SheetAction("Cambiar icono", Glyphs.image) { if (app != null) state.sheet = Sheet.IconPicker(app) },
            SheetAction("Quitar del dock", Glyphs.trash, destructive = true) { state.setDockSlot(slot, null) },
        ),
        header = { if (app != null) AppIcon(state, app, 56.dp) },
    )
}

@Composable
private fun AppMenu(state: LauncherState, app: AppEntry) {
    ActionList(
        app.label, "App",
        listOf(
            SheetAction("Añadir al inicio", Glyphs.plus) { state.addAppToHome(app); state.sheet = null },
            SheetAction("Poner en el dock", Glyphs.dock) { state.putInDock(app) },
            SheetAction("Cambiar icono", Glyphs.image) { state.sheet = Sheet.IconPicker(app) },
            SheetAction("Información de la app", Glyphs.info) { state.apps.openAppInfo(app); state.sheet = null },
            SheetAction("Desinstalar", Glyphs.trash, destructive = true) { state.apps.uninstall(app); state.sheet = null },
        ),
        header = { AppIcon(state, app, 56.dp) },
    )
}

@Composable
private fun HomeMenu(state: LauncherState) {
    ActionList(
        "Inicio", null,
        listOf(
            SheetAction("Añadir widget", Glyphs.widget) { state.sheet = Sheet.WidgetPicker },
            SheetAction("Añadir app", Glyphs.plus) { state.sheet = Sheet.PickApp(PickPurpose.AddToHome) },
            SheetAction("Fondos e iconos", Glyphs.customize) {
                state.sheet = null
                state.screen = com.glass.launcher.Screen.SETTINGS
            },
        ),
    )
}

// ---------------------------------------------------------------- Selector de widgets

private class ProviderRow(val info: AppWidgetProviderInfo, val label: String, val appLabel: String, val cols: Int, val rows: Int)

@Composable
private fun WidgetPicker(state: LauncherState) {
    val context = LocalContext.current
    val providers by produceState<List<ProviderRow>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val d = context.resources.displayMetrics.density
            state.widgets.providers().map { info ->
                val appLabel = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
                } catch (e: Exception) {
                    info.provider.packageName
                }
                val cw = state.cellWidthPx.takeIf { it > 0 } ?: (120f * d)
                val ch = state.cellHeightPx.takeIf { it > 0 } ?: (128f * d)
                ProviderRow(
                    info,
                    info.loadLabel(pm) ?: appLabel,
                    appLabel,
                    ceil(info.minWidth * d / cw).toInt().coerceIn(1, LauncherState.HOME_COLS),
                    ceil(info.minHeight * d / ch).toInt().coerceIn(1, LauncherState.HOME_ROWS),
                )
            }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
        }
    }
    val hasMusic = state.home.any { it.type == HomeType.MUSIC }
    val hasPhone = state.home.any { it.type == HomeType.PHONE }
    val hasSettings = state.home.any { it.type == HomeType.SETTINGS }
    val hasVehicle = state.home.any { it.type == HomeType.VEHICLE }

    Panel(960.dp) {
        Column(Modifier.heightIn(max = 600.dp)) {
            Txt("Añadir widget", 26.sp, FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt("DEL LAUNCHER", 13.sp, FontWeight.SemiBold, color = GlassColors.textDim, letterSpacing = 1.sp, modifier = Modifier.padding(start = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BuiltinCard("Mi auto", "4 × 2", Glyphs.car, Color(0xFF8FD3FF), Color(0xFF2A8AF0), !hasVehicle) { state.addBuiltin(HomeType.VEHICLE) }
                    BuiltinCard("Música", "4 × 2", Glyphs.music, Color(0xFFFF8FA8), Color(0xFFF0284F), !hasMusic) { state.addBuiltin(HomeType.MUSIC) }
                    BuiltinCard("Teléfono", "2 × 2", Glyphs.iphone, Color(0xFF7CF08F), Color(0xFF22B84A), !hasPhone) { state.addBuiltin(HomeType.PHONE) }
                    BuiltinCard("Personalizar", "1 × 1", Glyphs.customize, Color(0xFFA8B8FF), Color(0xFF4A5BD8), !hasSettings) { state.addBuiltin(HomeType.SETTINGS) }
                }
                Spacer(Modifier.height(8.dp))
                Txt("DE TUS APPS", 13.sp, FontWeight.SemiBold, color = GlassColors.textDim, letterSpacing = 1.sp, modifier = Modifier.padding(start = 8.dp))
                val list = providers
                when {
                    list == null -> Txt("Cargando…", 15.sp, color = GlassColors.textDim, modifier = Modifier.padding(8.dp))
                    list.isEmpty() -> Txt("Ninguna app instalada ofrece widgets.", 15.sp, color = GlassColors.textDim, modifier = Modifier.padding(8.dp))
                    else -> list.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { p ->
                                ProviderCard(p, Modifier.weight(1f)) {
                                    state.sheet = null
                                    state.addWidgetHandler?.invoke(p.info)
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuiltinCard(title: String, size: String, glyph: ImageVector, top: Color, bottom: Color, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .width(216.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.12f else 0.05f))
            .combinedClickableSimple(remember { MutableInteractionSource() }) { if (enabled) onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GlyphIcon(glyph, top, bottom, 52.dp)
        Column {
            Txt(title, 17.sp, FontWeight.SemiBold, color = if (enabled) Color.White else GlassColors.textDim)
            Txt(if (enabled) size else "Ya está en el inicio", 13.sp, color = GlassColors.textDim)
        }
    }
}

@Composable
private fun ProviderCard(p: ProviderRow, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, p.info.provider) {
        value = withContext(Dispatchers.IO) {
            try {
                IconProvider.drawableToImage(context.packageManager.getApplicationIcon(p.info.provider.packageName))
            } catch (e: Exception) {
                null
            }
        }
    }
    Row(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .combinedClickableSimple(remember { MutableInteractionSource() }, onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(52.dp)) }
        }
        Column(Modifier.weight(1f)) {
            Txt(p.label, 16.sp, FontWeight.SemiBold)
            Txt("${p.appLabel} · ${p.cols} × ${p.rows}", 13.sp, color = GlassColors.textDim)
        }
    }
}

// ---------------------------------------------------------------- Selector de icono individual

@Composable
private fun IconPicker(state: LauncherState, app: AppEntry) {
    val style = state.iconStyle
    val packPkg = if (style != "glass" && style != "original") style else null
    val names by produceState<List<String>?>(null, packPkg) {
        value = withContext(Dispatchers.IO) { packPkg?.let { state.packs.load(it)?.drawables } ?: emptyList() }
    }
    Panel(960.dp) {
        Column(Modifier.heightIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppIcon(state, app, 56.dp)
                Column(Modifier.weight(1f)) {
                    Txt("Icono de ${app.label}", 22.sp, FontWeight.Bold)
                    Txt(if (packPkg == null) "Elige primero un pack de iconos en Personalizar" else "Toca un icono del pack", 14.sp, color = GlassColors.textDim)
                }
                if (state.iconOverrides.containsKey(app.key)) {
                    PillButton("Restablecer") { state.setIconOverride(app, null, null) }
                }
            }
            val list = names
            if (packPkg != null && list != null) {
                if (list.isEmpty()) {
                    Txt("Este pack no tiene una lista de iconos.", 15.sp, color = GlassColors.textDim)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(9),
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    ) {
                        items(list) { name ->
                            PackIcon(state, packPkg, name) { state.setIconOverride(app, packPkg, name) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackIcon(state: LauncherState, pkg: String, name: String, onClick: () -> Unit) {
    val img by produceState<ImageBitmap?>(null, pkg, name) {
        value = withContext(Dispatchers.IO) {
            state.packs.load(pkg)?.load(name)?.let { IconProvider.drawableToImage(it) }
        }
    }
    Box(
        Modifier
            .size(84.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .combinedClickableSimple(remember { MutableInteractionSource() }, onClick),
        contentAlignment = Alignment.Center,
    ) {
        img?.let { Image(it, contentDescription = name, modifier = Modifier.size(68.dp)) }
            ?: Txt("…", 16.sp, align = TextAlign.Center)
    }
}
