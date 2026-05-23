package io.goodmidnight.transfer.ui.feature.transfer.shared

import io.goodmidnight.transfer.ui.core.viewmodel.BaseEffect

sealed interface SharedEffect : BaseEffect {
    data class ShowSnackBar(val message: String) : SharedEffect
    data object TransferCompleted : SharedEffect
}
