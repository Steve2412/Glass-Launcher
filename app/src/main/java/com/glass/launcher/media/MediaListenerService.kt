package com.glass.launcher.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.glass.launcher.GlassApp

/**
 * Con el "acceso a notificaciones" el launcher puede leer las sesiones de música
 * y también las notificaciones de la app de música Bluetooth de la pantalla.
 */
class MediaListenerService : NotificationListenerService() {

    private val media get() = (applicationContext as GlassApp).state.media

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        media.refreshAccess()
        media.onNotifications(safeActive())
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        media.onNotifications(safeActive())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        media.onNotifications(safeActive())
    }

    fun safeActive(): List<StatusBarNotification> = try {
        activeNotifications?.toList() ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        @Volatile
        var instance: MediaListenerService? = null
            private set
    }
}
