package io.goodmidnight.transfer.core.transfer

sealed interface TransferEvent {
    /**
     * Initiates the file transfer process (Sender mode).
     */
    data class StartTransfer(
        val ip: String,
        val port: Int,
        val fileUris: List<String>
    ) : TransferEvent

    /**
     * Starts listening for incoming connections (Receiver mode).
     */
    data class StartListening(val port: Int) : TransferEvent

    /**
     * Forcefully cancels the ongoing file transfer or stops listening.
     */
    data object CancelTransfer : TransferEvent
}
