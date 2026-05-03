
package io.goodmidnight.transfer.ui.feature.permission.data

import dagger.hilt.android.lifecycle.HiltViewModel
import io.goodmidnight.transfer.ui.core.exception.AppError
import io.goodmidnight.transfer.ui.core.viewmodel.BaseViewModel
import javax.inject.Inject

@HiltViewModel
class PermissionViewModel @Inject constructor() : 
    BaseViewModel<PermissionState, PermissionEvent, PermissionEffect, AppError>(PermissionState()) {

    init {
        bindEvent { event ->
            when (event) {
                PermissionEvent.OnRequestPermissionClick -> emitEffect(PermissionEffect.LaunchPermissionRequest)
                PermissionEvent.OnSettingsClick -> emitEffect(PermissionEffect.OpenSettings)
                is PermissionEvent.OnPermissionResult -> {
                    if (event.allGranted) {
                        emitEffect(PermissionEffect.NavigateToNext)
                    }
                }
            }
        }
    }
}