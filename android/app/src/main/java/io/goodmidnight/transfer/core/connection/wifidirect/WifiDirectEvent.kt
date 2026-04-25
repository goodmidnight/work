package io.goodmidnight.transfer.core.connection.wifidirect

sealed interface WifiDirectEvent {

    /**
     * Starts scanning the environment for nearby Wi-Fi Direct devices.
     */
    data object StartDiscovery : WifiDirectEvent

    /**
     *  Stops the ongoing discovery process to conserve battery and system resources.
     */
    data object StopDiscovery : WifiDirectEvent

    /**
     * Forces the creation of a P2P group, making this device the Group Owner (Hub).
     * Useful when explicitly setting up the device to receive files.
     */
    data object CreateGroup : WifiDirectEvent

    /**
     * Tears down the current P2P group and disconnects all participating devices.
     */
    data object RemoveGroup : WifiDirectEvent

    /**
     * Attempts to establish a Wi-Fi Direct connection with a specific peer.
     * @param deviceAddress The hardware MAC address of the target peer.
     */
    data class ConnectToPeer(val deviceAddress: String) : WifiDirectEvent
}