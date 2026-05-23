package io.goodmidnight.transfer.core.transfer

data class TransferState(
    val status: TransferStatus = TransferStatus.IDLE,
    val currentFileName: String = "",
    val progress: Int = 0,
    val totalFiles: Int = 0,
    val currentFileIndex: Int = 0
) {
    enum class TransferStatus {
        IDLE,
        LISTENING,
        CONNECTING,
        TRANSFERRING,
        COMPLETED,
        ERROR
    }
}
