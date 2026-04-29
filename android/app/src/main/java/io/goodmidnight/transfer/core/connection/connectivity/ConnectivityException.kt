package io.goodmidnight.transfer.core.connection.connectivity

import io.goodmidnight.transfer.core.exception.ApplicationException
import io.goodmidnight.transfer.core.exception.ErrorCode

sealed class ConnectivityException(
    override val message: String,
    override val cause: Throwable? = null,
    override val errorCode: ErrorCode,
) : ApplicationException(message, cause, errorCode) {

    data class BindingFailedException(
        override val message: String = "Failed to bind the application process to the specific P2P network.",
        override val cause: Throwable? = null,
        override val errorCode: ErrorCode = ConnectivityErrorCode.NETWORK_BINDING_FAILED,
    ) : ConnectivityException(message, cause, errorCode)

    data class RequestNetworkException(
        override val message: String = "Failed to request network allocation from the Android OS.",
        override val cause: Throwable? = null,
        override val errorCode: ErrorCode = ConnectivityErrorCode.NETWORK_REQUEST_FAILED,
    ) : ConnectivityException(message, cause, errorCode)

    data class UnsupportedVersionException(
        override val message: String = "Auto-connection to hotspot is only supported on Android 10 (API 29) or higher.",
        override val cause: Throwable? = null,
        override val errorCode: ErrorCode = ConnectivityErrorCode.UNSUPPORTED_ANDROID_VERSION,
    ) : ConnectivityException(message, cause, errorCode)

    data class UserConnectionDeniedException(
        override val message: String = "The connection request was denied by the user or the target network was not found.",
        override val cause: Throwable? = null,
        override val errorCode: ErrorCode = ConnectivityErrorCode.USER_DENIED_CONNECTION,
    ) : ConnectivityException(message, cause, errorCode)
}