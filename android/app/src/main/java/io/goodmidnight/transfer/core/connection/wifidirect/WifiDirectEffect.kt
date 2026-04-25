package io.goodmidnight.transfer.core.connection.wifidirect

import io.goodmidnight.transfer.core.exception.ApplicationException
sealed interface WifiDirectEffect {

    /**
     * Emitted when a Wi-Fi Direct group is successfully formed and the IP address is resolved.
     * Signals the ConnectionManager to begin TCP/UDP socket communication.
     * @param groupOwnerIp The IP address of the P2P Group Owner (Server).
     */
    data class ConnectionEstablished(val groupOwnerIp: String) : WifiDirectEffect

    /**
     * Emitted when a critical error occurs during discovery, grouping, or connection.
     * The upper layer should intercept this to display a Snackbar or Toast.
     * @param exception The exception containing error details.
     */
    data class SendError(val exception: ApplicationException) : WifiDirectEffect
}