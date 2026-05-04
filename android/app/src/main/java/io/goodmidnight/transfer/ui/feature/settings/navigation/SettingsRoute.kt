package io.goodmidnight.transfer.ui.feature.settings.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.goodmidnight.transfer.ui.feature.settings.data.SettingsEffect
import io.goodmidnight.transfer.ui.feature.settings.data.SettingsEvent
import io.goodmidnight.transfer.ui.feature.settings.data.SettingsState
import io.goodmidnight.transfer.ui.feature.settings.data.SettingsViewModel
import io.goodmidnight.transfer.ui.core.utils.LocalSnackbarHostState
import io.goodmidnight.transfer.ui.core.utils.showSnackbarImmediately
import io.goodmidnight.transfer.ui.feature.settings.ui.SettingsScreen
import kotlinx.coroutines.CoroutineScope

@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    popBackStack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state: SettingsState by viewModel.state.collectAsStateWithLifecycle()
    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    val snackbarHostState: SnackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(Unit) {
        viewModel.bindEffect(scope = this) { effect ->
            when (effect) {
                is SettingsEffect.ShowSnackBar -> snackbarHostState.showSnackbarImmediately(
                    coroutineScope,
                    effect.message
                )

                is SettingsEffect.PopBackStack -> popBackStack()
            }
        }
    }

    BackHandler(onBack = remember { { viewModel.onEvent(SettingsEvent.OnBack) } })

    SettingsScreen(
        modifier = modifier,
        state = state,
        onBackClick = remember {
            {
                viewModel.onEvent(SettingsEvent.OnBack)
            }
        },
        onChangeDeviceNameClick = remember {
            {
                viewModel.onEvent(SettingsEvent.OnShowChangeNameDialog)
            }
        },
        onChangeSaveLocationClick = remember {
            {
                viewModel.onEvent(SettingsEvent.OnRequestChangeSaveLocation)
            }
        },
        onClearCacheClick = remember {
            {
                viewModel.onEvent(SettingsEvent.OnClearTemporaryCache)
            }
        },
        onDismissDialogs = remember {
            {
                viewModel.onEvent(SettingsEvent.OnHideDialogs)
            }
        },
        onConfirmDeviceName = remember {
            { newName ->
                viewModel.onEvent(SettingsEvent.OnUpdateDeviceName(newName))
            }
        },
        onConfirmSaveLocation = remember {
            { newLocation ->
                viewModel.onEvent(SettingsEvent.OnUpdateSaveLocation(newLocation))
            }
        }
    )
}