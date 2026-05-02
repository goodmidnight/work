package io.goodmidnight.transfer.core.utils

import android.Manifest
import android.os.Build

/**
 * Returns an array of required runtime permissions based on the device's OS version.
 * Includes permissions for Camera (QR scanning), Wi-Fi Direct, Bluetooth, and Notifications.
 */
fun getRequiredPermissions(): Array<String> {
    val permissions = mutableListOf<String>()

    // Common required permission: Camera for QR code scanning
    permissions.add(Manifest.permission.CAMERA)

    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            // Android 13 (API 33) and above
            permissions.addAll(
                listOf(
                    Manifest.permission.NEARBY_WIFI_DEVICES, // Required for Wi-Fi Direct & Hotspot
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.POST_NOTIFICATIONS   // Required for Foreground Service UI updates
                )
            )
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Android 12 (API 31, 32)
            permissions.addAll(
                listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION, // Still required for Wi-Fi Direct on Android 12
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                )
            )
        }
        else -> {
            // Android 11 (API 30) and below
            permissions.addAll(
                listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION, // Legacy devices require location for BT/Wi-Fi scanning
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    return permissions.toTypedArray()
}