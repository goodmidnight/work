package io.goodmidnight.transfer.ui.feature.settings.data

import io.goodmidnight.transfer.ui.core.viewmodel.BaseEffect

sealed interface SettingsEffect : BaseEffect {
    // Displays a temporary snackbar message to the user.
    data class ShowSnackBar(val message: String) : SettingsEffect

    // Navigates back to the previous screen.
    data object PopBackStack : SettingsEffect
}