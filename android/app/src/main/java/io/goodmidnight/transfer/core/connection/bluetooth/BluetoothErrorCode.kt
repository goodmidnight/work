package io.goodmidnight.transfer.core.connection.bluetooth

import io.goodmidnight.transfer.core.exception.ErrorCode

enum class BluetoothErrorCode(
    override val message: String? = null,
    override val code: String,
) : ErrorCode {
    SCAN_FAILED(message = "Failed to start BLE scan", code = "BLE001"),
    ADVERTISE_FAILED(message = "Failed to start BLE advertising", code = "BLE002"),
    GATT_FAILED(message = "Failed to establish GATT connection", code = "BLE003"),
}