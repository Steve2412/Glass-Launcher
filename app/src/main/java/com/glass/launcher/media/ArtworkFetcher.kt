package com.glass.launcher.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Busca la carátula en el catálogo público de iTunes (gratis, sin cuenta)
 * usando el título y el artista que llegan por Bluetooth.
 */
object ArtworkFetcher {

    fun fetch(title: String, artist: String): Bitmap? {
        val t = clean(title)
        val a = clean(artist)
        if (t.isBlank()) return null
        val candidates = listOf("$a $t", t).filter { it.isNotBlank() }.distinct()
        for (term in candidates) {
            val url = searchArtworkUrl(term) ?: continue
            val bmp = download(url)
            if (bmp != null) return bmp
        }
        return null
    }

    private fun searchArtworkUrl(term: String): String? {
        val q = URLEncoder.encode(term.trim(), "UTF-8")
        val json = get("https://itunes.apple.com/search?term=$q&media=music&entity=song&limit=1") ?: return null
        return try {
            val results = JSONObject(json).getJSONArray("results")
            if (results.length() == 0) return null
            val art = results.getJSONObject(0).optString("artworkUrl100")
            if (art.isNullOrBlank()) null else art.replace("100x100bb", "600x600bb")
        } catch (e: Exception) {
            null
        }
    }

    private fun get(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 8000
                setRequestProperty("User-Agent", "GlassLauncher/1.0")
            }
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun download(url: String): Bitmap? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 10000
            }
            if (conn.responseCode != 200) null else conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Quita cosas como "(feat. X)", "- Remastered 2011", "[Official Video]". */
    fun clean(s: String): String = s
        .replace(Regex("\\s*[(\\[][^)\\]]*(feat|ft\\.|remaster|official|video|audio|lyric|live|version)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+-\\s+.*(remaster|version|mix|edit).*$", RegexOption.IGNORE_CASE), "")
        .trim()
}
