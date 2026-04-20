package io.goodmidnight.transfer.core.exception

/**
 * Base abstract class for all custom exceptions in the application.
 * Extends [RuntimeException] to provide a unified way of handling domain-specific errors
 * with associated [ErrorCode].
 */
abstract class ApplicationException(
    override val message: String? = null,
    override val cause: Throwable? = null,
    open val errorCode: ErrorCode,
) : RuntimeException(message, cause) {

    /**
     * Prints the stack trace of the error for debugging purposes.
     */
    protected fun printErrorStackTrace() {
        printStackTrace()
    }

    /**
     * Returns a string representation of the exception, including the error code and its message.
     */
    override fun toString(): String {
        return "${javaClass.simpleName}(" +
                "message=${message ?: "null"}, " +
                "cause=${cause?.javaClass?.name ?: "null"}, " +
                "errorCode=${errorCode.code}, " +
                "errorCodeMessage=${errorCode.message}" +
                ")"
    }
}
