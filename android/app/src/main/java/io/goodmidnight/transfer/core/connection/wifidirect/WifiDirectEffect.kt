package io.goodmidnight.transfer.core.connection.wifidirect

sealed interface WifiDirectEffect {

    /**
     * Emitted when a Wi-Fi Direct group is successfully formed and the IP address is resolved.
     * Signals the ConnectionManager to begin TCP/UDP socket communication.
     * @param groupOwnerIp The IP address of the P2P Group Owner (Server).
     */
    data class ConnectionEstablished(val groupOwnerIp: String) : WifiDirectEffect
}