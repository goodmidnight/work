package io.goodmidnight.transfer.domain.repository

import io.goodmidnight.transfer.domain.model.TransferProgress
import io.goodmidnight.transfer.domain.model.TransferResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The domain-level contract for the file transfer engine.
 */
interface TransferRepository {

    val transferProgressFlow: StateFlow<TransferProgress>

    /**
     * [Hub Mode] Opens a port and listens for incoming connections.
     * Implementation should handle the storage path internally.
     */
    fun startReceiver(port: Int): Boolean

    /**
     * [Spoke Mode] Connects to the target peer and starts sending a specific file.
     * @return A Flow of TransferResult to track the progress of this specific transfer.
     */
    fun startSender(ip: String, port: Int, filePath: String): Flow<TransferResult>

    /**
     * Gracefully stops the transfer engine.
     */
    fun stopTransfer()

    /**
     * Updates the global transfer progress state.
     */
    fun updateProgress(state: TransferProgress)
}
