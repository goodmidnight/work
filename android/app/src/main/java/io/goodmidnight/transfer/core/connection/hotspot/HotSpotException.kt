package io.goodmidnight.transfer.core.connection.hotspot

import io.goodmidnight.transfer.core.exception.ApplicationException
import io.goodmidnight.transfer.core.exception.ErrorCode


sealed class HotSpotException(message: String, cause: Throwable? = null, errorCode: ErrorCode) : ApplicationException(message, cause, errorCode) {
    class StartException(message: String = "Hotspot Start Error", cause: Throwable? = null) : HotSpotException(message, cause, HotSpotErrorCode.START_FAILED)
}