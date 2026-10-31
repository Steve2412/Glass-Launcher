package com.glass.launcher.ui

import android.text.format.DateFormat
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.glass.launcher.LauncherState
import com.glass.launcher.Sheet
import com.glass.launcher.data.HomeItem
import com.glass.launcher.data.HomeType
import com.glass.launcher.layoutHome
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pulsación larga sin interferir con los toques de los hijos. */
fun Modifier.longPressOnly(onLongPress: () -> Unit): Modifier = composed {
    val cb by rememberUpdatedState(onLongPress)
    Modifier.pointerInput(Unit) {
        detectTapGestures(onLongPress = { cb() })
    }
}

@Composable
fun HomeScreen(state: LauncherState) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 36.dp, end = 36.dp, top = 28.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        TopRow(state)
        HomeBand(state)
        Dock(state)
    }
}

// ---------------------------------------------------------------- Reloj y estado

@Composable
private fun TopRow(state: LauncherState) {
    val context = LocalContext.current
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            val ms = System.currentTimeMillis()
            delay(60_000L - ms % 60_000L + 50L)
        }
    }
    val is24 = DateFormat.is24HourFormat(context)
    val es = Locale("es", "ES")
    val time = SimpleDateFormat(if (is24) "H:mm" else "h:mm", es).format(now)
    val date = SimpleDateFormat("EEEE, d 'de' MMMM", es).format(now).replaceFirstChar { it.uppercase() }

    val weather by state.weather.weather.collectAsState()
    val phone by state.phone.state.collectAsState()
    val net by state.net.state.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            state.weather.refresh()
            delay(15 * 60_000L)
        }
    }

    Row(
        Modifier.fillMaxWidth().longPressOnly { state.sheet = Sheet.HomeMenu },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(time, 116.sp, FontWeight.SemiBold, letterSpacing = (-5).sp, shadow = true, lineHeight = 112.sp)
            Txt(date, 23.sp, FontWeight.Medium, shadow = true, modifier = Modifier.padding(start = 6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val w = weather
            if (w != null) {
                Row(
                    Modifier
                        .height(54.dp)
                        .glass(RoundedCornerShape(27.dp), elevation = 8.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val (glyph, tint) = weatherGlyph(w.code, w.isDay)
                    Glyph(glyph, 24.dp, tint)
                    Txt("${w.tempC}°", 19.sp, FontWeight.SemiBold)
                }
            }
            Row(
                Modifier
                    .height(54.dp)
                    .glass(RoundedCornerShape(27.dp), elevation = 8.dp)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Glyph(Glyphs.bluetooth, 22.dp, if (phone.connected) Color.White else Color.White.copy(alpha = 0.4f))
                Glyph(if (net.connected) Glyphs.wifi else Glyphs.wifiOff, 23.dp, if (net.connected) Color.White else Color.White.copy(alpha = 0.5f))
            }
        }
    }
}

private fun weatherGlyph(code: Int, day: Boolean) = when (code) {
    0, 1 -> if (day) Glyphs.sun to Color(0xFFFFD66B) else Glyphs.moon to Color(0xFFE6E9FF)
    2, 3 -> Glyphs.cloud to Color.White
    45, 48 -> Glyphs.fog to Color.White
    in 51..67, in 80..82 -> Glyphs.rain to Color(0xFF9FD3FF)
    in 71..77, 85, 86 -> Glyphs.snow to Color.White
    in 95..99 -> Glyphs.storm to Color(0xFFFFE08A)
    else -> Glyphs.cloud to Color.White
}

// ---------------------------------------------------------------- Banda de widgets y apps

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeBand(state: LauncherState) {
    val items = state.home.toList()
    val (placements, pages) = remember(items) { layoutHome(items) }
    val pagerState = rememberPagerState { pages }
    LaunchedEffect(state.goHomeSignal) {
        if (state.goHomeSignal > 0) pagerState.animateScrollToPage(0)
    }
    val showDots = pages > 1

    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(256.dp)) {
            val cellW = maxWidth / LauncherState.HOME_COLS
            val cellH = maxHeight / LauncherState.HOME_ROWS
            val density = LocalDensity.current
            state.cellWidthPx = with(density) { cellW.toPx() }
            state.cellHeightPx = with(density) { cellH.toPx() }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .longPressOnly { state.sheet = Sheet.HomeMenu }
                ) {
                    placements.filter { it.page == page }.forEach { p ->
                        Box(
                            Modifier
                                .offset(x = cellW * p.col, y = cellH * p.row)
                                .size(cellW * p.item.cols, cellH * p.item.rows)
                                .padding(9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            HomeItemView(state, p.item)
                        }
                    }
                }
            }
        }
        if (showDots) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
                repeat(pages) { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(8.dp)
                            .background(Color.White.copy(alpha = if (pagerState.currentPage == i) 1f else 0.4f), CircleShape)
                    )
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HomeItemView(state: LauncherState, item: HomeItem) {
    val menu = { state.sheet = Sheet.ItemMenu(item) }
    when (item.type) {
        HomeType.MUSIC -> MusicWidget(state, onLongPress = menu)
        HomeType.PHONE -> PhoneWidget(state, onLongPress = menu)
        HomeType.SETTINGS -> {
            val interaction = remember { MutableInteractionSource() }
            val scale = rememberPressScale(interaction)
            Column(
                Modifier
                    .scaled(scale)
                    .combinedClickableLong(interaction, onClick = { state.screen = com.glass.launcher.Screen.SETTINGS }, onLongClick = menu),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlyphIcon(Glyphs.customize, Color(0xFFA8B8FF), Color(0xFF4A5BD8), 82.dp)
                Txt("Personalizar", 14.sp, FontWeight.Medium, shadow = true)
            }
        }
        HomeType.APP -> {
            val apps by state.apps.apps.collectAsState()
            val app = remember(apps, item.appKey) { apps.firstOrNull { it.key == item.appKey } }
            if (app != null) {
                AppTile(state, app, 82.dp, onClick = { state.launch(app) }, onLongClick = menu)
            } else if (apps.isNotEmpty()) {
                // La app fue desinstalada
                LaunchedEffect(item.id) { state.removeItem(item) }
            }
        }
        HomeType.WIDGET -> HostedWidget(state, item, menu)
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableLong(interaction: MutableInteractionSource, onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.then(
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    )

/** Widget de Android de otra app, incrustado en el inicio. */
@Composable
private fun HostedWidget(state: LauncherState, item: HomeItem, onLongPress: () -> Unit) {
    val longPress by rememberUpdatedState(onLongPress)
    val shape = RoundedCornerShape(30.dp)
    BoxWithConstraints(Modifier.fillMaxSize().clip(shape)) {
        val wDp = maxWidth
        val hDp = maxHeight
        val sysDensity = LocalContext.current.resources.displayMetrics.density
        val density = LocalDensity.current
        val widthSys = (with(density) { wDp.toPx() } / sysDensity).toInt()
        val heightSys = (with(density) { hDp.toPx() } / sysDensity).toInt()
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val view = state.widgets.createView(ctx, item.widgetId)
                if (view == null) {
                    FrameLayout(ctx)
                } else {
                    view.onLongPress = { longPress() }
                    view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    view
                }
            },
            update = { v ->
                if (v is com.glass.launcher.widgets.LongPressWidgetView) {
                    v.onLongPress = { longPress() }
                    state.widgets.updateSize(v, widthSys, heightSys)
                }
            },
        )
    }
}

// ---------------------------------------------------------------- Widget del teléfono

@Composable
fun PhoneWidget(state: LauncherState, onLongPress: () -> Unit) {
    val phone by state.phone.state.collectAsState()
    val shape = RoundedCornerShape(38.dp)
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .glass(shape)
            .clip(shape)
            .combinedClickableLong(interaction, onClick = { state.launchKey(state.dock.getOrNull(0)) }, onLongClick = onLongPress)
            .padding(22.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                val level = phone.battery
                Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(78.dp)) {
                        val sw = 7.dp.toPx()
                        val inset = sw / 2
                        drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), Size(size.width - sw, size.height - sw), style = Stroke(sw))
                        if (level != null) {
                            val c = if (level <= 20) Color(0xFFFF6B5E) else GlassColors.green
                            drawArc(c, -90f, 360f * level / 100f, false, Offset(inset, inset), Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
                        }
                    }
                    Box(Modifier.size(60.dp).background(Color(0xC7141232), CircleShape), contentAlignment = Alignment.Center) {
                        Glyph(Glyphs.iphone, 27.dp)
                    }
                }
                Glyph(Glyphs.bluetooth, 22.dp, Color.White.copy(alpha = if (phone.connected) 0.85f else 0.4f))
            }
            Column {
                Txt(if (phone.connected) (phone.battery?.let { "$it%" } ?: "—") else "—", 38.sp, FontWeight.Bold, letterSpacing = (-1).sp)
                Txt(phone.name ?: "Teléfono", 17.sp, FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Txt(if (phone.connected) "Conectado" else "Sin conexión", 14.sp, FontWeight.Medium, color = GlassColors.textDim)
            }
        }
    }
}
