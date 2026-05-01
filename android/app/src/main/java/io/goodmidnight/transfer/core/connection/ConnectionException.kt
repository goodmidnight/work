package io.goodmidnight.transfer.core.connection

import io.goodmidnight.transfer.core.connection.wifidirect.WifiDirectErrorCode
import io.goodmidnight.transfer.core.exception.ApplicationException
import io.goodmidnight.transfer.core.exception.ErrorCode

/**
 * Represents specific errors that occur at the orchestration layer.
 */
sealed class ConnectionException(
    override val message: String,
    override val cause: Throwable? = null,
    override val errorCode: ErrorCode,
) : ApplicationException(message, cause, errorCode) {

    data class UnsupportedPeerTypeException(
        override val message: String = "Unsupported peer type selected for connection",
        override val cause: Throwable? = null,
        override val errorCode: ErrorCode = ConnectionErrorCode.UNSUPPORTED_PEER_TYPE
    ) : ConnectionException(message, cause, errorCode)
}