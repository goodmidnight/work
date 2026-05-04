package io.goodmidnight.transfer.ui.feature.transfer.home.data

import io.goodmidnight.transfer.ui.core.viewmodel.BaseEvent

sealed interface HomeEvent : BaseEvent {
    data object OnBack : HomeEvent
}
