package io.goodmidnight.transfer.ui.feature.transfer.room.data

import dagger.hilt.android.lifecycle.HiltViewModel
import io.goodmidnight.transfer.ui.core.exception.AppError
import io.goodmidnight.transfer.ui.core.viewmodel.BaseViewModel
import javax.inject.Inject

@HiltViewModel
class RoomViewModel @Inject constructor(
) : BaseViewModel<RoomState, RoomEvent, RoomEffect, AppError>() {


    init {
        bindEvent { event ->
            when (event) {
                RoomEvent.OnBack -> back()
            }
        }
        bindError { error ->
            error.handleError()
            error.uiMessage?.let { emitEffect(RoomEffect.ShowSnackBar(it)) }
        }
    }

    override fun createInitialState(): RoomState = RoomState()


    private suspend fun back() {
        emitEffect(RoomEffect.PopBackStack)
    }
}
