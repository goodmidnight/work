package io.goodmidnight.transfer.core.transfer

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.goodmidnight.transfer.core.base.BaseController
import io.goodmidnight.transfer.domain.model.TransferResult
import io.goodmidnight.transfer.domain.usecase.transfer.CancelTransferUseCase
import io.goodmidnight.transfer.domain.usecase.transfer.SendFileUseCase
import io.goodmidnight.transfer.domain.usecase.transfer.StartReceiverUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sendFileUseCase: SendFileUseCase,
    private val startReceiverUseCase: StartReceiverUseCase,
    private val cancelTransferUseCase: CancelTransferUseCase,
) : BaseController<TransferState, TransferEvent, TransferEffect, Exception>(
    "TransferController", TransferState()
) {
    private var transferJob: Job? = null

    override suspend fun handleEvent(event: TransferEvent) {
        when (event) {
            is TransferEvent.StartTransfer -> handleStartTransfer(event)
            is TransferEvent.StartListening -> handleStartListening(event.port)
            is TransferEvent.CancelTransfer -> handleCancelTransfer()
        }
    }

    private fun handleStartTransfer(event: TransferEvent.StartTransfer) {
        Log.i(TAG, "Starting transfer to ${event.ip}:${event.port}")
        transferJob?.cancel()

        updateState {
            copy(
                status = TransferState.TransferStatus.CONNECTING,
                totalFiles = event.fileUris.size,
                currentFileIndex = 0,
                progress = 0
            )
        }

        ensureForegroundService()

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
                                updateState {
                                    copy(
                                        currentFileIndex = currentFileIndex + 1,
                                        progress = 100
                                    )
                                }
                            }
                        }
                    }

                updateState { copy(status = TransferState.TransferStatus.COMPLETED) }
                emitEffect(TransferEffect.TransferCompleted)
                stopForegroundService()

            }.onFailure { e ->
                if (e is CancellationException) throw e
                Log.e(TAG, "Transfer failed", e)
                updateState { copy(status = TransferState.TransferStatus.ERROR) }
                emitError(e as? Exception ?: Exception(e))
                stopForegroundService()
            }
        }
    }

    private fun handleStartListening(port: Int) {
        Log.i(TAG, "Starting receiver on port $port")
        transferJob?.cancel()
        
        updateState {
            copy(
                status = TransferState.TransferStatus.LISTENING,
                progress = 0
            )
        }

        ensureForegroundService()

        transferJob = controllerScope.launch {
            runCatching {
                val success = startReceiverUseCase(port)
                if (!success) {
                    throw Exception("Failed to start receiver on port $port")
                }
            }.onFailure { e ->
                Log.e(TAG, "Receiver failed", e)
                updateState { copy(status = TransferState.TransferStatus.ERROR) }
                emitError(e as? Exception ?: Exception(e))
                stopForegroundService()
            }
        }
    }

    private fun handleCancelTransfer() {
        Log.i(TAG, "Cancelling transfer/listening")
        cancelTransferUseCase()
        transferJob?.cancel()

        updateState { copy(status = TransferState.TransferStatus.IDLE, progress = 0) }
        stopForegroundService()
    }

    private fun ensureForegroundService() {
        runCatching {
            startForegroundService()
        }.onFailure { e ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "Foreground service start not allowed", e)
                updateState { copy(status = TransferState.TransferStatus.ERROR) }
                emitError(Exception(e))
            } else {
                Log.e(TAG, "Failed to start foreground service", e)
                throw e
            }
        }
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

    companion object {
        private const val TAG = "TransferController"
    }
}
