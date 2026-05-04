package io.goodmidnight.transfer.ui.feature.transfer.home.data

import io.goodmidnight.transfer.ui.core.viewmodel.BaseEffect

sealed interface HomeEffect : BaseEffect {
    data class ShowSnackBar(val message: String) : HomeEffect
    data object PopBackStack : HomeEffect
}
