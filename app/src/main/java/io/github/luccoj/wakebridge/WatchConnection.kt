package io.github.luccoj.wakebridge

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Whether the watch is connected over Bluetooth LE, by any app (that is, Gadgetbridge). Android
 * reports this only with the "Nearby devices" permission; without it the state is unknown, and
 * starting is allowed.
 */
object WatchConnection {
    enum class State { CONNECTED, DISCONNECTED, UNKNOWN }

    /** Tests set this instead of querying Bluetooth. */
    var override: State? = null

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun state(context: Context): State {
        override?.let { return it }
        val address = Settings(context).watchAddress ?: return State.UNKNOWN
        if (!hasPermission(context)) return State.UNKNOWN
        return try {
            val manager = context.getSystemService(BluetoothManager::class.java) ?: return State.UNKNOWN
            val adapter = manager.adapter ?: return State.UNKNOWN
            if (!adapter.isEnabled) return State.DISCONNECTED
            val gatt = manager.getConnectedDevices(BluetoothProfile.GATT)
                .any { it.address.equals(address, ignoreCase = true) }
            // Second opinion, for Android versions that list only this app's own connections
            val acl = runCatching {
                val device = adapter.getRemoteDevice(address.uppercase())
                device.javaClass.getMethod("isConnected").invoke(device) as Boolean
            }.getOrDefault(false)
            if (gatt || acl) State.CONNECTED else State.DISCONNECTED
        } catch (e: Exception) {
            State.UNKNOWN
        }
    }
}
