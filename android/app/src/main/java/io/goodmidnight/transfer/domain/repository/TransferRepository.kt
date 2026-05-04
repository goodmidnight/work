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
     */
    fun startReceiver(port: Int): Boolean

    /**
     * [Spoke Mode] Connects to the target peer with multiple parallel pipes.
     */
    fun startSender(ip: String, port: Int, sessionCount: Int = 4)

    /**
     * Enqueues a file (by its absolute path or content URI) for transmission.
     */
    fun pushFile(fileUriOrPath: String): Flow<TransferResult>

    fun stopTransfer()

    fun updateProgress(state: TransferProgress)
}