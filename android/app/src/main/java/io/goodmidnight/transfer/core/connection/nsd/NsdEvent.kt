package io.goodmidnight.transfer.core.connection.nsd

sealed interface NsdEvent {

    /**
     * Starts scanning the local Wi-Fi network for nearby devices broadcasting the specific service.
     */
    data object StartDiscovery : NsdEvent

    /**
     *  Stops the ongoing discovery process to save battery and network resources.
     */
    data object StopDiscovery : NsdEvent

    /**
     * Starts broadcasting this device's presence on the local network.
     * @param deviceName The name to display to other users
     * @param port The socket port number where this device is listening for incoming file transfers.
     */
    data class StartBroadcasting(val deviceName: String, val port: Int) : NsdEvent

    /**
     * Stops broadcasting the service, effectively hiding this device from the network.
     */
    data object StopBroadcasting : NsdEvent

    /**
     * Triggered when the user selects a specific peer from the discovered list.
     * Initiates the process to halt discovery and prepare for socket connection.
     * * @param ipAddress The resolved IPv4 or IPv6 address of the target peer.
     * @param port The target socket port number.
     */
    data class ConnectToPeer(val ipAddress: String, val port: Int) : NsdEvent
}