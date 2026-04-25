package io.goodmidnight.transfer.core.connection.wifidirect

import io.goodmidnight.transfer.core.exception.ApplicationException
import io.goodmidnight.transfer.core.exception.ErrorCode

sealed class WifiDirectException(
    override val message: String,
    override val cause: Throwable? = null,
    override val errorCode: ErrorCode,
) : ApplicationException(message, cause, errorCode) {

    data class DiscoveryException(
        override val message: String = "Wi-Fi Direct Discovery Error",
        override val cause: Throwable? = null,
        override val errorCode: ErrorCode = WifiDirectErrorCode.DISCOVERY_FAILED,
    ) : WifiDirectException(message, cause, errorCode)

    data class ConnectionException(
        override val message: String = "Wi-Fi Direct Connection Error",
        override val cause: Throwable? = null,
        override val errorCode: ErrorCode = WifiDirectErrorCode.CONNECTION_FAILED,
    ) : WifiDirectException(message, cause, errorCode)

    data class GroupException(
        override val message: String = "Wi-Fi Direct Group Error",
        override val cause: Throwable? = null,
        override val errorCode: ErrorCode = WifiDirectErrorCode.GROUP_CREATION_FAILED,
    ) : WifiDirectException(message, cause, errorCode)
}