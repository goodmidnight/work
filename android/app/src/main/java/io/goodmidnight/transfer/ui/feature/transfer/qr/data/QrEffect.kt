package io.goodmidnight.transfer.ui.feature.transfer.qr.data

import io.goodmidnight.transfer.ui.core.viewmodel.BaseEffect

sealed interface QrEffect : BaseEffect {
    data object PopBackStack : QrEffect
    data class ShowSnackBar(val message: String) : QrEffect
    
    // QR 파싱 성공 시 SharedViewModel에 데이터를 넘겨주기 위한 이펙트
    data class ReturnScanResult(val ip: String, val ssid: String, val pw: String) : QrEffect
}