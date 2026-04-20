package io.goodmidnight.transfer.ui.feature.transfer.room.data

import io.goodmidnight.transfer.ui.core.viewmodel.BaseEffect

sealed interface RoomEffect : BaseEffect {
    data class ShowSnackBar(val message: String) : RoomEffect
    data object PopBackStack : RoomEffect
}
