package com.glass.launcher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/** Vista de widget que detecta la pulsación larga (para mover / quitar). */
class LongPressWidgetView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null
    private var downX = 0f
    private var downY = 0f
    private var fired = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPress = Runnable {
        fired = true
        onLongPress?.invoke()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false
                downX = ev.x
                downY = ev.y
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) removeCallbacks(longPress)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
        }
        return fired
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (fired) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) fired = false
            return true
        }
        return super.onTouchEvent(event)
    }
}

class GlassWidgetHost(context: Context, id: Int) : AppWidgetHost(context, id) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        LongPressWidgetView(context)
}

class WidgetManager(context: Context) {
    val host = GlassWidgetHost(context.applicationContext, HOST_ID)
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)

    fun providers(): List<AppWidgetProviderInfo> = try {
        manager.installedProviders
    } catch (e: Exception) {
        emptyList()
    }

    fun info(id: Int): AppWidgetProviderInfo? = try {
        manager.getAppWidgetInfo(id)
    } catch (e: Exception) {
        null
    }

    fun allocate(): Int = host.allocateAppWidgetId()

    fun delete(id: Int) {
        try {
            host.deleteAppWidgetId(id)
        } catch (_: Exception) {
        }
    }

    fun startListening() {
        try {
            host.startListening()
        } catch (_: Exception) {
        }
    }

    fun stopListening() {
        try {
            host.stopListening()
        } catch (_: Exception) {
        }
    }

    fun createView(context: Context, id: Int): LongPressWidgetView? {
        val info = info(id) ?: return null
        return try {
            (host.createView(context, id, info) as? LongPressWidgetView)
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    fun updateSize(view: AppWidgetHostView, widthDp: Int, heightDp: Int) {
        try {
            view.updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val HOST_ID = 1024
    }
}
