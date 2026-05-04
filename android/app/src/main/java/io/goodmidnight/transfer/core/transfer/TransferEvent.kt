package io.goodmidnight.transfer.core.transfer

sealed interface TransferEvent {
    /**
     * Initiates the file transfer process.
     *
     * @param ip The IP address of the target device (or hotspot gateway).
     * @param port The port number for socket communication.
     * @param fileUris A list of File URIs (SAF) or absolute paths to be transferred.
     */
    data class StartTransfer(
        val ip: String,
        val port: Int,
        val fileUris: List<String>
    ) : TransferEvent

    /**
     * Forcefully cancels the ongoing file transfer.
     */
    data object CancelTransfer : TransferEvent
}