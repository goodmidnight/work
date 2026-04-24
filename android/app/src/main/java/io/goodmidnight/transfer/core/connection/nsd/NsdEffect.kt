package io.goodmidnight.transfer.core.connection.nsd

import io.goodmidnight.transfer.core.exception.ApplicationException

sealed interface NsdEffect {

    /**
     * Emitted when a critical error occurs during discovery or broadcasting.
     * The upper layer should intercept this to show a Snackbar or Toast.
     */
    data class SendError(val exception: ApplicationException) : NsdEffect

    /**
     * Emitted when the user selects a peer and the IP/Port details are ready.
     * This signals the ConnectionManager to start the actual TCP/UDP socket connection.
     */
    data class ConnectionEstablished(val ipAddress: String, val port: Int) : NsdEffect
}