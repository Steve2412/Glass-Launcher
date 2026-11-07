package com.glass.launcher.data

import android.content.ComponentName
import org.json.JSONArray
import org.json.JSONObject

/** Una app instalada que se puede abrir. `key` = "paquete/clase". */
data class AppEntry(
    val key: String,
    val pkg: String,
    val cls: String,
    val label: String,
) {
    val component: ComponentName get() = ComponentName(pkg, cls)
}

enum class HomeType { MUSIC, PHONE, APP, WIDGET, SETTINGS, VEHICLE }

/** Un elemento colocado en la pantalla de inicio. */
data class HomeItem(
    val id: Long,
    val type: HomeType,
    val appKey: String? = null,
    val widgetId: Int = -1,
    val cols: Int = 1,
    val rows: Int = 1,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        if (appKey != null) put("app", appKey)
        put("wid", widgetId)
        put("c", cols)
        put("r", rows)
    }

    companion object {
        fun fromJson(o: JSONObject): HomeItem? = try {
            HomeItem(
                id = o.getLong("id"),
                type = HomeType.valueOf(o.getString("type")),
                appKey = if (o.has("app")) o.getString("app") else null,
                widgetId = o.optInt("wid", -1),
                cols = o.optInt("c", 1),
                rows = o.optInt("r", 1),
            )
        } catch (e: Exception) {
            null
        }

        fun listToJson(items: List<HomeItem>): String =
            JSONArray().apply { items.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(s: String?): List<HomeItem> {
            if (s.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(s)
                (0 until arr.length()).mapNotNull { fromJson(arr.getJSONObject(it)) }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

/** Datos de la canción que suena. */
data class TrackInfo(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val positionMs: Long,
    val positionUpdatedAt: Long,
    val speed: Float,
    val playing: Boolean,
    val sourcePkg: String?,
)

data class PhoneState(
    val connected: Boolean = false,
    val name: String? = null,
    val battery: Int? = null,
)

data class WeatherInfo(
    val tempC: Int,
    val code: Int,
    val isDay: Boolean,
)

data class NetState(
    val connected: Boolean = false,
    val wifi: Boolean = false,
)
