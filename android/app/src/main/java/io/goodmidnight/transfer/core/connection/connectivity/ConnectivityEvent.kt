package io.goodmidnight.transfer.core.connection.connectivity

sealed interface ConnectivityEvent {
    /**
     * Attempts to automatically connect to a specific hotspot using the acquired SSID and Password.
     */
    data class ConnectToHotspot(val ssid: String, val pw: String) : ConnectivityEvent

    /**
     * Binds the application process to the currently connected P2P Wi-Fi network.
     */
    data object RequestBinding : ConnectivityEvent

    /**
     * Releases the network binding and unregisters the network callback.
     */
    data object ReleaseBinding : ConnectivityEvent
}