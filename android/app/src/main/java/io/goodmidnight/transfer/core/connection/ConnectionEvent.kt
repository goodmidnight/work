package io.goodmidnight.transfer.core.connection

import io.goodmidnight.transfer.core.connection.ConnectionState.ConnectionMode
import io.goodmidnight.transfer.core.connection.model.Peer

sealed interface ConnectionEvent {
    data class StartDiscovery(val mode: ConnectionMode = ConnectionMode.AUTO) : ConnectionEvent
    data object StopDiscovery : ConnectionEvent
    data class ConnectToPeer(val peer: Peer) : ConnectionEvent

    /**
     * Starts the hosting process (Broadcasting/Server mode).
     * @param mode Selected connection technology (NSD, BLE, Hotspot, etc.)
     * @param deviceName The name to be displayed to other users.
     * @param wifiIp Current device's IP (Local IP for Wi-Fi or Gateway IP for Hotspot).
     * @param port Socket port for file transfer.
     * @param ssid The SSID of the network when operating in Hotspot mode (used for auto-connect).
     * @param pw The password of the network when operating in Hotspot mode (used for auto-connect).
     */
    data class StartHosting(
        val mode: ConnectionMode,
        val deviceName: String,
        val wifiIp: String,
        val port: Int = 8080,
        val ssid: String? = null,
        val pw: String? = null,
    ) : ConnectionEvent

    /**
     * Connects to a specific host's network and IP.
     */
    data class ConnectToHotspot(
        val ssid: String,
        val pw: String,
        val hostIp: String
    ) : ConnectionEvent

    data object StopHosting : ConnectionEvent
    data object Disconnect : ConnectionEvent
}