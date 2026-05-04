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
import io.goodmidnight.transfer.core.exception.CommonException
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

    init {
        // Wire up all reactive streams from underlying controllers.
        observeOrchestratorState()
        observeOrchestratorEffect()
        observeTransferState()
        observeState()

        // Map incoming UI events to downstream controller commands.
        bindEvent { event ->
            when (event) {
                is SharedEvent.OnSelectFiles -> updateState { copy(selectedFiles = event.uris) }

                is SharedEvent.OnStartDiscovery -> orchestrator.processEvent(StartDiscovery(event.mode))
                is SharedEvent.OnStopDiscovery -> orchestrator.processEvent(StopDiscovery)
                is SharedEvent.OnConnectToPeer -> orchestrator.processEvent(ConnectToPeer(event.peer))

                is SharedEvent.OnStartHosting -> handleStartHosting(event.deviceName, event.mode)
                is SharedEvent.OnStopHosting -> orchestrator.processEvent(StopHosting)

                is SharedEvent.OnCancelTransfer -> {
                    // Halts C++ engine and tears down the socket simultaneously.
                    transferController.processEvent(TransferEvent.CancelTransfer)
                    orchestrator.processEvent(Disconnect)
                }

                is SharedEvent.OnConnectQr -> handleConnectQr(event.ip, event.ssid, event.pw)

                SharedEvent.OnCheckPendingFiles -> handlePendingFiles()
            }
        }
    }

    /**
     * Subscribes to DataStore changes.
     * NOTE: Receiving file resolution is handled natively in the Data Layer via SettingsRepository.
     */
    private fun observeState() {
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
                updateState {
                    copy(
                        transferStatus = mapTransferStatus(transferState.status),
                        currentProgress = transferState.progress,
                        currentFileName = transferState.currentFileName
                    )
                }
            }
        }
    }

    /**
     * Listens for a successful handshake from the Orchestrator.
     * Once connected, it translates Android Uris to absolute POSIX file paths
     * and kicks off the native C++ file transfer engine.
     */
    private fun observeOrchestratorEffect() {
        viewModelScope.launch {
            orchestrator.effect.collect { effect ->
                if (effect is ConnectionEffect.ConnectionEstablished) {
                    // Convert SAF Uris to absolute paths readable by the C++ engine.
                    val filePaths = state.value.selectedFiles.mapNotNull { uri ->
                        fileResolver.getAbsolutePathFromUri(uri)
                    }

                    if (filePaths.isNotEmpty()) {
                        transferController.processEvent(
                            TransferEvent.StartTransfer(effect.hostIp, effect.port, filePaths)
                        )
                    } else {
                        emitError(AppError.of(CommonException.UnknownException("전환할 수 있는 파일이 없습니다.")))
                    }
                }
            }
        }
    }

    private fun handleStartHosting(deviceName: String, mode: ConnectionState.ConnectionMode) {
        viewModelScope.launch {
            val ip = orchestrator.state.value.localIp ?: "0.0.0.0"
            val ssid = "DIRECT-Transfer-${(1000..9999).random()}"
            val pw = (10000000..99999999).random().toString()

            // Offload heavy Bitmap generation to a background thread to prevent UI freezing.
            val qrBitmap = withContext(Dispatchers.Default) {
                qrGenerator.generate(ssid, pw, ip)
            }

            updateState { copy(qrBitmap = qrBitmap) }

            orchestrator.processEvent(
                StartHosting(
                    mode = mode,
                    deviceName = deviceName,
                    wifiIp = ip,
                    port = 8080,
                    ssid = ssid,
                    pw = pw
                )
            )
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