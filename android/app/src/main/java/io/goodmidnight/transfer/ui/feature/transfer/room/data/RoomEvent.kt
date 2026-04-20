package io.goodmidnight.transfer.ui.feature.transfer.room.data

import io.goodmidnight.transfer.ui.core.viewmodel.BaseEvent

sealed interface RoomEvent : BaseEvent {
    data object OnBack : RoomEvent
}
