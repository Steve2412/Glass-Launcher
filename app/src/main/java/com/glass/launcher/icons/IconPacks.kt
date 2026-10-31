package com.glass.launcher.icons

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

data class IconPackInfo(val pkg: String, val label: String)

/** Un pack de iconos de terceros (formato estándar Nova / ADW / Apex). */
class IconPack(
    val pkg: String,
    private val res: Resources,
    /** "paquete/clase" -> nombre del drawable */
    private val map: Map<String, String>,
    /** Todos los drawables que ofrece el pack (para elegir a mano). */
    val drawables: List<String>,
) {
    private val byPackage: Map<String, String> = buildMap {
        map.forEach { (k, v) -> putIfAbsent(k.substringBefore('/'), v) }
    }

    fun drawableNameFor(componentKey: String): String? =
        map[componentKey] ?: byPackage[componentKey.substringBefore('/')]

    fun load(name: String): Drawable? {
        return try {
            @Suppress("DiscouragedApi")
            val id = res.getIdentifier(name, "drawable", pkg)
            if (id == 0) null else res.getDrawableForDensity(id, DisplayMetrics.DENSITY_XXHIGH, null)
        } catch (e: Exception) {
            null
        }
    }
}

class IconPackManager(private val context: Context) {
    private val cache = HashMap<String, IconPack?>()

    fun installedPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, IconPackInfo>()
        for (action in ACTIONS) {
            try {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(Intent(action), 0).forEach { ri ->
                    val pkg = ri.activityInfo.packageName
                    if (pkg !in found) {
                        found[pkg] = IconPackInfo(pkg, ri.loadLabel(pm).toString())
                    }
                }
            } catch (_: Exception) {
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    @Synchronized
    fun load(pkg: String): IconPack? {
        if (cache.containsKey(pkg)) return cache[pkg]
        val pack = try {
            val res = context.packageManager.getResourcesForApplication(pkg)
            val map = HashMap<String, String>()
            val all = LinkedHashSet<String>()
            parse(res, pkg, "appfilter") { parser ->
                if (parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (comp != null && drawable != null) {
                        parseComponent(comp)?.let { map[it] = drawable }
                        all.add(drawable)
                    }
                }
            }
            val listed = LinkedHashSet<String>()
            parse(res, pkg, "drawable") { parser ->
                if (parser.name == "item") {
                    parser.getAttributeValue(null, "drawable")?.let { listed.add(it) }
                }
            }
            IconPack(pkg, res, map, (if (listed.isNotEmpty()) listed else all).toList())
        } catch (e: Exception) {
            null
        }
        cache[pkg] = pack
        return pack
    }

    @Synchronized
    fun clear() = cache.clear()

    private fun parse(res: Resources, pkg: String, name: String, onTag: (XmlPullParser) -> Unit) {
        val parser: XmlPullParser? = try {
            @Suppress("DiscouragedApi")
            val id = res.getIdentifier(name, "xml", pkg)
            if (id != 0) {
                res.getXml(id)
            } else {
                val stream = context.createPackageContext(pkg, 0).assets.open("$name.xml")
                Xml.newPullParser().apply { setInput(stream, "UTF-8") }
            }
        } catch (e: Exception) {
            null
        }
        parser ?: return
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) onTag(parser)
                event = parser.next()
            }
        } catch (_: Exception) {
        }
    }

    private fun parseComponent(raw: String): String? {
        val inner = raw.substringAfter('{', "").substringBefore('}', "")
        if (!inner.contains('/')) return null
        val pkg = inner.substringBefore('/')
        var cls = inner.substringAfter('/')
        if (cls.startsWith(".")) cls = pkg + cls
        return "$pkg/$cls"
    }

    companion object {
        val ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "com.anddoes.launcher.THEME",
            "com.teslacoilsw.launcher.THEME",
        )
    }
}
