package io.goodmidnight.transfer.core.connection.bluetooth

import io.goodmidnight.transfer.core.connection.model.Peer

/**
 * Represents the current UI/Data state of the Bluetooth discovery and advertising process.
 * This state is continuously observed by the ConnectionManager.
 * @param isScanning True if the device is currently searching for peers
 * @param isAdvertising True if the device is broadcasting its Wi-Fi IP
 * @param discoveredDevices List of unique peers found via BLE scan
 */
data class BluetoothState(
    val isScanning: Boolean = false,
    val isAdvertising: Boolean = false,
    val discoveredDevices: List<Peer> = emptyList(),
)