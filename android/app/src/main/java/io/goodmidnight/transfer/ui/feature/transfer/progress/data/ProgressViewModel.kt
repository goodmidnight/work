package io.goodmidnight.transfer.ui.feature.transfer.progress.data

import dagger.hilt.android.lifecycle.HiltViewModel
import io.goodmidnight.transfer.ui.core.exception.AppError
import io.goodmidnight.transfer.ui.core.viewmodel.BaseViewModel
import javax.inject.Inject

@HiltViewModel
class ProgressViewModel @Inject constructor(
) : BaseViewModel<ProgressState, ProgressEvent, ProgressEffect, AppError>(ProgressState()) {


    init {
        bindEvent { event ->
            when (event) {
                ProgressEvent.OnBack -> back()
            }
        }
        bindError { error ->
            error.handleError()
            error.uiMessage?.let { emitEffect(ProgressEffect.ShowSnackBar(it)) }
        }
    }



    private suspend fun back() {
        emitEffect(ProgressEffect.PopBackStack)
    }
}
