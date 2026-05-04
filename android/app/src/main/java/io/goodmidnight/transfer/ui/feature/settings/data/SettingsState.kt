package io.goodmidnight.transfer.ui.feature.settings.data

import androidx.compose.runtime.Immutable
import io.goodmidnight.transfer.ui.core.viewmodel.BaseState
import io.goodmidnight.transfer.ui.core.viewmodel.ScreenState
import io.goodmidnight.transfer.ui.core.viewmodel.UiState

@Immutable
data class SettingsState(
    override val uiState: UiState = UiState(),
    override val screenState: ScreenState = ScreenState.INITIAL,

    // The name broadcasted to other devices during discovery.
    val deviceName: String = "Device",

    // The relative or absolute path where received files will be stored.
    val saveLocation: String = "Downloads/Transfer",

    // Human-readable string representing the total size of temporary files (e.g., "1.2 MB").
    val cacheSize: String = "0 MB",

    // Controls the visibility of the device name modification dialog.
    val showChangeNameDialog: Boolean = false,
    val showChangeLocationDialog: Boolean = false
) : BaseState