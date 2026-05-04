package io.goodmidnight.transfer.domain.usecase.transfer

import io.goodmidnight.transfer.domain.repository.TransferRepository
import javax.inject.Inject

/**
 * UseCase for initializing the Hub (Receiver) mode.
 * Instructs the repository to open a specific port and listen for incoming connections.
 */
class StartReceiverUseCase @Inject constructor(
    private val repository: TransferRepository
) {
    /**
     * @param port The port to bind to (e.g., 50000)
     * @return true if successfully started, false if port is blocked or in use.
     */
    operator fun invoke(port: Int): Boolean {
        return repository.startReceiver(port)
    }
}