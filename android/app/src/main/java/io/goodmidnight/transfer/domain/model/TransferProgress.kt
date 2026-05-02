package io.goodmidnight.transfer.domain.model

/**
 * Represents the global state of the overall file transfer process.
 * Designed to be observed concurrently by UI components and Foreground Services.
 *
 * @property isTransferring True if an active data transfer session is currently running.
 * @property currentFileName The name of the file currently being processed.
 * @property progress The completion percentage of the current file transfer (Range: 0 to 100).
 * @property isCompleted True if all scheduled tasks/files have been successfully transferred.
 * @property error Holds an error message if the transfer fails or is aborted. Null if no error occurred.
 */
data class TransferProgress(
    val isTransferring: Boolean = false,
    val currentFileName: String = "",
    val progress: Int = 0,
    val isCompleted: Boolean = false,
    val error: String? = null
)