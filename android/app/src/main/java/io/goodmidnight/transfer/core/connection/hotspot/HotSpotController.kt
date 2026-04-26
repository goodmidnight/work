package io.goodmidnight.transfer.core.connection.hotspot

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.goodmidnight.transfer.core.exception.ApplicationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controller responsible for managing the Local Only Hotspot.
 * It creates a temporary Wi-Fi Access Point that other devices can connect to.
 * This AP does not provide internet access, making it ideal for secure, high-speed P2P transfers.
 * Note: Requires location and nearby devices permissions to function properly.
 */
@Singleton
@SuppressLint("MissingPermission")
class HotSpotController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("HotSpotController"))
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    // Holds the active hotspot reservation to keep the AP alive and close it later.
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    private val _state = MutableStateFlow(HotSpotState())
    val state: StateFlow<HotSpotState> = _state.asStateFlow()

    private val _event = Channel<HotSpotEvent>(
        capacity = Channel.BUFFERED,
        onBufferOverflow = BufferOverflow.DROP_LATEST
    )

    private val _effect = MutableSharedFlow<HotSpotEffect>()
    val effect = _effect.asSharedFlow()

    /**
     * Listener to monitor the lifecycle of the Local Only Hotspot.
     * Kept private to enforce encapsulation.
     */
    private val hotspotListener = object : WifiManager.LocalOnlyHotspotCallback() {
        override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
            super.onStarted(reservation)
            this@HotSpotController.reservation = reservation

            if (reservation == null) return

            val ssid: String
            val password: String

            // Extract SSID and Password handling modern Android API deprecations securely
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

            _state.update {
                it.copy(
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

    init {
        observeEvents()
    }

    /**
     * Exposes a thread-safe method to send user intents to this controller.
     */
    suspend fun processEvent(event: HotSpotEvent) = _event.send(event)

    private fun observeEvents() {
        scope.launch {
            _event.receiveAsFlow().collect { event ->
                when (event) {
                    is HotSpotEvent.StartHotspot -> handleStartHotspot()
                    is HotSpotEvent.StopHotspot -> handleStopHotspot()
                }
            }
        }
    }

    /**
     *  Requests the OS to spin up a temporary Local Only Hotspot.
     */
    private fun handleStartHotspot() {
        wifiManager.startLocalOnlyHotspot(hotspotListener, null)
    }

    /**
     *  Tears down the Hotspot and resets the internal state.
     */
    private fun handleStopHotspot() {
        reservation?.close()
        reservation = null
        _state.update { HotSpotState() }
    }

    /**
     *  Helper method to dispatch exceptions securely to the shared effect flow.
     */
    private fun emitError(exception: ApplicationException) {
        scope.launch {
            _effect.emit(HotSpotEffect.SendError(exception))
        }
    }
}