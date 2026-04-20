package io.goodmidnight.transfer.ui.feature.settings.main.data

import io.goodmidnight.transfer.ui.core.viewmodel.BaseEffect

sealed interface SettingsEffect : BaseEffect {
    data class ShowSnackBar(val message: String) : SettingsEffect
    data object PopBackStack : SettingsEffect
}
