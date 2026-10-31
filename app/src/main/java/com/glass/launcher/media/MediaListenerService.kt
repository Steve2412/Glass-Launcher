package com.glass.launcher.media

import android.service.notification.NotificationListenerService

/**
 * Servicio vacío: solo existe para que el usuario conceda "acceso a notificaciones",
 * que es lo que permite leer la música que suena (MediaSession) y controlarla.
 */
class MediaListenerService : NotificationListenerService()
