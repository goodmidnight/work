package io.goodmidnight.transfer.domain.usecase.transfer

import io.goodmidnight.transfer.domain.repository.TransferRepository
import javax.inject.Inject

/**
 * UseCase for canceling an ongoing file transfer.
 * Instructs the repository to stop the underlying C++ engine and safely close all TCP sockets.
 */
class CancelTransferUseCase @Inject constructor(
    private val repository: TransferRepository
) {
    operator fun invoke() {
        repository.stopTransfer()
    }
}