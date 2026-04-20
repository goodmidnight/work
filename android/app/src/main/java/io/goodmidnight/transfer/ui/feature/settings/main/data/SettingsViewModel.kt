package io.goodmidnight.transfer.ui.feature.settings.main.data

import dagger.hilt.android.lifecycle.HiltViewModel
import io.goodmidnight.transfer.ui.core.exception.AppError
import io.goodmidnight.transfer.ui.core.viewmodel.BaseViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
) : BaseViewModel<SettingsState, SettingsEvent, SettingsEffect, AppError>() {


    init {
        bindEvent { event ->
            when (event) {
                SettingsEvent.OnBack -> back()
            }
        }
        bindError { error ->
            error.handleError()
            error.uiMessage?.let { emitEffect(SettingsEffect.ShowSnackBar(it)) }
        }
    }

    override fun createInitialState(): SettingsState = SettingsState()


    /**
     * [back]
     * - 뒤로가기
     */
    private suspend fun back() {
        emitEffect(SettingsEffect.PopBackStack)
    }
}
