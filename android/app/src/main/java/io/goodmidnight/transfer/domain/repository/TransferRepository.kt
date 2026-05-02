package io.goodmidnight.transfer.domain.repository

import io.goodmidnight.transfer.domain.model.TransferProgress
import io.goodmidnight.transfer.domain.model.TransferResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TransferRepository {

    val transferProgressFlow: StateFlow<TransferProgress>
    fun connectToPeer(ip: String, port: Int)
    fun sendFile(absolutePath: String): Flow<TransferResult>
    fun stopTransfer()
    fun updateProgress(state: TransferProgress)
}