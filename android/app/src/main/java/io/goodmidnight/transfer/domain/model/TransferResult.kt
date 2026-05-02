package io.goodmidnight.transfer.domain.model

/**
 * Represents the discrete events emitted through the data stream during a file transfer pipeline.
 * Used primarily by UseCases and Repositories to track granular transfer operations.
 */
sealed interface TransferResult {

    /**
     * Emitted continuously while a file transfer is in progress.
     *
     * @property fileName The name of the file currently being transferred.
     * @property percent The current completion percentage of the file (Range: 0 to 100).
     */
    data class Progress(val fileName: String, val percent: Int) : TransferResult

    /**
     * Emitted when a single file has been completely and successfully transferred.
     *
     * @property fileName The name of the successfully transferred file.
     */
    data class Completed(val fileName: String) : TransferResult
}