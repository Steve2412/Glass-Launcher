package com.glass.launcher

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.glass.launcher.data.AppEntry
import com.glass.launcher.data.AppRepository
import com.glass.launcher.data.HomeItem
import com.glass.launcher.data.HomeType
import com.glass.launcher.data.NetworkMonitor
import com.glass.launcher.data.PhoneRepository
import com.glass.launcher.data.Prefs
import com.glass.launcher.data.WallpaperImages
import com.glass.launcher.data.Vehicle
import com.glass.launcher.data.Wallpapers
import com.glass.launcher.data.WeatherRepository
import com.glass.launcher.icons.IconPackManager
import com.glass.launcher.icons.IconProvider
import com.glass.launcher.media.MediaRepository
import com.glass.launcher.widgets.WidgetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

enum class Screen { HOME, LIBRARY, SETTINGS, DIAG }

/** Ventanas emergentes (menús y selectores) que se muestran sobre el launcher. */
sealed interface Sheet {
    data class ItemMenu(val item: HomeItem) : Sheet
    data class DockMenu(val slot: Int) : Sheet
    data class AppMenu(val app: AppEntry) : Sheet
    data object HomeMenu : Sheet
    data class PickApp(val purpose: PickPurpose) : Sheet
    data object WidgetPicker : Sheet
    data class IconPicker(val app: AppEntry) : Sheet
    data class WidgetSize(val item: HomeItem) : Sheet
}

sealed interface PickPurpose {
    data class DockSlot(val slot: Int) : PickPurpose
    data object AddToHome : PickPurpose
}

class LauncherState(private val context: Context) {
    val scope = MainScope()
    val prefs = Prefs(context)
    val net = NetworkMonitor(context)
    val apps = AppRepository(context, scope)
    val packs = IconPackManager(context)
    val icons = IconProvider(apps, packs)
    val media = MediaRepository(context, prefs, net, scope)
    val phone = PhoneRepository(context)
    val weather = WeatherRepository(context)
    val widgets = WidgetManager(context)
    private val audio = context.getSystemService(AudioManager::class.java)

    // --- Navegación ---
    var screen by mutableStateOf(Screen.HOME)
    var sheet by mutableStateOf<Sheet?>(null)
    var homePage by mutableIntStateOf(0)
    var goHomeSignal by mutableIntStateOf(0)

    /** Tamaño de una celda del inicio (para calcular el tamaño de los widgets). */
    var cellWidthPx = 0f
    var cellHeightPx = 0f

    /** Lo asigna la Activity: inicia el flujo de permiso/configuración de un widget. */
    var addWidgetHandler: ((AppWidgetProviderInfo) -> Unit)? = null

    // --- Ajustes ---
    var wallpaperId by mutableStateOf(prefs.wallpaper)
        private set
    var wallpaperVersion by mutableIntStateOf(0)
        private set
    var wallpaperImages by mutableStateOf<WallpaperImages?>(null)
        private set
    var iconStyle by mutableStateOf(prefs.iconStyle)
        private set
    var packFrame by mutableStateOf(prefs.packFrame)
        private set
    var artworkOnline by mutableStateOf(prefs.artworkOnline)
        private set
    var artworkWifiOnly by mutableStateOf(prefs.artworkWifiOnly)
        private set
    var barMode by mutableStateOf(prefs.barMode)
        private set
    var userName by mutableStateOf(prefs.userName)
        private set
    var vehicleName by mutableStateOf(prefs.vehicleName)
        private set
    /** Cambia cuando el usuario pone otra foto del vehículo. */
    var vehicleVersion by mutableIntStateOf(0)
        private set
    var iconOverrides by mutableStateOf(prefs.loadIconOverrides())
        private set

    // --- Inicio y dock ---
    val home = mutableStateListOf<HomeItem>().apply { addAll(prefs.loadHome()) }
    val dock = mutableStateListOf<String>().apply { addAll(prefs.loadDock()) }

    init {
        icons.configure(iconStyle, packFrame, iconOverrides)
        apps.onChanged = { onAppsChanged() }
    }

    private fun onAppsChanged() {
        if (!prefs.initialized && apps.apps.value.isNotEmpty()) {
            createDefaults()
            prefs.initialized = true
            prefs.vehicleCardAdded = true
        }
        if (prefs.initialized && !prefs.vehicleCardAdded) {
            // Instalaciones anteriores: añadir la tarjeta "Mi auto" al principio
            if (home.none { it.type == HomeType.VEHICLE }) {
                home.add(0, newItem(HomeType.VEHICLE, cols = 4, rows = 2))
                saveHome()
            }
            prefs.vehicleCardAdded = true
        }
        icons.invalidate()
    }

    // ---------------- Valores por defecto (primer arranque) ----------------

