package io.goodmidnight.transfer.core.transfer

sealed interface TransferEffect {
    /**
     * Emitted when all files in the queue have been successfully transferred.
     */
    data object TransferCompleted : TransferEffect
}