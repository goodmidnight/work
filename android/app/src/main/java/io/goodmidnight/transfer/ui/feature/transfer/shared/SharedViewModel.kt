package io.goodmidnight.transfer.ui.feature.transfer.shared

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.goodmidnight.transfer.core.connection.ConnectionEffect
import io.goodmidnight.transfer.core.connection.ConnectionEvent.ConnectToHotspot
import io.goodmidnight.transfer.core.connection.ConnectionEvent.ConnectToPeer
import io.goodmidnight.transfer.core.connection.ConnectionEvent.Disconnect
import io.goodmidnight.transfer.core.connection.ConnectionEvent.StartDiscovery
import io.goodmidnight.transfer.core.connection.ConnectionEvent.StartHosting
import io.goodmidnight.transfer.core.connection.ConnectionEvent.StopDiscovery
import io.goodmidnight.transfer.core.connection.ConnectionEvent.StopHosting
import io.goodmidnight.transfer.core.connection.ConnectionOrchestrator
import io.goodmidnight.transfer.core.connection.ConnectionState
import io.goodmidnight.transfer.core.qr.QrGenerator
import io.goodmidnight.transfer.core.transfer.TransferController
import io.goodmidnight.transfer.core.transfer.TransferEvent
import io.goodmidnight.transfer.core.transfer.TransferState
import io.goodmidnight.transfer.core.utils.FileResolver
import io.goodmidnight.transfer.core.utils.PendingFileStorage
import io.goodmidnight.transfer.domain.usecase.GetSettingsUseCase
import io.goodmidnight.transfer.ui.core.exception.AppError
import io.goodmidnight.transfer.ui.core.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SharedViewModel @Inject constructor(
    private val orchestrator: ConnectionOrchestrator,
    private val transferController: TransferController,
    private val qrGenerator: QrGenerator,
    private val fileResolver: FileResolver,
    private val pendingFileStorage: PendingFileStorage,
    private val getSettingsUseCase: GetSettingsUseCase
) : BaseViewModel<SharedState, SharedEvent, SharedEffect, AppError>(SharedState()) {

    companion object {
        private const val SOCKET_PORT = 50001
    }

    init {
        observeOrchestratorState()
        observeOrchestratorEffect()
        observeTransferState()
        observeSettings()

        bindEvent { event ->
            when (event) {
                is SharedEvent.OnSelectFiles -> updateState { copy(selectedFiles = event.uris) }
                is SharedEvent.OnStartDiscovery -> viewModelScope.launch { orchestrator.processEvent(StartDiscovery(event.mode)) }
                is SharedEvent.OnStopDiscovery -> viewModelScope.launch { orchestrator.processEvent(StopDiscovery) }
                is SharedEvent.OnConnectToPeer -> {
                    updateState { copy(transferStatus = SharedState.TransferStatus.CONNECTING) }
                    viewModelScope.launch { orchestrator.processEvent(ConnectToPeer(event.peer)) }
                }
                is SharedEvent.OnStartHosting -> handleStartHosting(event.deviceName, event.mode)
                is SharedEvent.OnStopHosting -> handleStopHosting()
                is SharedEvent.OnCancelTransfer -> handleCancelTransfer()
                is SharedEvent.OnConnectQr -> handleConnectQr(event.ip, event.ssid, event.pw)
                SharedEvent.OnCheckPendingFiles -> handlePendingFiles()
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            getSettingsUseCase().collect { settings ->
                val location = settings.saveLocation.ifBlank { "Downloads/Transfer" }
                updateState { copy(saveLocation = location) }
            }
        }
    }

    private fun observeOrchestratorState() {
        viewModelScope.launch {
            orchestrator.state.collect { connState ->
                updateState {
                    copy(
                        isDiscovering = connState.isDiscovering,
                        isHosting = connState.isHosting,
                        discoveredPeers = connState.discoveredPeers
                    )
                }
            }
        }
    }

    private fun observeTransferState() {
        viewModelScope.launch {
            transferController.state.collect { transferState ->
                val newStatus = mapTransferStatus(transferState.status)
                
                // 수신/전송 완료 시 Effect 발생
                if (newStatus == SharedState.TransferStatus.COMPLETED && 
                    state.value.transferStatus != SharedState.TransferStatus.COMPLETED) {
                    emitEffect(SharedEffect.TransferCompleted)
                }

                updateState {
                    copy(
                        transferStatus = newStatus,
                        currentProgress = transferState.progress,
                        currentFileName = transferState.currentFileName
                    )
                }
            }
        }
    }

    private fun observeOrchestratorEffect() {
        viewModelScope.launch {
            orchestrator.effect.collect { effect ->
                when (effect) {
                    is ConnectionEffect.ConnectionEstablished -> {
                        val filePaths = state.value.selectedFiles.mapNotNull { uri ->
                            fileResolver.getAbsolutePathFromUri(uri)
                        }

                        if (filePaths.isNotEmpty()) {
                            transferController.processEvent(
                                TransferEvent.StartTransfer(effect.hostIp, effect.port, filePaths)
                            )
                        } else {
                            // 수신 측인 경우 LISTENING 상태 유지
                            updateState { copy(transferStatus = SharedState.TransferStatus.LISTENING) }
                        }
                    }
                    is ConnectionEffect.ConnectionLost -> {
                        viewModelScope.launch {
                            transferController.processEvent(TransferEvent.CancelTransfer)
                        }
                        updateState { copy(transferStatus = SharedState.TransferStatus.IDLE) }
                    }
                }
            }
        }
    }

    private fun handleStartHosting(deviceName: String, mode: ConnectionState.ConnectionMode) {
        viewModelScope.launch {
            // Hosting 시작 전 상태 초기화 및 스피너 준비
            updateState { copy(qrBitmap = null, transferStatus = SharedState.TransferStatus.LISTENING) }

            val ip = orchestrator.state.value.localIp ?: "0.0.0.0"
            val ssid = "DIRECT-Transfer-${(1000..9999).random()}"
            val pw = (10000000..99999999).random().toString()

            val qrBitmap = withContext(Dispatchers.Default) {
                qrGenerator.generate(ssid, pw, ip)
            }

            updateState { copy(qrBitmap = qrBitmap) }

            orchestrator.processEvent(
                StartHosting(
                    mode = mode,
                    deviceName = deviceName,
                    wifiIp = ip,
                    port = SOCKET_PORT,
                    ssid = ssid,
                    pw = pw
                )
            )
            transferController.processEvent(TransferEvent.StartListening(SOCKET_PORT))
        }
    }

    private fun handleStopHosting() {
        viewModelScope.launch {
            orchestrator.processEvent(StopHosting)
            transferController.processEvent(TransferEvent.CancelTransfer)
            updateState { copy(isHosting = false, qrBitmap = null, transferStatus = SharedState.TransferStatus.IDLE) }
        }
    }

    private fun handleCancelTransfer() {
        viewModelScope.launch {
            transferController.processEvent(TransferEvent.CancelTransfer)
            orchestrator.processEvent(Disconnect)
            updateState { copy(transferStatus = SharedState.TransferStatus.IDLE) }
        }
    }

    private fun handleConnectQr(ip: String, ssid: String, pw: String) {
        updateState { copy(transferStatus = SharedState.TransferStatus.CONNECTING) }
        viewModelScope.launch {
            orchestrator.processEvent(ConnectToHotspot(ssid = ssid, pw = pw, hostIp = ip))
        }
    }

    private fun handlePendingFiles() {
        viewModelScope.launch {
            val pendingUris = pendingFileStorage.loadFiles()
            if (pendingUris.isNotEmpty()) {
                updateState { copy(selectedFiles = pendingUris) }
                orchestrator.processEvent(StartDiscovery(ConnectionState.ConnectionMode.AUTO))
                pendingFileStorage.clearFiles()
            }
        }
    }

    private fun mapTransferStatus(status: TransferState.TransferStatus): SharedState.TransferStatus {
        return try {
            SharedState.TransferStatus.valueOf(status.name)
        } catch (e: Exception) {
            SharedState.TransferStatus.IDLE
        }
    }
}
