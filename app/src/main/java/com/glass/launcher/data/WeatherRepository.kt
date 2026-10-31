package com.glass.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/** Clima actual con Open-Meteo (gratis, sin cuenta). */
class WeatherRepository(private val context: Context) {
    private val _weather = MutableStateFlow<WeatherInfo?>(null)
    val weather: StateFlow<WeatherInfo?> = _weather

    private var lastFetch = 0L

    suspend fun refresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastFetch < 25 * 60_000L && _weather.value != null) return
        val result = withContext(Dispatchers.IO) {
            val loc = location() ?: ipLocation() ?: return@withContext null
            fetch(loc.first, loc.second)
        }
        if (result != null) {
            _weather.value = result
            lastFetch = now
        }
    }

    @SuppressLint("MissingPermission")
    private fun location(): Pair<Double, Double>? {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        return try {
            val lm = context.getSystemService(LocationManager::class.java)
            lm.getProviders(true)
                .mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
                ?.let { it.latitude to it.longitude }
        } catch (e: Exception) {
            null
        }
    }

    private fun ipLocation(): Pair<Double, Double>? = try {
        val o = JSONObject(get("https://ipwho.is/") ?: "")
        if (o.optBoolean("success", false)) o.getDouble("latitude") to o.getDouble("longitude") else null
    } catch (e: Exception) {
        null
    }

    private fun fetch(lat: Double, lon: Double): WeatherInfo? = try {
        val json = get(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,is_day&timezone=auto"
        ) ?: ""
        val cur = JSONObject(json).getJSONObject("current")
        WeatherInfo(
            tempC = cur.getDouble("temperature_2m").roundToInt(),
            code = cur.optInt("weather_code", 0),
            isDay = cur.optInt("is_day", 1) == 1,
        )
    } catch (e: Exception) {
        null
    }

    private fun get(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 8000
            }
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
