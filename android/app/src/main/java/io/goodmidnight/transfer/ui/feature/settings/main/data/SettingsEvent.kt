package io.goodmidnight.transfer.ui.feature.settings.main.data

import io.goodmidnight.transfer.ui.core.viewmodel.BaseEvent

sealed interface SettingsEvent : BaseEvent {
    data object OnBack : SettingsEvent
}
