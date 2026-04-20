package io.goodmidnight.transfer.ui.feature.transfer.connection.data

import io.goodmidnight.transfer.ui.core.viewmodel.BaseEffect

sealed interface ConnectionEffect : BaseEffect {
    data class ShowSnackBar(val message: String) : ConnectionEffect
    data object PopBackStack : ConnectionEffect
}
