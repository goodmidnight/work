package io.goodmidnight.transfer.core.connection.nsd

sealed interface NsdEffect {

    /**
     * Emitted when the user selects a peer and the IP/Port details are ready.
     * This signals the ConnectionManager to start the actual TCP/UDP socket connection.
     */
    data class ConnectionEstablished(val ipAddress: String, val port: Int) : NsdEffect
}