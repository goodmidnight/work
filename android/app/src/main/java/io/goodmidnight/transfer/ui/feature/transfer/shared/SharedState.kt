package io.goodmidnight.transfer.ui.feature.transfer.shared

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Immutable
import io.goodmidnight.transfer.core.connection.model.Peer
import io.goodmidnight.transfer.ui.core.viewmodel.BaseState
import io.goodmidnight.transfer.ui.core.viewmodel.ScreenState
import io.goodmidnight.transfer.ui.core.viewmodel.UiState

@Immutable
data class SharedState(
    override val uiState: UiState = UiState(),
    override val screenState: ScreenState = ScreenState.INITIAL,
    val selectedFiles: List<Uri> = emptyList(),
    val discoveredPeers: List<Peer> = emptyList(),
    val isDiscovering: Boolean = false,
    val isHosting: Boolean = false,
    val qrBitmap: Bitmap? = null,
    val transferStatus: TransferStatus = TransferStatus.IDLE,
    val currentProgress: Int = 0,
    val currentFileName: String = "",
    val saveLocation: String = ""
) : BaseState {
    enum class TransferStatus {
        IDLE,
        LISTENING,
        CONNECTING,
        TRANSFERRING,
        COMPLETED,
        ERROR
    }
}
