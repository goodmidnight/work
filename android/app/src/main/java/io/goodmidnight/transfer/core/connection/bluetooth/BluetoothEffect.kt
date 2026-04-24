package io.goodmidnight.transfer.core.connection.bluetooth

import io.goodmidnight.transfer.core.exception.ApplicationException

/**
 * Represents one-time events (side-effects) emitted by the BluetoothController.
 * These are not state representations but triggers for actions like navigation or showing toasts.
 */
sealed interface BluetoothEffect {

    /**
     * Triggered when the BLE handshake is successful and we have the target IP/Port.
     */
    data class ConnectionEstablished(val ipAddress: String, val port: Int) : BluetoothEffect

    /**
     *  Triggered when an error occurs during scanning or advertising.
     */
    data class SendError(val exception: ApplicationException) : BluetoothEffect
}