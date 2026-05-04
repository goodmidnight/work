package io.goodmidnight.transfer.ui.feature.transfer.progress.navigation

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
import io.goodmidnight.transfer.ui.core.utils.LocalSnackbarHostState
import io.goodmidnight.transfer.ui.core.utils.showSnackbarImmediately
import io.goodmidnight.transfer.ui.feature.transfer.progress.data.ProgressEffect
import io.goodmidnight.transfer.ui.feature.transfer.progress.data.ProgressState
import io.goodmidnight.transfer.ui.feature.transfer.progress.data.ProgressViewModel
import io.goodmidnight.transfer.ui.feature.transfer.progress.ui.ProgressScreen
import io.goodmidnight.transfer.ui.feature.transfer.shared.SharedEvent
import io.goodmidnight.transfer.ui.feature.transfer.shared.SharedState
import io.goodmidnight.transfer.ui.feature.transfer.shared.SharedViewModel
import kotlinx.coroutines.CoroutineScope

/**
 * Route level component for the Progress screen.
 * Orchestrates navigation based on the global transfer state and handles system back press logic.
 */
@Composable
fun ProgressRoute(
    modifier: Modifier = Modifier,
    popBackStack: () -> Unit,
    sharedViewModel: SharedViewModel,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state: ProgressState by viewModel.state.collectAsStateWithLifecycle()
    val sharedState: SharedState by sharedViewModel.state.collectAsStateWithLifecycle()
    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    val snackbarHostState: SnackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(Unit) {
        viewModel.bindEffect(scope = this) { effect ->
            when (effect) {
                is ProgressEffect.ShowSnackBar -> snackbarHostState.showSnackbarImmediately(
                    coroutineScope,
                    effect.message
                )
                ProgressEffect.PopBackStack -> popBackStack()
            }
        }
    }

    // Automatically dismiss the progress screen if the global state resets to IDLE
    LaunchedEffect(sharedState.transferStatus) {
        if (sharedState.transferStatus == SharedState.TransferStatus.IDLE) {
            popBackStack()
        }
    }

    // Intercepts the hardware back button to prevent accidental exits during an active transfer.
    BackHandler(onBack = remember {
        {
            if (sharedState.transferStatus == SharedState.TransferStatus.TRANSFERRING) {
                sharedViewModel.onEvent(SharedEvent.OnCancelTransfer)
            } else {
                popBackStack()
            }
        }
    })

    ProgressScreen(
        modifier = modifier,
        state = state,
        sharedState = sharedState,
        onCancelClick = remember {
            {
                sharedViewModel.onEvent(SharedEvent.OnCancelTransfer)
            }
        },
        onDoneClick = remember {
            {
                sharedViewModel.onEvent(SharedEvent.OnCancelTransfer)
            }
        }
    )
}