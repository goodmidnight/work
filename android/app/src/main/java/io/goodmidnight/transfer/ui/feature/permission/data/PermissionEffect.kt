package io.goodmidnight.transfer.ui.feature.permission.data

import io.goodmidnight.transfer.ui.core.viewmodel.BaseEffect

sealed interface PermissionEffect : BaseEffect {
    data object LaunchPermissionRequest : PermissionEffect
    data object OpenSettings : PermissionEffect
    data object NavigateToNext : PermissionEffect
}