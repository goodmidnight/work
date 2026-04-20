package io.goodmidnight.transfer.core.exception

/**
 * A sealed class for general-purpose exceptions that are not specific to a single domain.
 */
sealed class CommonException(
    override val message: String,
    override val cause: Throwable? = null,
    override val errorCode: ErrorCode = CommonErrorCode.UNKNOWN_ERROR,
) : ApplicationException(message, cause, errorCode) {

    /**
     * Exception used when the exact cause of the failure is not categorized.
     */
    data class UnknownException(
        override val message: String = "Unknown Exception",
        override val cause: Throwable? = null,
        override val errorCode: ErrorCode = CommonErrorCode.UNKNOWN_ERROR,
    ) : CommonException(message, cause, errorCode)
}
