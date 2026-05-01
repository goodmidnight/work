package io.goodmidnight.transfer.core.connection

import io.goodmidnight.transfer.core.connection.model.Peer

/**
 * Represents the unified UI state for the connection lobby.
 * Aggregates discovery results from all underlying hardware controllers.
 *
 * @property hostIp Target IP and Port for the upcoming socket connection.
 * @property activeSsid SSID to display as a QR code or text if operating as a Hotspot.
 */
data class ConnectionState(
    val currentMode: ConnectionMode = ConnectionMode.AUTO,
    val isDiscovering: Boolean = false,
    val isHosting: Boolean = false,
    val isConnected: Boolean = false,
    val discoveredPeers: List<Peer> = emptyList(),
    val localIp: String? = null,
    val hostIp: String? = null,
    val hostPort: Int = 8080,
    val activeSsid: String? = null
) {
    enum class ConnectionMode {
        AUTO,        // Automatically selects the best protocol
        NSD,         // mDNS over shared Wi-Fi
        WIFI_DIRECT, // Wi-Fi Direct (P2P)
        HOTSPOT,     // Local Only Hotspot (Cross-platform compatibility)
        BLUETOOTH    // BLE Discovery
    }
}