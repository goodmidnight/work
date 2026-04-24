package io.goodmidnight.transfer.core.connection.nsd

import io.goodmidnight.transfer.core.exception.ErrorCode

enum class NsdErrorCode(
    override val message: String? = null,
    override val code: String,
) : ErrorCode {
    DISCOVERY_START_FAILED(message = "Failed to start service discovery", code = "NSD001"),
    DISCOVERY_STOP_FAILED(message = "Failed to stop service discovery", code = "NSD002"),
    BROADCAST_START_FAILED(message = "Failed to register service broadcast", code = "NSD003"),
    BROADCAST_STOP_FAILED(message = "Failed to unregister service broadcast", code = "NSD004"),
    RESOLVE_FAILED(message = "Failed to resolve service", code = "NSD005")
}