package io.goodmidnight.transfer.core.connection.bluetooth

import io.goodmidnight.transfer.core.exception.ApplicationException
import io.goodmidnight.transfer.core.exception.ErrorCode

sealed class BluetoothException(
    override val message: String,
    override val cause: Throwable? = null,
    override val errorCode: ErrorCode,
) : ApplicationException(message, cause, errorCode) {

    data class ScanException(
        override val message: String = "BLE Scan Error",
        override val cause: Throwable? = null,
        override val errorCode: ErrorCode = BluetoothErrorCode.SCAN_FAILED,
    ) : BluetoothException(message, cause, errorCode)

    data class AdvertiseException(
        override val message: String = "BLE Advertise Error",
        override val cause: Throwable? = null,
        override val errorCode: ErrorCode = BluetoothErrorCode.ADVERTISE_FAILED,
    ) : BluetoothException(message, cause, errorCode)

    data class GattConnectionException(
        override val message: String = "BLE GATT Connection Error",
        override val cause: Throwable? = null,
        override val errorCode: ErrorCode = BluetoothErrorCode.GATT_FAILED,
    ) : BluetoothException(message, cause, errorCode)
}