    private fun createDefaults() {
        val used = HashSet<String>()
        fun pick(vararg words: String): String {
            val a = apps.findByKeywords(words.toList(), used) ?: return ""
            used.add(a.key)
            return a.key
        }
        val newDock = listOf(
            pick("bluetooth", "teléfono", "telefono", "phone", "dialer"),
            pick("maps", "mapas", "waze", "navi"),
            pick("bt music", "music", "música", "musica", "spotify"),
            pick("zlink", "carplay", "carlink", "autokit"),
            pick("radio", "fm"),
            pick("settings", "ajustes", "configuración", "configuracion"),
        )
        dock.clear()
        dock.addAll(newDock)
        prefs.saveDock(newDock)

        val items = mutableListOf(
            newItem(HomeType.VEHICLE, cols = 4, rows = 2),
            newItem(HomeType.MUSIC, cols = 4, rows = 2),
            newItem(HomeType.PHONE, cols = 2, rows = 2),
        )
        listOf(
            arrayOf("youtube"), arrayOf("chrome", "browser", "navegador"), arrayOf("video"),
            arrayOf("file", "archivos"), arrayOf("play store"), arrayOf("av in", "avin"), arrayOf("maps", "waze"),
        ).forEach { words ->
            val k = pick(*words)
            if (k.isNotEmpty() && items.count { it.type == HomeType.APP } < 7) items.add(newItem(HomeType.APP, appKey = k))
        }
        items.add(newItem(HomeType.SETTINGS))
        home.clear()
        home.addAll(items)
        saveHome()
    }

    private fun newItem(type: HomeType, appKey: String? = null, widgetId: Int = -1, cols: Int = 1, rows: Int = 1): HomeItem {
        val id = prefs.nextId
        prefs.nextId = id + 1
        return HomeItem(id, type, appKey, widgetId, cols, rows)
    }

    private fun saveHome() = prefs.saveHome(home.toList())

    // ---------------- Navegación ----------------

    fun goHome() {
        sheet = null
        screen = Screen.HOME
        goHomeSignal++
    }

    fun back() {
        when {
            sheet != null -> sheet = null
            screen == Screen.DIAG -> screen = Screen.SETTINGS
            screen != Screen.HOME -> screen = Screen.HOME
        }
    }

    // ---------------- Apps ----------------

    fun launch(app: AppEntry) {
        apps.launch(app)
        sheet = null
    }

    fun launchKey(key: String?) {
        apps.find(key)?.let { launch(it) }
    }

    // ---------------- Inicio ----------------

    fun addAppToHome(app: AppEntry) {
        home.add(newItem(HomeType.APP, appKey = app.key))
        saveHome()
        toast("${app.label} añadida al inicio")
    }

    fun addBuiltin(type: HomeType) {
        val item = when (type) {
            HomeType.MUSIC -> newItem(type, cols = 4, rows = 2)
            HomeType.PHONE -> newItem(type, cols = 2, rows = 2)
            HomeType.VEHICLE -> newItem(type, cols = 4, rows = 2)
            else -> newItem(type)
        }
        home.add(item)
        saveHome()
        sheet = null
    }

    fun addWidgetItem(widgetId: Int, info: AppWidgetProviderInfo?, cellWidthPx: Float, cellHeightPx: Float) {
        var cols = 2
        var rows = 2
        if (info != null && cellWidthPx > 0 && cellHeightPx > 0) {
            val d = context.resources.displayMetrics.density
            cols = ceil(info.minWidth * d / cellWidthPx).toInt().coerceIn(1, HOME_COLS)
            rows = ceil(info.minHeight * d / cellHeightPx).toInt().coerceIn(1, HOME_ROWS)
            if (cols < 2 && rows >= 2) cols = 2
        }
        home.add(newItem(HomeType.WIDGET, widgetId = widgetId, cols = cols, rows = rows))
        saveHome()
    }

    fun removeItem(item: HomeItem) {
        if (item.type == HomeType.WIDGET) widgets.delete(item.widgetId)
        home.removeAll { it.id == item.id }
        saveHome()
        sheet = null
    }

    fun moveItem(item: HomeItem, delta: Int) {
        val i = home.indexOfFirst { it.id == item.id }
        val j = i + delta
        if (i < 0 || j < 0 || j >= home.size) return
        val tmp = home[i]
        home[i] = home[j]
        home[j] = tmp
        saveHome()
    }

    fun resizeItem(item: HomeItem, cols: Int, rows: Int) {
        val i = home.indexOfFirst { it.id == item.id }
        if (i < 0) return
        home[i] = home[i].copy(cols = cols.coerceIn(1, HOME_COLS), rows = rows.coerceIn(1, HOME_ROWS))
        saveHome()
        sheet = null
    }

    // ---------------- Dock ----------------

    fun setDockSlot(slot: Int, app: AppEntry?) {
        if (slot !in dock.indices) return
        dock[slot] = app?.key ?: ""
        prefs.saveDock(dock.toList())
        sheet = null
    }

