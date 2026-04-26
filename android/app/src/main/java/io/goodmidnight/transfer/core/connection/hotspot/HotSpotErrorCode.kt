package io.goodmidnight.transfer.core.connection.hotspot

import io.goodmidnight.transfer.core.exception.ErrorCode

enum class HotSpotErrorCode(override val code: String, override val message: String? = null) : ErrorCode {
    START_FAILED("HOTSPOT001", "Failed to start Local Only Hotspot"),
    UNSUPPORTED("HOTSPOT002", "Hotspot is not supported on this device")
}
