package com.glass.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.util.DisplayMetrics
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val user: UserHandle = Process.myUserHandle()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    /** Se llama cuando cambia la lista (instalar / desinstalar). */
    var onChanged: (() -> Unit)? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
    }

    init {
        try {
            launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {
        }
        reload()
    }

    fun reload() {
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                try {
                    launcherApps.getActivityList(null, user)
                        .map {
                            val cn = it.componentName
                            AppEntry(
                                key = "${cn.packageName}/${cn.className}",
                                pkg = cn.packageName,
                                cls = cn.className,
                                label = it.label?.toString()?.trim().orEmpty().ifEmpty { cn.packageName },
                            )
                        }
                        .filter { it.pkg != context.packageName }
                        .distinctBy { it.key }
                        .sortedBy { it.label.lowercase() }
                } catch (e: Exception) {
                    emptyList()
                }
            }
            _apps.value = list
            onChanged?.invoke()
        }
    }

    fun find(key: String?): AppEntry? = if (key.isNullOrEmpty()) null else _apps.value.firstOrNull { it.key == key }

    fun loadIcon(entry: AppEntry): Drawable? = try {
        launcherApps.getActivityList(entry.pkg, user)
            .firstOrNull { it.componentName.className == entry.cls }
            ?.getIcon(DisplayMetrics.DENSITY_XXHIGH)
            ?: context.packageManager.getApplicationIcon(entry.pkg)
    } catch (e: Exception) {
        null
    }

    fun launch(entry: AppEntry) {
        try {
            launcherApps.startMainActivity(entry.component, user, null, null)
        } catch (e: Exception) {
            try {
                val i = context.packageManager.getLaunchIntentForPackage(entry.pkg)
                    ?: throw IllegalStateException()
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            } catch (e2: Exception) {
                Toast.makeText(context, "No se pudo abrir ${entry.label}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openAppInfo(entry: AppEntry) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${entry.pkg}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    fun uninstall(entry: AppEntry) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:${entry.pkg}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    /** Primera app cuya etiqueta o paquete contenga alguna de las palabras. */
    fun findByKeywords(words: List<String>, exclude: Set<String> = emptySet()): AppEntry? {
        val list = _apps.value.filter { it.key !in exclude }
        for (w in words) {
            val hit = list.firstOrNull { it.label.lowercase().contains(w) }
                ?: list.firstOrNull { it.pkg.lowercase().contains(w.replace(" ", "")) }
            if (hit != null) return hit
        }
        return null
    }
}
