package com.glass.launcher.media

import android.Manifest
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.util.LruCache
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.glass.launcher.data.NetworkMonitor
import com.glass.launcher.data.Prefs
import com.glass.launcher.data.TrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lee la música que suena en la pantalla, probando varias fuentes en orden:
 * 1) MediaSession de Android (incluidas las que se anuncian en notificaciones),
 * 2) eventos AVRCP de Bluetooth (broadcast), 3) la notificación de la app de música Bluetooth,
 * 4) broadcasts genéricos de reproductores.
 * La carátula: la que llega por Bluetooth / notificación; si no, se busca en internet.
 */
class MediaRepository(
    private val context: Context,
    private val prefs: Prefs,
    private val net: NetworkMonitor,
    private val scope: CoroutineScope,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val msm = context.getSystemService(MediaSessionManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val listenerComponent = ComponentName(context, MediaListenerService::class.java)

    private val _track = MutableStateFlow<TrackInfo?>(null)
    val track: StateFlow<TrackInfo?> = _track

    private val _art = MutableStateFlow<ImageBitmap?>(null)
    val art: StateFlow<ImageBitmap?> = _art

    private val _hasAccess = MutableStateFlow(false)
    val hasAccess: StateFlow<Boolean> = _hasAccess

    /** De dónde salió la canción actual (para el diagnóstico). */
    var currentSource: String = "ninguna"
        private set

    private var registered = false
    private var sessionControllers: List<MediaController> = emptyList()
    private var notifControllers: List<MediaController> = emptyList()
    private var controllers: List<MediaController> = emptyList()
    private var active: MediaController? = null

    private var avrcpTrack: TrackInfo? = null
    private var avrcpMeta: MediaMetadata? = null
    private var notifTrack: TrackInfo? = null
    private var notifArt: Bitmap? = null
    private var broadcastTrack: TrackInfo? = null

    private var notifSummary: List<String> = emptyList()
    private val events = ArrayDeque<String>()

    private val artCache = LruCache<String, Bitmap>(24)
    private var artKey: String? = null
    private var artJob: Job? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        sessionControllers = list ?: emptyList()
        rebuildControllers()
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = pick()
        override fun onMetadataChanged(metadata: MediaMetadata?) = pick()
        override fun onSessionDestroyed() = refreshSessions()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            i ?: return
            logEvent("${i.action?.substringAfterLast('.')} ${extrasSummary(i.extras)}")
            when (i.action) {
                AVRCP_TRACK_EVENT -> onAvrcpEvent(i)
                else -> onGenericBroadcast(i)
            }
        }
    }

    init {
        try {
            val f = IntentFilter().apply { BROADCASTS.forEach { addAction(it) } }
            ContextCompat.registerReceiver(context, receiver, f, ContextCompat.RECEIVER_EXPORTED)
        } catch (_: Exception) {
        }
        net.onConnected = { if (_art.value == null) retryArtwork() }
    }

    // ---------------------------------------------------------------- Acceso y sesiones

    fun refreshAccess() {
        val ok = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        _hasAccess.value = ok
        if (ok && !registered) {
            try {
                msm.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
                registered = true
            } catch (e: Exception) {
                logEvent("Error al escuchar sesiones: ${e.javaClass.simpleName}")
            }
        }
        if (ok) {
            refreshSessions()
            MediaListenerService.instance?.let { onNotifications(it.safeActive()) }
        }
    }

    fun openAccessSettings() {
        val intents = listOf(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent.flattenToString()),
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (i in intents) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
    }

    private fun refreshSessions() {
        sessionControllers = try {
            msm.getActiveSessions(listenerComponent)
        } catch (e: Exception) {
            emptyList()
        }
        rebuildControllers()
    }

    private fun rebuildControllers() {
        val all = (sessionControllers + notifControllers).distinctBy { it.sessionToken }
        controllers.forEach { try { it.unregisterCallback(controllerCallback) } catch (_: Exception) {} }
        controllers = all
        controllers.forEach { try { it.registerCallback(controllerCallback, handler) } catch (_: Exception) {} }
        pick()
    }

    // ---------------------------------------------------------------- Notificaciones

    /** Lo llama MediaListenerService cada vez que cambian las notificaciones. */
    fun onNotifications(list: List<StatusBarNotification>) {
        val tokens = mutableListOf<MediaSession.Token>()
        var best: StatusBarNotification? = null
        var bestScore = 0
        val summary = mutableListOf<String>()
        for (sbn in list) {
            if (sbn.packageName == context.packageName) continue
            val n = sbn.notification ?: continue
            val extras = n.extras
            val token = sessionToken(extras)
            if (token != null) tokens.add(token)
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            summary.add("${sbn.packageName} | ${title.take(40)} | ${text.take(40)}${if (token != null) " | [sesión]" else ""}")
            if (title.isBlank()) continue
            val score = when {
                token != null -> 3
                n.category == Notification.CATEGORY_TRANSPORT -> 2
                MUSIC_PKG.containsMatchIn(sbn.packageName) -> 1
                else -> 0
            }
            if (score > bestScore) {
                bestScore = score
                best = sbn
            }
        }
        notifSummary = summary
        notifControllers = tokens.mapNotNull {
            try {
                MediaController(context, it)
            } catch (e: Exception) {
                null
            }
        }

        val b = best
        if (b != null) {
            val ex = b.notification.extras
            notifTrack = TrackInfo(
                title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                artist = (ex.getCharSequence(Notification.EXTRA_TEXT) ?: ex.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString().orEmpty(),
                album = "",
                durationMs = 0L, positionMs = 0L, positionUpdatedAt = SystemClock.elapsedRealtime(), speed = 1f,
                playing = true,
                sourcePkg = b.packageName,
            )
            notifArt = largeIcon(b.notification)
        } else {
            notifTrack = null
            notifArt = null
        }
        rebuildControllers()
    }

    @Suppress("DEPRECATION")
    private fun sessionToken(extras: Bundle?): MediaSession.Token? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            extras?.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            extras?.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        }
    } catch (e: Exception) {
        null
    }

    private fun largeIcon(n: Notification): Bitmap? = try {
        val d: Drawable? = n.getLargeIcon()?.loadDrawable(context)
        when {
            d == null -> null
            d is BitmapDrawable && d.bitmap != null -> d.bitmap
            else -> {
                val w = d.intrinsicWidth.takeIf { it > 0 } ?: 256
                val h = d.intrinsicHeight.takeIf { it > 0 } ?: 256
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                    d.setBounds(0, 0, w, h)
                    d.draw(Canvas(it))
                }
            }
        }
    } catch (e: Exception) {
        null
    }

    // ---------------------------------------------------------------- Broadcasts

    @Suppress("DEPRECATION")
    private fun onAvrcpEvent(i: Intent) {
        val md = try {
            if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(AVRCP_EXTRA_METADATA, MediaMetadata::class.java)
            else i.getParcelableExtra(AVRCP_EXTRA_METADATA)
        } catch (e: Exception) {
            null
        }
        val st = try {
            if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(AVRCP_EXTRA_PLAYBACK, PlaybackState::class.java)
            else i.getParcelableExtra(AVRCP_EXTRA_PLAYBACK)
        } catch (e: Exception) {
            null
        }
        if (md != null) {
            avrcpMeta = md
            val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            avrcpTrack = if (title.isNullOrBlank()) null else TrackInfo(
                title = title,
                artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                album = md.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
                positionMs = 0L, positionUpdatedAt = SystemClock.elapsedRealtime(), speed = 1f,
                playing = avrcpTrack?.playing ?: true, sourcePkg = "com.android.bluetooth",
            )
        }
        if (st != null) {
            avrcpTrack = avrcpTrack?.copy(
                playing = st.state == PlaybackState.STATE_PLAYING,
                positionMs = st.position,
                positionUpdatedAt = SystemClock.elapsedRealtime(),
            )
        }
        pick()
    }

    private fun onGenericBroadcast(i: Intent) {
        val ex = i.extras ?: return
        val title = firstString(ex, "track", "title", "name", "song", "songName", "music_title") ?: return
        if (title.isBlank()) return
        val artist = firstString(ex, "artist", "singer", "artistName", "music_artist").orEmpty()
        val playing = when {
            ex.containsKey("playing") -> ex.getBoolean("playing")
            ex.containsKey("isPlaying") -> ex.getBoolean("isPlaying")
            else -> true
        }
        broadcastTrack = TrackInfo(
            title = title, artist = artist, album = firstString(ex, "album").orEmpty(),
            durationMs = (ex.get("duration") as? Number)?.toLong() ?: 0L,
            positionMs = (ex.get("position") as? Number)?.toLong() ?: 0L,
            positionUpdatedAt = SystemClock.elapsedRealtime(), speed = 1f,
            playing = playing, sourcePkg = null,
        )
        pick()
    }

    private fun firstString(b: Bundle, vararg keys: String): String? {
        for (k in keys) {
            val v = try {
                @Suppress("DEPRECATION")
                b.get(k)
            } catch (e: Exception) {
                null
            }
            if (v is CharSequence && v.isNotBlank()) return v.toString()
        }
        return null
    }

    // ---------------------------------------------------------------- Elegir la fuente

    private fun pick() {
        val withTitle = controllers.filter {
            !it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank() ||
                !it.metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).isNullOrBlank()
        }
        val playing = withTitle.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val previous = active?.let { a -> withTitle.firstOrNull { it.sessionToken == a.sessionToken } }
        active = playing ?: previous ?: withTitle.firstOrNull()

        val c = active
        val md = c?.metadata
        if (c != null && md != null) {
            val st = c.playbackState
            val info = TrackInfo(
                title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty(),
                artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                    ?: "",
                album = md.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
                positionMs = st?.position ?: 0L,
                positionUpdatedAt = st?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
                speed = st?.playbackSpeed ?: 1f,
                playing = st?.state == PlaybackState.STATE_PLAYING,
                sourcePkg = c.packageName,
            )
            currentSource = "sesión multimedia (${c.packageName})"
            publish(info, md, null)
            return
        }
        when {
            avrcpTrack != null -> {
                currentSource = "Bluetooth AVRCP"
                publish(avrcpTrack, avrcpMeta, null)
            }
            notifTrack != null -> {
                currentSource = "notificación (${notifTrack?.sourcePkg})"
                publish(notifTrack, null, notifArt)
            }
            broadcastTrack != null -> {
                currentSource = "broadcast"
                publish(broadcastTrack, null, null)
            }
            else -> {
                currentSource = "ninguna"
                publish(null, null, null)
            }
        }
    }

    private fun publish(info: TrackInfo?, md: MediaMetadata?, bitmap: Bitmap?) {
        _track.value = info
        if (info == null) {
            artKey = null
            artJob?.cancel()
            _art.value = null
            return
        }
        val key = "${info.artist}|${info.title}".lowercase()
        val embedded = bitmap ?: md?.let {
            it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        }
        if (embedded != null) {
            artJob?.cancel()
            artKey = key
            artCache.put(key, embedded)
            _art.value = embedded.asImageBitmap()
            return
        }
        if (key == artKey && _art.value != null) return
        artKey = key
        artCache.get(key)?.let {
            _art.value = it.asImageBitmap()
            return
        }
        _art.value = null
        val uri = md?.let {
            it.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: it.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: it.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        }
        loadArtwork(key, info, uri)
    }

    private fun retryArtwork() {
        val t = _track.value ?: return
        val key = "${t.artist}|${t.title}".lowercase()
        loadArtwork(key, t, null)
    }

    private fun loadArtwork(key: String, info: TrackInfo, uri: String?) {
        artJob?.cancel()
        artJob = scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                var b: Bitmap? = null
                if (!uri.isNullOrBlank()) {
                    b = try {
                        context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (b == null && canDownload()) {
                    b = ArtworkFetcher.fetch(info.title, info.artist)
                }
                b
            }
            if (bmp != null) {
                artCache.put(key, bmp)
                if (artKey == key) _art.value = bmp.asImageBitmap()
            }
        }
    }

    private fun canDownload(): Boolean {
        if (!prefs.artworkOnline) return false
        val s = net.state.value
        if (!s.connected) return false
        return !prefs.artworkWifiOnly || s.wifi
    }

    fun onArtworkSettingsChanged() {
        if (_art.value == null) retryArtwork()
    }

    // ---------------------------------------------------------------- Controles

    fun playPause() {
        val c = active
        if (c != null) {
            val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
            if (playing) c.transportControls.pause() else c.transportControls.play()
        } else {
            key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    fun next() {
        active?.transportControls?.skipToNext() ?: key(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous() {
        active?.transportControls?.skipToPrevious() ?: key(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    private fun key(code: Int) {
        try {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        } catch (_: Exception) {
        }
    }

    fun sourceLabel(pkg: String?, phoneName: String?): String {
        val bt = phoneName ?: "Teléfono"
        return when {
            pkg == null -> "Bluetooth · $bt"
            pkg.contains("bluetooth", true) || pkg.contains(".bt", true) -> "Bluetooth · $bt"
            pkg == "com.spotify.music" -> "Spotify"
            pkg == "com.google.android.apps.youtube.music" -> "YouTube Music"
            pkg == "com.google.android.youtube" -> "YouTube"
            else -> try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg
            }
        }
    }

    // ---------------------------------------------------------------- Diagnóstico

    private fun logEvent(s: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        synchronized(events) {
            events.addFirst("$t $s")
            while (events.size > 15) events.removeLast()
        }
    }

    private fun extrasSummary(b: Bundle?): String {
        if (b == null) return "{}"
        return try {
            b.keySet().joinToString(", ", "{", "}") { k ->
                @Suppress("DEPRECATION")
                val v = b.get(k)
                "${k.substringAfterLast('.')}=${v.toString().take(40)}"
            }.take(300)
        } catch (e: Exception) {
            "{?}"
        }
    }

    fun diagnostics(): List<Pair<String, List<String>>> {
        val sections = mutableListOf<Pair<String, List<String>>>()
        sections.add(
            "Estado" to listOf(
                "Acceso a notificaciones: ${if (_hasAccess.value) "sí" else "NO"}",
                "Servicio de notificaciones conectado: ${if (MediaListenerService.instance != null) "sí" else "no"}",
                "Fuente actual: $currentSource",
                "Canción: ${_track.value?.let { "${it.title} — ${it.artist}" } ?: "(nada)"}",
                "Carátula: ${if (_art.value != null) "sí" else "no"}",
            )
        )
        sections.add(
            "Sesiones multimedia (${controllers.size})" to controllers.map {
                val md = it.metadata
                val st = it.playbackState?.state
                "${it.packageName} · estado=$st · ${md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "(sin título)"}"
            }.ifEmpty { listOf("(ninguna)") }
        )
        sections.add("Notificaciones" to notifSummary.take(15).ifEmpty { listOf("(ninguna)") })
        sections.add("Eventos recibidos" to synchronized(events) { events.toList() }.ifEmpty { listOf("(ninguno)") })
        val pm = context.packageManager
        val related = try {
            pm.getInstalledApplications(0)
                .map { it.packageName }
                .filter { RELATED_PKG.containsMatchIn(it) }
                .sorted()
        } catch (e: Exception) {
            emptyList()
        }
        sections.add("Apps relacionadas instaladas" to related.ifEmpty { listOf("(ninguna)") })
        sections.add("Registro del sistema" to readLog())
        return sections
    }

    private fun readLog(): List<String> {
        val granted = context.checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            return listOf(
                "Sin permiso. Opcional, por ADB:",
                "adb shell pm grant ${context.packageName} android.permission.READ_LOGS",
            )
        }
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "1500", "-v", "brief"))
            val lines = p.inputStream.bufferedReader().readLines()
            p.destroy()
            lines.filter { LOG_FILTER.containsMatchIn(it) }.takeLast(40).map { it.take(160) }.ifEmpty { listOf("(sin coincidencias)") }
        } catch (e: Exception) {
            listOf("Error leyendo el registro: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        const val AVRCP_TRACK_EVENT = "android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT"
        const val AVRCP_EXTRA_METADATA = "android.bluetooth.avrcp-controller.profile.extra.METADATA"
        const val AVRCP_EXTRA_PLAYBACK = "android.bluetooth.avrcp-controller.profile.extra.PLAYBACK"

        val BROADCASTS = listOf(
            AVRCP_TRACK_EVENT,
            "android.bluetooth.avrcp-controller.profile.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.a2dp-sink.profile.action.PLAYING_STATE_CHANGED",
            "com.android.music.metachanged",
            "com.android.music.playstatechanged",
            "com.android.music.queuechanged",
            "com.spotify.music.metadatachanged",
            "com.spotify.music.playbackstatechanged",
            "com.htc.music.metachanged",
            "com.sec.android.app.music.metachanged",
            "fm.last.android.metachanged",
            "com.amazon.mp3.metachanged",
            "com.miui.player.metachanged",
            "com.real.IMP.metachanged",
            "com.rdio.android.metachanged",
            "com.andrew.apollo.metachanged",
        )

        private val MUSIC_PKG = Regex("music|bt|bluetooth|audio|player|zlink|carplay|goc|syu|a2dp|media", RegexOption.IGNORE_CASE)
        private val RELATED_PKG = Regex("bt|bluetooth|music|goc|syu|zlink|carplay|carlink|a2dp|avrcp|media|phone|dial", RegexOption.IGNORE_CASE)
        private val LOG_FILTER = Regex("avrcp|a2dp|metadata|title|artist|btmusic|bt_music|goc|track|now ?playing|id3", RegexOption.IGNORE_CASE)
    }
}
