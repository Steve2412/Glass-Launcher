package com.glass.launcher.media

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
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

/**
 * Lee la música que suena en la pantalla (incluido el audio Bluetooth del iPhone)
 * a través de las MediaSession de Android, y resuelve la carátula:
 * 1) la que manda el teléfono por Bluetooth, 2) si no llega, la busca en internet.
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

    private var registered = false
    private var controllers: List<MediaController> = emptyList()
    private var active: MediaController? = null
    private var broadcastTrack: TrackInfo? = null

    private val artCache = LruCache<String, Bitmap>(24)
    private var artKey: String? = null
    private var artJob: Job? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        onSessions(list ?: emptyList())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = pick()
        override fun onMetadataChanged(metadata: MediaMetadata?) = pick()
        override fun onSessionDestroyed() = refreshSessions()
    }

    /** Respaldo: algunos reproductores avisan por broadcast en vez de MediaSession. */
    private val metaReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            i ?: return
            val title = i.getStringExtra("track") ?: return
            val artist = i.getStringExtra("artist").orEmpty()
            val playing = i.getBooleanExtra("playing", i.getBooleanExtra("isPlaying", true))
            val duration = i.getLongExtra("duration", 0L)
            val position = i.getLongExtra("position", 0L)
            broadcastTrack = TrackInfo(
                title = title, artist = artist, album = i.getStringExtra("album").orEmpty(),
                durationMs = duration, positionMs = position,
                positionUpdatedAt = SystemClock.elapsedRealtime(), speed = 1f,
                playing = playing, sourcePkg = null,
            )
            if (active == null) publish(broadcastTrack, null)
        }
    }

    init {
        try {
            val f = IntentFilter().apply {
                addAction("com.android.music.metachanged")
                addAction("com.android.music.playstatechanged")
                addAction("com.spotify.music.metadatachanged")
            }
            ContextCompat.registerReceiver(context, metaReceiver, f, ContextCompat.RECEIVER_EXPORTED)
        } catch (_: Exception) {
        }
        net.onConnected = { if (_art.value == null) retryArtwork() }
    }

    fun refreshAccess() {
        val ok = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        _hasAccess.value = ok
        if (ok && !registered) {
            try {
                msm.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
                registered = true
            } catch (_: Exception) {
            }
        }
        if (ok) refreshSessions()
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
        try {
            onSessions(msm.getActiveSessions(listenerComponent))
        } catch (_: Exception) {
            onSessions(emptyList())
        }
    }

    private fun onSessions(list: List<MediaController>) {
        controllers.forEach { try { it.unregisterCallback(controllerCallback) } catch (_: Exception) {} }
        controllers = list
        controllers.forEach { try { it.registerCallback(controllerCallback, handler) } catch (_: Exception) {} }
        pick()
    }

    private fun pick() {
        val playing = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val previous = active?.let { a -> controllers.firstOrNull { it.sessionToken == a.sessionToken } }
        val withMeta = controllers.firstOrNull { it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) != null }
        active = playing ?: previous ?: withMeta ?: controllers.firstOrNull()
        val c = active
        if (c == null) {
            publish(broadcastTrack, null)
            return
        }
        val md = c.metadata
        if (md == null) {
            publish(broadcastTrack, null)
            return
        }
        val st = c.playbackState
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (title.isNullOrBlank()) {
            publish(broadcastTrack, null)
            return
        }
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val info = TrackInfo(
            title = title,
            artist = artist,
            album = md.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = st?.position ?: 0L,
            positionUpdatedAt = st?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = st?.playbackSpeed ?: 1f,
            playing = st?.state == PlaybackState.STATE_PLAYING,
            sourcePkg = c.packageName,
        )
        publish(info, md)
    }

    private fun publish(info: TrackInfo?, md: MediaMetadata?) {
        _track.value = info
        if (info == null) {
            artKey = null
            artJob?.cancel()
            _art.value = null
            return
        }
        val key = "${info.artist}|${info.title}".lowercase()
        val embedded = md?.let {
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

    /** Se llama al cambiar los ajustes de carátulas. */
    fun onArtworkSettingsChanged() {
        if (_art.value == null) retryArtwork()
    }

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
}
