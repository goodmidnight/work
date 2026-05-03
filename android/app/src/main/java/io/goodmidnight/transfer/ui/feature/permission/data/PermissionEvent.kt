package io.goodmidnight.transfer.ui.feature.permission.data

import io.goodmidnight.transfer.ui.core.viewmodel.BaseEvent

sealed interface PermissionEvent : BaseEvent {
    data object OnRequestPermissionClick : PermissionEvent
    data object OnSettingsClick : PermissionEvent
    data class OnPermissionResult(val allGranted: Boolean) : PermissionEvent
}