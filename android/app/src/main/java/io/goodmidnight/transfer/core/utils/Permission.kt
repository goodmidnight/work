package io.goodmidnight.transfer.core.utils

import android.Manifest
import android.os.Build

/**
 * Returns an array of required runtime permissions based on the device's OS version.
 * Includes permissions for Camera (QR scanning), Wi-Fi Direct, Bluetooth, and Notifications.
 */
fun getRequiredPermissions(): Array<String> {
    val permissions = mutableListOf<String>()

    // Common required permissions
    permissions.add(Manifest.permission.CAMERA)
    permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
    permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            // Android 13 (API 33) and above
            permissions.addAll(
                listOf(
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            )
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Android 12 (API 31, 32)
            permissions.addAll(
                listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                )
            )
        }
    }

    return permissions.toTypedArray()
}
