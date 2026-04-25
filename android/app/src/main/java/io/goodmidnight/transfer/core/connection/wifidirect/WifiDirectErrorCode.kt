package io.goodmidnight.transfer.core.connection.wifidirect

import io.goodmidnight.transfer.core.exception.ErrorCode

enum class WifiDirectErrorCode(
    override val message: String? = null,
    override val code: String,
) : ErrorCode {
    DISCOVERY_FAILED(message = "Failed to discover peers", code = "WFD001"),
    CONNECTION_FAILED(message = "Failed to connect to peer", code = "WFD002"),
    GROUP_CREATION_FAILED(message = "Failed to create group", code = "WFD003")
}