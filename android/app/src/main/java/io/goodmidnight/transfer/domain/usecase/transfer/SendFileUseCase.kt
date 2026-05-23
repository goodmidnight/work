package io.goodmidnight.transfer.domain.usecase.transfer

import io.goodmidnight.transfer.domain.model.TransferResult
import io.goodmidnight.transfer.domain.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * UseCase for orchestrating the transfer of files.
 * In the updated engine, each file transfer initiates its own session 
 * via the unified startSender method which handles both connection and transmission.
 */
class SendFileUseCase @Inject constructor(
    private val repository: TransferRepository
) {
    /**
     * @param ip Target IP address
     * @param port Target TCP port
     * @param fileUris List of File URIs (from MediaStore/SAF) or absolute paths
     */
    operator fun invoke(ip: String, port: Int, fileUris: List<String>): Flow<TransferResult> =
        flow {
            // Iterate through the requested files and transmit them sequentially.
            // The repository.startSender now combines connection and transmission into one flow.
            for (uri in fileUris) {
                // emitAll forwards the real-time TransferResult (Progress, Completed) to the UI layer.
                emitAll(repository.startSender(ip = ip, port = port, filePath = uri))
            }
        }
}
