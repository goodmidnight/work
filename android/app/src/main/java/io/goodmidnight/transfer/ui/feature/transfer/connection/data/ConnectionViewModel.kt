package io.goodmidnight.transfer.ui.feature.transfer.connection.data

import dagger.hilt.android.lifecycle.HiltViewModel
import io.goodmidnight.transfer.ui.core.exception.AppError
import io.goodmidnight.transfer.ui.core.viewmodel.BaseViewModel
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
) : BaseViewModel<ConnectionState, ConnectionEvent, ConnectionEffect, AppError>() {


    init {
        bindEvent { event ->
            when (event) {
                ConnectionEvent.OnBack -> back()
            }
        }
        bindError { error ->
            error.handleError()
            error.uiMessage?.let { emitEffect(ConnectionEffect.ShowSnackBar(it)) }
        }
    }

    override fun createInitialState(): ConnectionState = ConnectionState()


    private suspend fun back() {
        emitEffect(ConnectionEffect.PopBackStack)
    }
}
