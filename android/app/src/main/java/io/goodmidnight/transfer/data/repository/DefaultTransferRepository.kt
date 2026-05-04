package io.goodmidnight.transfer.data.repository

import io.goodmidnight.transfer.data.datasource.AndroidFileDataSource
import io.goodmidnight.transfer.data.datasource.SettingsDataSource
import io.goodmidnight.transfer.data.exception.DataException
import io.goodmidnight.transfer.data.jni.TransferEngine
import io.goodmidnight.transfer.domain.model.TransferProgress
import io.goodmidnight.transfer.domain.model.TransferResult
import io.goodmidnight.transfer.domain.repository.TransferRepository
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DefaultTransferRepository]
 * - The core data layer implementation that bridges the Kotlin Domain layer with the high-speed Native C++ JNI layer (TransferEngine).
 */
@Singleton
class DefaultTransferRepository @Inject constructor(
    private val settingsDataSource: SettingsDataSource,
    private val fileDataSource: AndroidFileDataSource,
) : TransferRepository {

    // Global state container for transfer progress, observable by UI and background services.
    private val _transferProgressFlow = MutableStateFlow(TransferProgress())
    override val transferProgressFlow: StateFlow<TransferProgress> =
        _transferProgressFlow.asStateFlow()

    // @Volatile ensures thread-safe, memory-visible reads across CPU cores.
    // This is strictly required because the C++ JNI callback executes on a separate ASIO worker thread
    // and cannot safely invoke suspend functions or wait for disk I/O.
    @Volatile
    private var currentSaveLocation: String = "Downloads/Transfer"

    private val repositoryScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("DefaultTransferRepository"))

    init {
        // Asynchronous Settings Caching
        // Continuously observe DataStore and update the volatile memory cache.
        // This guarantees zero-latency reads when the C++ layer requests a file path.
        repositoryScope.launch {
            settingsDataSource.settingsFlow.collect { settings ->
                currentSaveLocation = settings.saveLocation
            }
        }

        // Synchronous File Descriptor Request Callback (Triggered by Native C++)
        TransferEngine.setFdRequestCallback { fileName ->
            if (fileName.endsWith(".meta")) {
                // A. Delegate hidden resume-metadata file creation
                return@setFdRequestCallback fileDataSource.createMetaFileDescriptor(fileName)
            } else {
                // B. Delegate actual media file creation using the pre-cached relative path
                // e.g., Extract "MyTransfer" from "Downloads/MyTransfer"
                val relativePath =
                    this.currentSaveLocation.substringAfter("Downloads/").ifEmpty { "Transfer" }
                return@setFdRequestCallback fileDataSource.createTransferFileDescriptor(
                    fileName,
                    relativePath
                )
            }
        }
    }

    // ========================================================================
    // Transfer Control Commands
    // ========================================================================

    override fun startReceiver(port: Int): Boolean {
        return TransferEngine.startReceiver(port)
    }

    override fun startSender(ip: String, port: Int, sessionCount: Int) {
        TransferEngine.startSender(ip, port, sessionCount)
    }

    override fun pushFile(fileUriOrPath: String): Flow<TransferResult> = callbackFlow {
        // Transform asynchronous JNI callbacks into a cold Kotlin Flow.
        TransferEngine.setCallback { fileName, stateCode, progress, msg ->
            when (stateCode) {
                0 -> { // STARTED
                    updateProgress(
                        TransferProgress(
                            isTransferring = true,
                            currentFileName = fileName,
                            progress = 0
                        )
                    )
                }

                1 -> { // PROGRESS
                    val result = TransferResult.Progress(fileName, progress)
                    trySend(result) // Emit the chunk progress to upstream consumers (UseCases)

                    updateProgress(
                        TransferProgress(
                            isTransferring = true,
                            currentFileName = fileName,
                            progress = progress
                        )
                    )
                }

                2 -> { // COMPLETED
                    trySend(TransferResult.Completed(fileName))

                    updateProgress(
                        TransferProgress(
                            isTransferring = false,
                            isCompleted = true
                        )
                    )
                    close() // Gracefully terminate the Flow for this specific file
                }

                -1 -> { // ERROR
                    updateProgress(TransferProgress(isTransferring = false, error = msg))
                    close(DataException.TransferEngineException(message = "Engine Error: $msg"))
                }
            }
        }

        // Instruct the Native C++ engine to enqueue and transmit the file
        TransferEngine.pushFile(fileUriOrPath)

        // When the Flow is cancelled or closed, clear the JNI callback reference
        // so the C++ engine does not hold onto a dead Kotlin object.
        awaitClose {
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