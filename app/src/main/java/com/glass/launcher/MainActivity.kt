package com.glass.launcher

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.glass.launcher.ui.GlassRoot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val state: LauncherState get() = (application as GlassApp).state

    private var pendingWidgetId = -1
    private var pendingWidgetInfo: AppWidgetProviderInfo? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        state.phone.start()
        state.scope.launch { state.weather.refresh(force = true) }
    }

    private val bindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingWidgetId
        val info = pendingWidgetInfo
        if (result.resultCode == Activity.RESULT_OK && id != -1 && info != null) {
            configureOrAdd(id, info)
        } else if (id != -1) {
            state.widgets.delete(id)
            clearPending()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state.addWidgetHandler = { info -> startAddWidget(info) }
        setContent {
            LaunchedEffect(Unit) {
                snapshotFlow { state.barMode }.collect { applyBarMode(it) }
            }
            GlassRoot(state)
        }
        requestNeededPermissions()
    }

    override fun onStart() {
        super.onStart()
        state.widgets.startListening()
    }

    override fun onStop() {
        super.onStop()
        state.widgets.stopListening()
    }

    override fun onResume() {
        super.onResume()
        state.media.refreshAccess()
        state.phone.start()
        applyBarMode(state.barMode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pulsar "Inicio" estando ya en el launcher: cerrar todo y volver a la primera página
        if (intent.action == Intent.ACTION_MAIN) state.goHome()
    }

    private fun requestNeededPermissions() {
        val wanted = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) {
            permissionLauncher.launch(wanted.toTypedArray())
        } else {
            state.phone.start()
        }
    }

    /**
     * transparent: el launcher se dibuja detrás de las barras del sistema y estas quedan transparentes.
     * hidden: pantalla completa (las barras aparecen un momento al deslizar desde el borde).
     * normal: barras negras como el launcher original.
     */
    @Suppress("DEPRECATION")
    private fun applyBarMode(mode: String) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val edgeToEdge = mode != "normal"
        WindowCompat.setDecorFitsSystemWindows(window, !edgeToEdge)
        val barColor = if (edgeToEdge) Color.TRANSPARENT else Color.BLACK
        window.statusBarColor = barColor
        window.navigationBarColor = barColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        if (mode == "hidden") {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ---------------------------------------------------------------- Widgets

    private fun startAddWidget(info: AppWidgetProviderInfo) {
        val id = try {
            state.widgets.allocate()
        } catch (e: Exception) {
            state.toast("No se pudo crear el widget")
            return
        }
        pendingWidgetId = id
        pendingWidgetInfo = info
        val allowed = try {
            state.widgets.manager.bindAppWidgetIdIfAllowed(id, info.provider)
        } catch (e: Exception) {
            false
        }
        if (allowed) {
            configureOrAdd(id, info)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            try {
                bindLauncher.launch(intent)
            } catch (e: Exception) {
                state.widgets.delete(id)
                clearPending()
                state.toast("Esta pantalla no permite añadir widgets")
            }
        }
    }

    private fun configureOrAdd(id: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            try {
                state.widgets.host.startAppWidgetConfigureActivityForResult(this, id, 0, REQUEST_CONFIGURE, null)
                return
            } catch (e: Exception) {
                // Si no se puede configurar, se añade tal cual
            }
        }
        addWidget(id, info)
    }

    private fun addWidget(id: Int, info: AppWidgetProviderInfo) {
        state.addWidgetItem(id, info, state.cellWidthPx, state.cellHeightPx)
        clearPending()
    }

    private fun clearPending() {
        pendingWidgetId = -1
        pendingWidgetInfo = null
    }

    @Deprecated("Necesario para startAppWidgetConfigureActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE) {
            val id = pendingWidgetId
            val info = pendingWidgetInfo
            if (resultCode == Activity.RESULT_OK && id != -1 && info != null) {
                addWidget(id, info)
            } else if (id != -1) {
                state.widgets.delete(id)
                clearPending()
            }
        }
    }

    companion object {
        private const val REQUEST_CONFIGURE = 4711
    }
}
