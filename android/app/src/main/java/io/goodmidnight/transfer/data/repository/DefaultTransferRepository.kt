package io.goodmidnight.transfer.data.repository

import io.goodmidnight.transfer.data.jni.TransferEngine
import io.goodmidnight.transfer.domain.model.TransferProgress
import io.goodmidnight.transfer.domain.model.TransferResult
import io.goodmidnight.transfer.domain.repository.TransferRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The concrete implementation of [TransferRepository] handling the actual data transfer domain.
 */
@Singleton
class DefaultTransferRepository @Inject constructor() : TransferRepository {

    // StateFlow to hold the global transfer state, observable by both Services and UI components.
    private val _transferProgressFlow = MutableStateFlow(TransferProgress())
    override val transferProgressFlow: StateFlow<TransferProgress> = _transferProgressFlow.asStateFlow()

    override suspend fun connectToPeer(ip: String, port: Int) {
        TransferEngine.connectToPeer(ip, port)
    }

    override fun sendFile(absolutePath: String): Flow<TransferResult> = callbackFlow {
        TransferEngine.setCallback { fileName, stateCode, progress, msg ->
            when (stateCode) {
                0 -> {
                    // STARTED: File transfer initiated
                    updateProgress(
                        TransferProgress(
                            isTransferring = true,
                            currentFileName = fileName,
                            progress = 0
                        )
                    )
                }
                1 -> {
                    // PROGRESS: File transfer in progress
                    val result = TransferResult.Progress(fileName, progress)
                    trySend(result) // Emit the stream downstream for the UseCase to consume

                    // Update the global state (useful for Notification / UI updates)
                    updateProgress(
                        TransferProgress(
                            isTransferring = true,
                            currentFileName = fileName,
                            progress = progress
                        )
                    )
                }
                2 -> {
                    // COMPLETED: Single file transfer successfully finished
                    trySend(TransferResult.Completed(fileName))

                    // Update the global state
                    updateProgress(
                        TransferProgress(
                            isTransferring = false,
                            isCompleted = true
                        )
                    )
                    close() // Close the flow stream for this specific file successfully
                }
                -1 -> {
                    // ERROR: An error occurred during the transfer
                    updateProgress(
                        TransferProgress(
                            isTransferring = false,
                            error = msg
                        )
                    )
                    close(Exception(msg)) // Propagate the exception to the UseCase's catch block
                }
            }
        }

        // Start the engine to initiate the actual file transfer.
        TransferEngine.sendFile(absolutePath)

        // Ensure resources are cleaned up when the Flow is closed (canceled, completed, or errored).
        awaitClose {
            // Nullify the callback to prevent memory leaks from the C++ layer pointing to dead Kotlin objects
            TransferEngine.setCallback(null)
        }
    }

    override fun updateProgress(state: TransferProgress) {
        _transferProgressFlow.value = state
    }

    override fun stopTransfer() {
        TransferEngine.stopEngine()
        updateProgress(
            TransferProgress(
                isTransferring = false,
                isCompleted = false,
                error = "Transfer cancelled by the user."
            )
        )
    }
}