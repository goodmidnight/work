package io.goodmidnight.transfer.core.exception

/**
 * Enumeration of common error codes used across the application.
 * Each error code contains a unique identifier and a default human-readable message.
 */
enum class CommonErrorCode(
    override val message: String? = null,
    override val code: String,
) : ErrorCode {
    /**
     * Fallback error code for unexpected or unidentified issues.
     */
    UNKNOWN_ERROR(message = "Unknown Error", code = "D001"),
}
