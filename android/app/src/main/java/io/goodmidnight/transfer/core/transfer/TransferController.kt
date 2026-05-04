package io.goodmidnight.transfer.core.transfer

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.goodmidnight.transfer.core.base.BaseController
import io.goodmidnight.transfer.domain.model.TransferResult
import io.goodmidnight.transfer.domain.usecase.transfer.CancelTransferUseCase
import io.goodmidnight.transfer.domain.usecase.transfer.SendFileUseCase // [수정됨] Import 변경
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sendFileUseCase: SendFileUseCase,
    private val cancelTransferUseCase: CancelTransferUseCase,
) : BaseController<TransferState, TransferEvent, TransferEffect, Exception>(
    "TransferController", TransferState()
) {
    private var transferJob: Job? = null

    override suspend fun handleEvent(event: TransferEvent) {
        when (event) {
            is TransferEvent.StartTransfer -> handleStartTransfer(event)
            is TransferEvent.CancelTransfer -> handleCancelTransfer()
        }
    }

    private fun handleStartTransfer(event: TransferEvent.StartTransfer) {
        transferJob?.cancel()

        updateState {
            copy(
                status = TransferState.TransferStatus.CONNECTING,
                totalFiles = event.fileUris.size,
                currentFileIndex = 0,
                progress = 0
            )
        }

        runCatching {
            startForegroundService()
        }.onFailure { e ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                // Expected exception: Occurs when trying to start a foreground service from the background (Android 12+).
                // Update state to ERROR to notify the user gracefully instead of crashing the app.
                updateState { copy(status = TransferState.TransferStatus.ERROR) }
                emitError(Exception(e))
                return
            } else throw e
        }

        transferJob = controllerScope.launch {
            runCatching {
                sendFileUseCase(event.ip, event.port, event.fileUris)
                    .collect { result ->
                        when (result) {
                            is TransferResult.Progress -> {
                                updateState {
                                    copy(
                                        status = TransferState.TransferStatus.TRANSFERRING,
                                        currentFileName = result.fileName,
                                        progress = result.percent
                                    )
                                }
                            }
                            is TransferResult.Completed -> {
                                // Increment the file index and ensure progress is 100% for the completed file
                                updateState {
                                    copy(
                                        currentFileIndex = currentFileIndex + 1,
                                        progress = 100
                                    )
                                }
                            }
                        }
                    }

                // If collect completes normally, all files are successfully transferred
                updateState { copy(status = TransferState.TransferStatus.COMPLETED) }
                emitEffect(TransferEffect.TransferCompleted)
                stopForegroundService()

            }.onFailure { e ->
                if (e is CancellationException) throw e

                updateState { copy(status = TransferState.TransferStatus.ERROR) }
                emitError(e as? Exception ?: Exception(e))
                stopForegroundService()
            }
        }
    }

    private fun handleCancelTransfer() {
        cancelTransferUseCase()
        transferJob?.cancel()

        updateState { copy(status = TransferState.TransferStatus.IDLE, progress = 0) }
        stopForegroundService()
    }

    private fun startForegroundService() {
        val intent = Intent(context, TransferService::class.java).apply {
            action = TransferService.ACTION_START_FOREGROUND
        }
        ContextCompat.startForegroundService(context, intent)
    }

    private fun stopForegroundService() {
        val intent = Intent(context, TransferService::class.java).apply {
            action = TransferService.ACTION_STOP_FOREGROUND
        }
        context.startService(intent)
    }
}