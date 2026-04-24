package io.goodmidnight.transfer.core.connection.bluetooth

/**
 * Defines all user intents or commands that can be sent to the BluetoothController.
 */
sealed interface BluetoothEvent {
    /**
     *  Starts scanning for nearby BLE devices broadcasting the specific Service UUID.
     */
    data object StartScan : BluetoothEvent

    /**
     * Stops the ongoing BLE scan.
     */
    data object StopScan : BluetoothEvent

    /**
     * Starts broadcasting this device's Wi-Fi IP and Port via BLE.
     * Usually called by the Receiver (Hub) so senders can find where to connect.
     */
    data class StartAdvertising(val wifiIp: String, val port: Int) : BluetoothEvent

    /**
     * Stops broadcasting the BLE signal.
     */
    data object StopAdvertising : BluetoothEvent

    /**
     *  Initiates a Wi-Fi socket connection using the IP obtained from the BLE handshake.
     */
    data class HandshakeAndConnect(val ipAddress: String, val port: Int) : BluetoothEvent
}