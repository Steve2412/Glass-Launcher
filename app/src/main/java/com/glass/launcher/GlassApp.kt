package com.glass.launcher

import android.app.Application

class GlassApp : Application() {
    lateinit var state: LauncherState
        private set

    override fun onCreate() {
        super.onCreate()
        state = LauncherState(this)
    }
}
