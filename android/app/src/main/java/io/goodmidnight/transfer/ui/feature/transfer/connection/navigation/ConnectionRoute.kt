package io.goodmidnight.transfer.ui.feature.transfer.connection.navigation

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
import io.goodmidnight.transfer.ui.feature.transfer.connection.data.ConnectionEffect
import io.goodmidnight.transfer.ui.feature.transfer.connection.data.ConnectionEvent
import io.goodmidnight.transfer.ui.feature.transfer.connection.data.ConnectionState
import io.goodmidnight.transfer.ui.feature.transfer.connection.data.ConnectionViewModel
import io.goodmidnight.transfer.ui.feature.transfer.connection.ui.ConnectionScreen
import io.goodmidnight.transfer.ui.feature.transfer.shared.SharedState
import io.goodmidnight.transfer.ui.feature.transfer.shared.SharedViewModel
import kotlinx.coroutines.CoroutineScope

@Composable
fun ConnectionRoute(
    modifier: Modifier = Modifier,
    popBackStack: () -> Unit,
    sharedViewModel: SharedViewModel,
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val state: ConnectionState by viewModel.state.collectAsStateWithLifecycle()
    val sharedState: SharedState by sharedViewModel.state.collectAsStateWithLifecycle()
    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    val snackbarHostState: SnackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(Unit) {
        viewModel.bindEffect(scope = this) { effect ->
            when (effect) {
                is ConnectionEffect.ShowSnackBar -> snackbarHostState.showSnackbarImmediately(
                    coroutineScope,
                    effect.message
                )

                ConnectionEffect.PopBackStack -> popBackStack()
            }
        }
    }

    BackHandler(onBack = remember { { viewModel.onEvent(ConnectionEvent.OnBack) } })

    ConnectionScreen(
        modifier = modifier,
        state = state,
    )
}