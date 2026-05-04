package io.goodmidnight.transfer.ui.feature.settings.data

import io.goodmidnight.transfer.ui.core.viewmodel.BaseEvent

sealed interface SettingsEvent : BaseEvent {
    data object OnBack : SettingsEvent
    data object OnShowChangeNameDialog : SettingsEvent
    data object OnHideChangeNameDialog : SettingsEvent
    data object OnRequestChangeSaveLocation : SettingsEvent
    data object OnClearTemporaryCache : SettingsEvent
    data class OnUpdateDeviceName(val newName: String) : SettingsEvent
    data class OnUpdateSaveLocation(val newLocation: String) : SettingsEvent
    data object OnHideDialogs : SettingsEvent
}