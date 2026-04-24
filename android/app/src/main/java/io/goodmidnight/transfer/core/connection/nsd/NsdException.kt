package io.goodmidnight.transfer.core.connection.nsd

import io.goodmidnight.transfer.core.exception.ApplicationException
import io.goodmidnight.transfer.core.exception.ErrorCode

sealed class NsdException(
    override val message: String,
    override val cause: Throwable? = null,
    override val errorCode: ErrorCode,
) : ApplicationException(message, cause, errorCode) {

    data class DiscoveryException(
        override val message: String = "NSD Discovery Error",
        override val cause: Throwable? = null,
        override val errorCode: ErrorCode = NsdErrorCode.DISCOVERY_START_FAILED,
    ) : NsdException(message, cause, errorCode)

    data class BroadcastException(
        override val message: String = "NSD Broadcast Error",
        override val cause: Throwable? = null,
        override val errorCode: ErrorCode = NsdErrorCode.BROADCAST_START_FAILED,
    ) : NsdException(message, cause, errorCode)
}