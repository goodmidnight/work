package io.goodmidnight.transfer.ui.feature.transfer.room.navigation

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
import io.goodmidnight.transfer.ui.feature.transfer.room.data.RoomEffect
import io.goodmidnight.transfer.ui.feature.transfer.room.data.RoomEvent
import io.goodmidnight.transfer.ui.feature.transfer.room.data.RoomState
import io.goodmidnight.transfer.ui.feature.transfer.room.data.RoomViewModel
import io.goodmidnight.transfer.ui.feature.transfer.room.ui.RoomScreen
import io.goodmidnight.transfer.ui.feature.transfer.shared.SharedState
import io.goodmidnight.transfer.ui.feature.transfer.shared.SharedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun RoomRoute(
    modifier: Modifier = Modifier,
    popBackStack: () -> Unit,
    sharedViewModel: SharedViewModel,
    viewModel: RoomViewModel = hiltViewModel()
) {
    val state: RoomState by viewModel.state.collectAsStateWithLifecycle()
    val sharedState: SharedState by sharedViewModel.state.collectAsStateWithLifecycle()
    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    val snackbarHostState: SnackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(Unit) {
        viewModel.bindEffect(scope = this) { effect ->
            when (effect) {
                is RoomEffect.ShowSnackBar -> snackbarHostState.showSnackbarImmediately(
                    coroutineScope,
                    effect.message
                )

                RoomEffect.PopBackStack ->
                    popBackStack()
            }
        }
    }

    BackHandler(onBack = remember { { viewModel.onEvent(RoomEvent.OnBack) } })

    RoomScreen(
        modifier = modifier,
        state = state,
    )
}