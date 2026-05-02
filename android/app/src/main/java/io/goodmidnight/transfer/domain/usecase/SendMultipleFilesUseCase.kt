package io.goodmidnight.transfer.domain.usecase

import io.goodmidnight.transfer.domain.repository.TransferRepository
import io.goodmidnight.transfer.domain.model.TransferResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * UseCase for orchestrating the transfer of multiple files sequentially.
 * Establishes a P2P connection first, then delegates the transfer of each file to the repository.
 */
class SendMultipleFilesUseCase @Inject constructor(
    private val repository: TransferRepository
) {
    operator fun invoke(ip: String, port: Int, filePaths: List<String>): Flow<TransferResult> = flow {
        // Establish the socket connection with the target device
        repository.connectToPeer(ip, port)

        // Iterate through the requested files and transmit them sequentially
        for (path in filePaths) {
            emitAll(repository.sendFile(path))
        }
    }
}