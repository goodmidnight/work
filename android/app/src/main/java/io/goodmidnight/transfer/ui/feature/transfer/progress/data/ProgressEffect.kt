package io.goodmidnight.transfer.ui.feature.transfer.progress.data

import io.goodmidnight.transfer.ui.core.viewmodel.BaseEffect

sealed interface ProgressEffect : BaseEffect {
    data class ShowSnackBar(val message: String) : ProgressEffect
    data object PopBackStack : ProgressEffect
}
