package io.goodmidnight.transfer.core.exception

/**
 * Interface defining the structure of an error code.
 * Used to provide a consistent error reporting mechanism across the application.
 */
interface ErrorCode {
    /**
     * A unique string identifier for the error (e.g., "D001").
     */
    val code: String

    /**
     * An optional human-readable message describing the error.
     */
    val message: String?
}
