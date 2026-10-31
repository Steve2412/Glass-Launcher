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
                BluetoothAdapter.ACTION_STATE_CHANGED -> queryProfiles()
            }
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
                            val d = proxy?.connectedDevices?.firstOrNull()
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
        _state.value = PhoneState(connected = true, name = name, battery = readBattery(d))
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
    }
}
