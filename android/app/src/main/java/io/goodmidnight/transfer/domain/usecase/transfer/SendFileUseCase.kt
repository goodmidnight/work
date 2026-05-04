package io.goodmidnight.transfer.domain.usecase.transfer

import io.goodmidnight.transfer.domain.model.TransferResult
import io.goodmidnight.transfer.domain.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * UseCase for orchestrating the transfer of files.
 * It establishes a high-speed P2P connection first (opening parallel pipes),
 * then sequentially delegates the transfer of each file to the C++ engine.
 *
 * This acts as a convenient wrapper, combining the 'connect' and 'push' operations.
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
            // Step 1: Establish the parallel TCP socket connections with the target device.
            // We use 4 parallel sessions by default to maximize bandwidth.
            repository.startSender(ip = ip, port = port, sessionCount = 4)

            // Step 2: Iterate through the requested files and transmit them sequentially.
            // The C++ engine handles the chunking and parallelization automatically.
            for (uri in fileUris) {
                // emitAll forwards the real-time TransferResult (Progress, Completed) to the ViewModel.
                emitAll(repository.pushFile(uri))
            }
        }
}