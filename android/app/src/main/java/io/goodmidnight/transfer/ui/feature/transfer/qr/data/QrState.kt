package io.goodmidnight.transfer.ui.feature.transfer.qr.data

import androidx.compose.runtime.Immutable
import io.goodmidnight.transfer.ui.core.viewmodel.BaseState
import io.goodmidnight.transfer.ui.core.viewmodel.ScreenState
import io.goodmidnight.transfer.ui.core.viewmodel.UiState

@Immutable
data class QrState(
    override val uiState: UiState = UiState(),
    override val screenState: ScreenState = ScreenState.INITIAL,
    val hasCameraPermission: Boolean = false,
    val isScanning: Boolean = true
) : BaseState