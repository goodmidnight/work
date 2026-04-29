package io.goodmidnight.transfer.core.connection.connectivity

import io.goodmidnight.transfer.core.exception.ErrorCode

enum class ConnectivityErrorCode(
    override val message: String? = null,
    override val code: String,
) : ErrorCode {
    NETWORK_BINDING_FAILED(
        message = "Failed to bind the application process to the specific P2P network.",
        code = "CON001"
    ),
    NETWORK_REQUEST_FAILED(
        message = "Failed to request network allocation from the Android OS.",
        code = "CON002"
    ),
    UNSUPPORTED_ANDROID_VERSION(
        message = "Auto-connection to hotspot is only supported on Android 10 (API 29) or higher.",
        code = "CON003"
    ),
    USER_DENIED_CONNECTION(
        message = "The connection request was denied by the user or the target network was not found.",
        code = "CON004"
    )
}