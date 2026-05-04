package io.goodmidnight.transfer.ui.feature.transfer.qr.data

import io.goodmidnight.transfer.ui.core.viewmodel.BaseEvent

sealed interface QrEvent : BaseEvent {
    data object OnBack : QrEvent
    
    // 권한 요청 결과
    data class OnPermissionResult(val isGranted: Boolean) : QrEvent
    
    // 카메라 분석기에서 QR 코드를 인식했을 때
    data class OnQrScanned(val rawData: String) : QrEvent
}