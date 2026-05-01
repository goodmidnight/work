package io.goodmidnight.transfer.core.connection

import io.goodmidnight.transfer.core.exception.ErrorCode

/**
 * Represents specific error codes and default messages for the Connection Orchestrator.
 */
enum class ConnectionErrorCode(
    override val message: String? = null,
    override val code: String,
) : ErrorCode {

    UNSUPPORTED_PEER_TYPE(
        message = "The selected peer type is not supported for connection.",
        code = "ORC001"
    )
}