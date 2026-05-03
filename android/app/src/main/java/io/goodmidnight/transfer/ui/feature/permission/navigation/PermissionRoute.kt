package io.goodmidnight.transfer.ui.feature.permission.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.goodmidnight.transfer.core.utils.getRequiredPermissions
import io.goodmidnight.transfer.ui.core.utils.buildSettingsIntent
import io.goodmidnight.transfer.ui.feature.permission.data.PermissionEffect
import io.goodmidnight.transfer.ui.feature.permission.data.PermissionEvent
import io.goodmidnight.transfer.ui.feature.permission.data.PermissionState
import io.goodmidnight.transfer.ui.feature.permission.data.PermissionViewModel
import io.goodmidnight.transfer.ui.feature.permission.ui.PermissionScreen

@Composable
fun PermissionRoute(
    modifier: Modifier = Modifier,
    navigateToNext: () -> Unit,
    viewModel: PermissionViewModel = hiltViewModel(),
) {
    val state: PermissionState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requiredPermissions = remember { getRequiredPermissions() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val allGranted = permissions.entries.all { it.value }
            viewModel.onEvent(PermissionEvent.OnPermissionResult(allGranted))
        }
    )

    LaunchedEffect(Unit) {
        viewModel.bindEffect(scope = this) { effect ->
            when (effect) {
                PermissionEffect.LaunchPermissionRequest -> permissionLauncher.launch(
                    requiredPermissions
                )

                PermissionEffect.OpenSettings -> context.buildSettingsIntent()
                    .also { context.startActivity(it) }

                PermissionEffect.NavigateToNext -> navigateToNext()
            }
        }
    }

    PermissionScreen(
        modifier = modifier,
        state = state,
        onRequestClick = remember { { viewModel.onEvent(PermissionEvent.OnRequestPermissionClick) } },
        onSettingsClick = remember { { viewModel.onEvent(PermissionEvent.OnSettingsClick) } }
    )
}
