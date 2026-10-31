package com.glass.launcher.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<NetState> = _state

    /** Se llama cuando vuelve la conexión. */
    var onConnected: (() -> Unit)? = null

    init {
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = update()
                override fun onLost(network: Network) = update()
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update()
            })
        } catch (_: Exception) {
        }
    }

    private fun update() {
        val before = _state.value.connected
        val now = read()
        _state.value = now
        if (!before && now.connected) onConnected?.invoke()
    }

    private fun read(): NetState = try {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps == null) {
            NetState(false, false)
        } else {
            NetState(
                connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            )
        }
    } catch (e: Exception) {
        NetState(false, false)
    }
}
