package io.goodmidnight.transfer.ui.feature.transfer.home.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.goodmidnight.transfer.core.connection.ConnectionState.ConnectionMode
import io.goodmidnight.transfer.ui.core.utils.LocalSnackbarHostState
import io.goodmidnight.transfer.ui.core.utils.showSnackbarImmediately
import io.goodmidnight.transfer.ui.feature.transfer.home.data.HomeEffect
import io.goodmidnight.transfer.ui.feature.transfer.home.data.HomeEvent
import io.goodmidnight.transfer.ui.feature.transfer.home.data.HomeState
import io.goodmidnight.transfer.ui.feature.transfer.home.data.HomeViewModel
import io.goodmidnight.transfer.ui.feature.transfer.home.ui.HomeScreen
import io.goodmidnight.transfer.ui.feature.transfer.shared.SharedEvent
import io.goodmidnight.transfer.ui.feature.transfer.shared.SharedState
import io.goodmidnight.transfer.ui.feature.transfer.shared.SharedViewModel
import kotlinx.coroutines.CoroutineScope

@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
    popBackStack: () -> Unit,
    navigateToProgress: () -> Unit,
    navigateToSettings: () -> Unit,
    navigateToQr: () -> Unit,
    sharedViewModel: SharedViewModel,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state: HomeState by viewModel.state.collectAsStateWithLifecycle()
    val sharedState: SharedState by sharedViewModel.state.collectAsStateWithLifecycle()
    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    val snackbarHostState: SnackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(sharedState.transferStatus) {
        if (sharedState.transferStatus == SharedState.TransferStatus.CONNECTING ||
            sharedState.transferStatus == SharedState.TransferStatus.TRANSFERRING
        ) {
            navigateToProgress()
        }
    }

    val multiplePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            sharedViewModel.onEvent(SharedEvent.OnSelectFiles(uris))
            sharedViewModel.onEvent(SharedEvent.OnStartDiscovery(ConnectionMode.AUTO))
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            sharedViewModel.onEvent(SharedEvent.OnSelectFiles(uris))
            sharedViewModel.onEvent(SharedEvent.OnStartDiscovery(ConnectionMode.AUTO))
        }
    }

    LaunchedEffect(Unit) {
        sharedViewModel.onEvent(SharedEvent.OnCheckPendingFiles)

        viewModel.bindEffect(scope = this) { effect ->
            when (effect) {
                is HomeEffect.ShowSnackBar -> snackbarHostState.showSnackbarImmediately(
                    coroutineScope,
                    effect.message
                )

                HomeEffect.PopBackStack -> popBackStack()
            }
        }
    }

    BackHandler(onBack = remember { { viewModel.onEvent(HomeEvent.OnBack) } })

    HomeScreen(
        modifier = modifier,
        state = state,
        sharedState = sharedState,
        onSettingsClick = navigateToSettings,
        onQrScanClick = navigateToQr,
        onSendPhotosClick = remember {
            {
                multiplePhotoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            }
        },
        onSendDocumentsClick = remember { { documentPickerLauncher.launch(arrayOf("*/*")) } },
        onReceiveClick = remember {
            {
                sharedViewModel.onEvent(
                    SharedEvent.OnStartHosting(
                        deviceName = state.deviceName,
                        mode = ConnectionMode.AUTO
                    )
                )
            }
        },
        onPeerClick = remember {
            { peer ->
                sharedViewModel.onEvent(SharedEvent.OnConnectToPeer(peer))
            }
        },
        onCancelHosting = remember {
            {
                sharedViewModel.onEvent(SharedEvent.OnStopHosting)
            }
        },
        onCancelDiscovery = remember {
            {
                sharedViewModel.onEvent(SharedEvent.OnStopDiscovery)
            }
        }
    )
}