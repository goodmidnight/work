package io.goodmidnight.transfer.ui.feature.transfer.shared

import dagger.hilt.android.lifecycle.HiltViewModel
import io.goodmidnight.transfer.ui.core.exception.AppError
import io.goodmidnight.transfer.ui.core.viewmodel.BaseViewModel
import javax.inject.Inject

@HiltViewModel
class SharedViewModel @Inject constructor(
) : BaseViewModel<SharedState, SharedEvent, SharedEffect, AppError>() {


    init {
        bindEvent { event ->
        }
        bindError { error ->
            error.handleError()
            error.uiMessage?.let { emitEffect(SharedEffect.ShowSnackBar(it)) }
        }
    }

    override fun createInitialState(): SharedState = SharedState()
}
