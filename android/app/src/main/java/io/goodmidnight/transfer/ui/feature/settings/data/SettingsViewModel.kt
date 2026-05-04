package io.goodmidnight.transfer.ui.feature.settings.data

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.goodmidnight.transfer.core.utils.FileResolver
import io.goodmidnight.transfer.domain.usecase.GetSettingsUseCase
import io.goodmidnight.transfer.domain.usecase.UpdateDeviceNameUseCase
import io.goodmidnight.transfer.domain.usecase.UpdateSaveLocationUseCase
import io.goodmidnight.transfer.ui.core.exception.AppError
import io.goodmidnight.transfer.ui.core.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The central ViewModel for the Settings screen.
 * It strictly follows the MVI architecture by consuming SettingsEvent and emitting SettingsState and SettingsEffect.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getSettingsUseCase: GetSettingsUseCase,
    private val updateDeviceNameUseCase: UpdateDeviceNameUseCase,
    private val updateSaveLocationUseCase: UpdateSaveLocationUseCase,
    private val fileResolver: FileResolver,
) : BaseViewModel<SettingsState, SettingsEvent, SettingsEffect, AppError>(SettingsState()) {

    init {
        // Start collecting DataStore values and calculating initial disk usage upon creation.
        observeSettings()
        calculateCacheSize()

        // Route incoming MVI events to their respective handler functions.
        bindEvent { event ->
            when (event) {
                is SettingsEvent.OnBack -> back()
                is SettingsEvent.OnShowChangeNameDialog -> updateState { copy(showChangeNameDialog = true) }
                is SettingsEvent.OnHideChangeNameDialog -> updateState { copy(showChangeNameDialog = false) }
                is SettingsEvent.OnClearTemporaryCache -> clearCache()
                is SettingsEvent.OnUpdateDeviceName -> updateDeviceName(event.newName)
                is SettingsEvent.OnUpdateSaveLocation -> updateSaveLocation(event.newLocation)
                is SettingsEvent.OnRequestChangeSaveLocation -> {
                    updateState { copy(showChangeLocationDialog = true) }
                }

                is SettingsEvent.OnHideDialogs -> {
                    updateState {
                        copy(
                            showChangeNameDialog = false,
                            showChangeLocationDialog = false
                        )
                    }
                }
            }
        }

        // Centralized error handling block.
        bindError { error ->
            error.handleError()
            error.uiMessage?.let { emitEffect(SettingsEffect.ShowSnackBar(it)) }
        }
    }

    /**
     * Continuously observes the DataStore via UseCase.
     * Updates the UI state automatically whenever settings change elsewhere in the app.
     */
    private fun observeSettings() {
        viewModelScope.launch {
            getSettingsUseCase().collect { settings ->
                updateState {
                    copy(
                        deviceName = settings.deviceName,
                        saveLocation = settings.saveLocation
                    )
                }
            }
        }
    }

    /**
     * Calculates the cache directory size.
     * Uses Dispatchers.IO to prevent blocking the Main (UI) thread during file system traversal.
     */
    private fun calculateCacheSize() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                fileResolver.getCacheSizeFormatted()
            }
            updateState { copy(cacheSize = size) }
        }
    }

    /**
     * Wipes all temporary `.meta` files and cached data.
     * Displays a snackbar depending on the success or failure of the I/O operation.
     */
    private fun clearCache() {
        viewModelScope.launch {
            val isSuccess = withContext(Dispatchers.IO) {
                fileResolver.clearAllCacheFiles()
            }

            if (isSuccess) {
                updateState { copy(cacheSize = "0 B") }
                emitEffect(SettingsEffect.ShowSnackBar("임시 캐시 파일이 안전하게 삭제되었습니다."))
            } else {
                emitEffect(SettingsEffect.ShowSnackBar("캐시 삭제에 실패했습니다."))
            }
        }
    }

    private fun updateDeviceName(newName: String) {
        viewModelScope.launch {
            if (newName.isNotBlank()) {
                updateDeviceNameUseCase(newName)
                updateState { copy(showChangeNameDialog = false) }
                emitEffect(SettingsEffect.ShowSnackBar("기기 이름이 변경되었습니다."))
            }
        }
    }

    private fun updateSaveLocation(newLocation: String) {
        viewModelScope.launch {
            updateSaveLocationUseCase(newLocation)
            emitEffect(SettingsEffect.ShowSnackBar("저장 위치가 변경되었습니다."))
            updateState { copy(showChangeLocationDialog = false) }
        }
    }

    private suspend fun back() {
        emitEffect(SettingsEffect.PopBackStack)
    }
}