    fun putInDock(app: AppEntry) {
        val empty = dock.indexOfFirst { it.isEmpty() || apps.find(it) == null }
        if (empty >= 0) {
            setDockSlot(empty, app)
            toast("${app.label} añadida al dock")
        } else {
            sheet = null
            toast("El dock está lleno: mantén pulsada una app del dock para cambiarla")
        }
    }

    fun volume(up: Boolean) {
        try {
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI,
            )
        } catch (_: Exception) {
        }
    }

    // ---------------- Ajustes ----------------

    fun setWallpaper(id: String) {
        wallpaperId = id
        prefs.wallpaper = id
        wallpaperVersion++
    }

    fun setPhotoWallpaper(uri: Uri) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Wallpapers.savePhoto(context, uri) }
            if (ok) setWallpaper(Wallpapers.PHOTO) else toast("No se pudo usar esa imagen")
        }
    }

    suspend fun renderWallpaper(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val id = wallpaperId
        val imgs = withContext(Dispatchers.Default) { Wallpapers.render(context, id, w, h) }
        wallpaperImages = imgs
    }

    fun chooseIconStyle(style: String) {
        iconStyle = style
        prefs.iconStyle = style
        icons.configure(iconStyle, packFrame, iconOverrides)
    }

    fun togglePackFrame() {
        packFrame = !packFrame
        prefs.packFrame = packFrame
        icons.configure(iconStyle, packFrame, iconOverrides)
    }

    fun setIconOverride(app: AppEntry, packPkg: String?, drawable: String?) {
        val m = iconOverrides.toMutableMap()
        if (packPkg == null || drawable == null) m.remove(app.key) else m[app.key] = "$packPkg|$drawable"
        iconOverrides = m
        prefs.saveIconOverrides(m)
        icons.configure(iconStyle, packFrame, iconOverrides)
        sheet = null
    }

    fun toggleArtworkOnline() {
        artworkOnline = !artworkOnline
        prefs.artworkOnline = artworkOnline
        media.onArtworkSettingsChanged()
    }

    fun toggleArtworkWifiOnly() {
        artworkWifiOnly = !artworkWifiOnly
        prefs.artworkWifiOnly = artworkWifiOnly
        media.onArtworkSettingsChanged()
    }

    fun chooseBarMode(mode: String) {
        barMode = mode
        prefs.barMode = mode
    }

    fun changeUserName(name: String) {
        userName = name
        prefs.userName = name
    }

    fun changeVehicleName(name: String) {
        vehicleName = name
        prefs.vehicleName = name
    }

    fun setVehiclePhoto(uri: Uri) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Vehicle.savePhoto(context, uri) }
            if (ok) vehicleVersion++ else toast("No se pudo usar esa imagen")
        }
    }

    fun resetVehiclePhoto() {
        Vehicle.photoFile(context).delete()
        vehicleVersion++
    }

    fun openDefaultLauncherSettings() {
        try {
            context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
        }
    }

    fun searchIconPacks() {
        val intents = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=icon%20pack&c=apps")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=icon%20pack&c=apps")),
        )
        for (i in intents) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
        toast("No se encontró Play Store")
    }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val HOME_COLS = 10
        const val HOME_ROWS = 2
    }
}

/** Posición calculada de un elemento en la cuadrícula del inicio. */
data class Placement(val item: HomeItem, val page: Int, val col: Int, val row: Int)

/** Coloca los elementos en páginas de 10×2 celdas, en orden, ocupando el primer hueco libre. */
fun layoutHome(items: List<HomeItem>, cols: Int = LauncherState.HOME_COLS, rows: Int = LauncherState.HOME_ROWS): Pair<List<Placement>, Int> {
    val grids = mutableListOf<Array<BooleanArray>>()
    fun grid(p: Int): Array<BooleanArray> {
        while (grids.size <= p) grids.add(Array(rows) { BooleanArray(cols) })
        return grids[p]
    }
    val result = mutableListOf<Placement>()
    var startPage = 0
    for (item in items) {
        val w = item.cols.coerceIn(1, cols)
        val h = item.rows.coerceIn(1, rows)
        var placed = false
        var page = startPage
        while (!placed) {
            val g = grid(page)
            loop@ for (r in 0..rows - h) {
                for (c in 0..cols - w) {
                    var free = true
                    for (rr in r until r + h) for (cc in c until c + w) if (g[rr][cc]) free = false
                    if (free) {
                        for (rr in r until r + h) for (cc in c until c + w) g[rr][cc] = true
                        result.add(Placement(item, page, c, r))
                        placed = true
                        break@loop
                    }
                }
            }
            if (!placed) page++
        }
        startPage = result.last().page
    }
    return result to maxOf(1, grids.size)
}
