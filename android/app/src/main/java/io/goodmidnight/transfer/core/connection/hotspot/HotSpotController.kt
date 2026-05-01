package io.goodmidnight.transfer.core.connection.hotspot

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.goodmidnight.transfer.core.base.BaseController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controller responsible for managing the Local Only Hotspot.
 * It creates a temporary Wi-Fi Access Point that other devices can connect to.
 * This AP does not provide internet access, making it ideal for secure, high-speed P2P transfers.
 * Note: Requires Location and Nearby Devices permissions to function properly.
 */
@Singleton
@SuppressLint("MissingPermission")
class HotSpotController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BaseController<HotSpotState, HotSpotEvent, HotSpotEffect, HotSpotException>(
    "HotSpotController",
    HotSpotState()
) {
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    // Holds the active hotspot reservation to keep the AP alive and close it later.
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    override suspend fun handleEvent(event: HotSpotEvent) {
        when (event) {
            is HotSpotEvent.StartHotspot -> handleStartHotspot()
            is HotSpotEvent.StopHotspot -> handleStopHotspot()
        }
    }

    /**
     * Requests the OS to spin up a temporary Local Only Hotspot.
     * Wrapped in runCatching to prevent crashes if the system rejects the request due to missing permissions or hardware limits.
     */
    private fun handleStartHotspot() {
        runCatching {
            wifiManager.startLocalOnlyHotspot(hotspotListener, null)
        }.onFailure { e ->
            Log.w("HotSpotController", "Failed to start Local Only Hotspot", e)
            emitError(
                HotSpotException.StartException(
                    message = "System rejected hotspot start request",
                    cause = e
                )
            )
        }
    }

    /**
     * Tears down the Hotspot and resets the internal state.
     */
    private fun handleStopHotspot() {
        reservation?.let { res ->
            runCatching { res.close() }
                .onFailure { e -> Log.w("HotSpotController", "Failed to close hotspot cleanly", e) }
        }
        reservation = null
        updateState { HotSpotState() }
    }

    /**
     * Listener to monitor the lifecycle of the Local Only Hotspot.
     */
    private val hotspotListener = object : WifiManager.LocalOnlyHotspotCallback() {
        override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
            super.onStarted(reservation)
            this@HotSpotController.reservation = reservation

            if (reservation == null) return

            val ssid: String
            val password: String

            // Extract SSID and Password securely, handling modern Android API deprecations.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val config = reservation.softApConfiguration
                ssid = config.wifiSsid?.toString()?.removeSurrounding("\"") ?: "Unknown"
                password = config.passphrase ?: "No Password"
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val config = reservation.softApConfiguration
                @Suppress("DEPRECATION")
                ssid = config.ssid ?: "Unknown"
                password = config.passphrase ?: "No Password"
            } else {
                @Suppress("DEPRECATION")
                val config = reservation.wifiConfiguration
                ssid = config?.SSID?.removeSurrounding("\"") ?: "Unknown"
                password = config?.preSharedKey ?: "No Password"
            }

            updateState {
                copy(
                    isActive = true,
                    ssid = ssid,
                    password = password
                )
            }
        }

        override fun onFailed(reason: Int) {
            super.onFailed(reason)
            emitError(HotSpotException.StartException(message = "Hotspot start failed with reason code: $reason"))
        }

        override fun onStopped() {
            super.onStopped()
            handleStopHotspot()
        }
    }

    /**
     * Prevents battery drain and system resource leaks by ensuring the hotspot reservation
     * is safely closed when the controller is destroyed.
     */
    override fun clear() {
        handleStopHotspot()
        super.clear()
    }
}