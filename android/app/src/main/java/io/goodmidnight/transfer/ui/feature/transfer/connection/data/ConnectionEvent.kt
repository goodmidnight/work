package io.goodmidnight.transfer.ui.feature.transfer.connection.data

import io.goodmidnight.transfer.ui.core.viewmodel.BaseEvent

sealed interface ConnectionEvent : BaseEvent {
    data object OnBack : ConnectionEvent
}
