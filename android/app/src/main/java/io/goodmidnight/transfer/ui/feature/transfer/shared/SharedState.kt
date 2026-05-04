package io.goodmidnight.transfer.ui.feature.transfer.shared

import androidx.compose.runtime.Immutable
import io.goodmidnight.transfer.core.connection.model.Peer
import io.goodmidnight.transfer.ui.core.viewmodel.BaseState
import io.goodmidnight.transfer.ui.core.viewmodel.ScreenState
import io.goodmidnight.transfer.ui.core.viewmodel.UiState
import android.graphics.Bitmap
import android.net.Uri
@Immutable
data class SharedState(
    override val uiState: UiState = UiState(),
    override val screenState: ScreenState = ScreenState.INITIAL,

    // Connection Phase States
    val isDiscovering: Boolean = false,
    val isHosting: Boolean = false,
    val discoveredPeers: List<Peer> = emptyList(),

    // Transfer Phase States
    val transferStatus: TransferStatus = TransferStatus.IDLE,
    val currentProgress: Int = 0,
    val currentFileName: String = "",

    // UI Assets & Metadata
    val qrBitmap: Bitmap? = null,
    val selectedFiles: List<Uri> = emptyList(),
    val saveLocation: String = ""
) : BaseState {

    /**
     * High-level abstraction of the native C++ engine's state machine.
     */
    enum class TransferStatus {
        IDLE, CONNECTING, TRANSFERRING, COMPLETED, ERROR
    }
}