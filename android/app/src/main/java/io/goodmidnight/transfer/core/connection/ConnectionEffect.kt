package io.goodmidnight.transfer.core.connection

import io.goodmidnight.transfer.core.connection.model.Peer

sealed interface ConnectionEffect {
    /**
     * Emitted when a P2P socket connection routing is fully established and ready.
     * @param hostIp The resolved IP address to connect the socket to.
     * @param port The target port for the socket.
     * @param type The specific protocol used for this connection (NSD, WiFi_Direct, etc.)
     */
    data class ConnectionEstablished(
        val hostIp: String,
        val port: Int,
        val type: Peer.Type
    ) : ConnectionEffect
}