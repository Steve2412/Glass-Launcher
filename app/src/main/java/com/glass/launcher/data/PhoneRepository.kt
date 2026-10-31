package com.glass.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Estado del teléfono conectado por Bluetooth (nombre y batería).
 * Algunas pantallas usan un módulo Bluetooth propio y no lo exponen a Android;
 * en ese caso el widget muestra "Sin conexión".
 */
@SuppressLint("MissingPermission")
class PhoneRepository(private val context: Context) {
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _state = MutableStateFlow(PhoneState())
    val state: StateFlow<PhoneState> = _state

    private var started = false
    private var device: BluetoothDevice? = null
    private val profileDevices = HashMap<Int, List<String>>()
    private var lastEvent: String = "(ninguno)"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            intent ?: return
            val d: BluetoothDevice? = deviceFrom(intent)
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> if (d != null && isPhone(d)) setDevice(d)
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (d != null && d.address == device?.address) {
                    device = null
                    _state.value = PhoneState()
                    queryProfiles()
                }
                ACTION_BATTERY -> if (d != null && d.address == device?.address) {
                    val level = intent.getIntExtra(EXTRA_BATTERY, -1)
                    _state.value = _state.value.copy(battery = level.takeIf { it in 0..100 })
                }
                // Batería que el iPhone informa por manos libres (HFP): escala 0–5
                HFP_AG_EVENT -> {
                    val level = intent.getIntExtra(HFP_EXTRA_BATTERY, -1)
                    if (d != null && device == null) setDevice(d)
                    if (level in 0..5) _state.value = _state.value.copy(battery = level * 20)
                }
                HFP_CONNECTION, A2DP_SINK_CONNECTION -> {
                    val st = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                    if (d != null && st == BluetoothProfile.STATE_CONNECTED) setDevice(d)
                    if (d != null && st == BluetoothProfile.STATE_DISCONNECTED && d.address == device?.address) {
                        device = null
                        _state.value = PhoneState()
                        queryProfiles()
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> queryProfiles()
            }
            lastEvent = "${intent.action?.substringAfterLast('.')} ${intent.extras?.keySet()?.joinToString(",") { it.substringAfterLast('.') }}"
        }
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (started || adapter == null || !hasPermission()) return
        started = true
        try {
            val f = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(ACTION_BATTERY)
                addAction(HFP_AG_EVENT)
                addAction(HFP_CONNECTION)
                addAction(A2DP_SINK_CONNECTION)
            }
            ContextCompat.registerReceiver(context, receiver, f, ContextCompat.RECEIVER_EXPORTED)
        } catch (_: Exception) {
        }
        queryProfiles()
    }

    private fun queryProfiles() {
        val a = adapter ?: return
        if (!hasPermission()) return
        // 11 = A2DP_SINK, 16 = HEADSET_CLIENT (perfiles de una unidad de auto)
        for (profile in listOf(11, 16, BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
            try {
                a.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile?) {
                        try {
                            val list = proxy?.connectedDevices.orEmpty()
                            profileDevices[p] = list.map { safeName(it) }
                            val d = list.firstOrNull()
                            if (d != null && device == null) setDevice(d)
                        } catch (_: Exception) {
                        }
                        try {
                            a.closeProfileProxy(p, proxy)
                        } catch (_: Exception) {
                        }
                    }

                    override fun onServiceDisconnected(p: Int) {}
                }, profile)
            } catch (_: Exception) {
            }
        }
    }

    private fun setDevice(d: BluetoothDevice) {
        device = d
        val name = try {
            (if (Build.VERSION.SDK_INT >= 30) d.alias else null) ?: d.name
        } catch (e: Exception) {
            null
        }
        val same = _state.value.connected && _state.value.name == name
        _state.value = PhoneState(connected = true, name = name, battery = readBattery(d) ?: if (same) _state.value.battery else null)
    }

    private fun safeName(d: BluetoothDevice): String = try {
        d.name ?: d.address
    } catch (e: Exception) {
        "?"
    }

    fun diagnostics(): List<String> {
        val a = adapter
        val lines = mutableListOf<String>()
        lines.add("Permiso Bluetooth: ${if (hasPermission()) "sí" else "NO"}")
        if (a == null) {
            lines.add("Android no expone un adaptador Bluetooth (módulo propio de la pantalla)")
            return lines
        }
        try {
            lines.add("Adaptador encendido: ${if (a.isEnabled) "sí" else "no"}")
            lines.add("Emparejados: " + (a.bondedDevices?.joinToString { safeName(it) }?.ifEmpty { "(ninguno)" } ?: "(ninguno)"))
        } catch (e: Exception) {
            lines.add("No se pudo leer el adaptador: ${e.javaClass.simpleName}")
        }
        val names = mapOf(11 to "A2DP receptor", 16 to "Manos libres (HFP)", BluetoothProfile.A2DP to "A2DP", BluetoothProfile.HEADSET to "Auricular")
        names.forEach { (p, n) -> lines.add("$n: ${profileDevices[p]?.joinToString()?.ifEmpty { "nadie" } ?: "sin datos"}") }
        lines.add("Teléfono detectado: ${_state.value.name ?: "no"} · batería ${_state.value.battery ?: "?"}")
        lines.add("Último evento: $lastEvent")
        if (!started) queryProfiles()
        return lines
    }

    private fun isPhone(d: BluetoothDevice): Boolean = try {
        val major = d.bluetoothClass?.majorDeviceClass
        major == null || major == android.bluetooth.BluetoothClass.Device.Major.PHONE ||
            major == android.bluetooth.BluetoothClass.Device.Major.UNCATEGORIZED
    } catch (e: Exception) {
        true
    }

    private fun readBattery(d: BluetoothDevice): Int? = try {
        val m = d.javaClass.getMethod("getBatteryLevel")
        (m.invoke(d) as? Int)?.takeIf { it in 0..100 }
    } catch (e: Throwable) {
        null
    }

    @Suppress("DEPRECATION")
    private fun deviceFrom(i: Intent): BluetoothDevice? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        const val ACTION_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY = "android.bluetooth.device.extra.BATTERY_LEVEL"
        const val HFP_AG_EVENT = "android.bluetooth.headsetclient.profile.action.AG_EVENT"
        const val HFP_EXTRA_BATTERY = "android.bluetooth.headsetclient.extra.BATTERY_LEVEL"
        const val HFP_CONNECTION = "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED"
        const val A2DP_SINK_CONNECTION = "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED"
    }
}
