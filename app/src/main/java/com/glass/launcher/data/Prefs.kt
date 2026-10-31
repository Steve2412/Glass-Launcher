package com.glass.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("glass_launcher", Context.MODE_PRIVATE)

    var wallpaper: String
        get() = sp.getString("wallpaper", "noche") ?: "noche"
        set(v) = sp.edit().putString("wallpaper", v).apply()

    /** "glass", "original" o el paquete de un icon pack. */
    var iconStyle: String
        get() = sp.getString("icon_style", "glass") ?: "glass"
        set(v) = sp.edit().putString("icon_style", v).apply()

    var packFrame: Boolean
        get() = sp.getBoolean("pack_frame", true)
        set(v) = sp.edit().putBoolean("pack_frame", v).apply()

    var artworkOnline: Boolean
        get() = sp.getBoolean("artwork_online", true)
        set(v) = sp.edit().putBoolean("artwork_online", v).apply()

    var artworkWifiOnly: Boolean
        get() = sp.getBoolean("artwork_wifi_only", true)
        set(v) = sp.edit().putBoolean("artwork_wifi_only", v).apply()

    var immersive: Boolean
        get() = sp.getBoolean("immersive", false)
        set(v) = sp.edit().putBoolean("immersive", v).apply()

    var initialized: Boolean
        get() = sp.getBoolean("initialized", false)
        set(v) = sp.edit().putBoolean("initialized", v).apply()

    var nextId: Long
        get() = sp.getLong("next_id", 1L)
        set(v) = sp.edit().putLong("next_id", v).apply()

    fun loadDock(): List<String> {
        val s = sp.getString("dock", null) ?: return List(DOCK_SIZE) { "" }
        return try {
            val arr = JSONArray(s)
            List(DOCK_SIZE) { i -> if (i < arr.length()) arr.optString(i, "") else "" }
        } catch (e: Exception) {
            List(DOCK_SIZE) { "" }
        }
    }

    fun saveDock(keys: List<String>) {
        sp.edit().putString("dock", JSONArray(keys).toString()).apply()
    }

    fun loadHome(): List<HomeItem> = HomeItem.listFromJson(sp.getString("home", null))

    fun saveHome(items: List<HomeItem>) {
        sp.edit().putString("home", HomeItem.listToJson(items)).apply()
    }

    /** appKey -> "paqueteDelPack|nombreDrawable" */
    fun loadIconOverrides(): Map<String, String> {
        val s = sp.getString("icon_overrides", null) ?: return emptyMap()
        return try {
            val o = JSONObject(s)
            o.keys().asSequence().associateWith { o.getString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveIconOverrides(map: Map<String, String>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        sp.edit().putString("icon_overrides", o.toString()).apply()
    }

    companion object {
        const val DOCK_SIZE = 6
    }
}
