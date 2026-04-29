package io.goodmidnight.transfer.core.connection.bluetooth

import io.goodmidnight.transfer.core.connection.model.Peer

data class BluetoothState(
    val isScanning: Boolean = false,
    val isAdvertising: Boolean = false,
    val discoveredDevices: List<Peer> = emptyList(),
)