package io.goodmidnight.transfer.ui.feature.transfer.qr.data

import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import io.goodmidnight.transfer.ui.core.exception.AppError
import io.goodmidnight.transfer.ui.core.viewmodel.BaseViewModel
import org.json.JSONObject
import javax.inject.Inject

/**
 * ViewModel for handling the business logic of the QR scanning process.
 * Parses scanned JSON data and manages rapid-fire scan debouncing.
 */
@HiltViewModel
class QrViewModel @Inject constructor() :
    BaseViewModel<QrState, QrEvent, QrEffect, AppError>(QrState()) {

    init {
        bindEvent { event ->
            when (event) {
                is QrEvent.OnBack -> emitEffect(QrEffect.PopBackStack)

                is QrEvent.OnPermissionResult -> {
                    updateState { copy(hasCameraPermission = event.isGranted) }
                    if (!event.isGranted) {
                        emitEffect(QrEffect.ShowSnackBar("QR 스캔을 위해 카메라 권한이 필요합니다."))
                        emitEffect(QrEffect.PopBackStack)
                    }
                }

                is QrEvent.OnQrScanned -> handleQrScanned(event.rawData)
            }
        }
    }

    /**
     * Parses the raw JSON string retrieved from the QR code.
     */
    private suspend fun handleQrScanned(rawData: String) {
        if (!state.value.isScanning) return

        try {
            val json = JSONObject(rawData)
            val ssid = json.getString("ssid")
            val pw = json.getString("pw")
            val ip = json.getString("ip")

            // Lock the scanner to prevent multiple rapid emissions from consecutive camera frames.
            updateState { copy(isScanning = false) }

            // Delegate the connection execution to the routing layer.
            emitEffect(QrEffect.ReturnScanResult(ip, ssid, pw))

        } catch (e: Exception) {
            Log.w("QrViewModel", "Invalid QR code format: $rawData; $e")
        }
    }
}