package io.goodmidnight.transfer.ui.feature.transfer.home.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.goodmidnight.transfer.domain.usecase.GetSettingsUseCase
import io.goodmidnight.transfer.ui.core.exception.AppError
import io.goodmidnight.transfer.ui.core.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getSettingsUseCase: GetSettingsUseCase,
) : BaseViewModel<HomeState, HomeEvent, HomeEffect, AppError>(HomeState()) {


    init {
        bindEvent { event ->
            when (event) {
                HomeEvent.OnBack -> back()
            }
        }
        bindError { error ->
            error.handleError()
            error.uiMessage?.let { emitEffect(HomeEffect.ShowSnackBar(it)) }
        }

        observeState()
    }


    private suspend fun back() {
        emitEffect(HomeEffect.PopBackStack)
    }

    private fun observeState() {
        viewModelScope.launch {
            val systemDeviceName = withContext(Dispatchers.IO) {
                Settings.Global.getString(
                    context.contentResolver,
                    Settings.Global.DEVICE_NAME
                ) ?: Build.MODEL
            }

            getSettingsUseCase().collect { settings ->
                val finalDeviceName = settings.deviceName.ifBlank { systemDeviceName }
                updateState { copy(deviceName = finalDeviceName) }
            }
        }
    }
}
