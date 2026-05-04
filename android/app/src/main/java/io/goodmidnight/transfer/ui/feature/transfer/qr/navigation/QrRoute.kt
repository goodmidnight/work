package io.goodmidnight.transfer.ui.feature.transfer.qr.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.goodmidnight.transfer.ui.core.utils.LocalSnackbarHostState
import io.goodmidnight.transfer.ui.core.utils.showSnackbarImmediately
import io.goodmidnight.transfer.ui.feature.transfer.qr.data.QrEffect
import io.goodmidnight.transfer.ui.feature.transfer.qr.data.QrEvent
import io.goodmidnight.transfer.ui.feature.transfer.qr.data.QrState
import io.goodmidnight.transfer.ui.feature.transfer.qr.data.QrViewModel
import io.goodmidnight.transfer.ui.feature.transfer.qr.ui.QrScreen
import io.goodmidnight.transfer.ui.feature.transfer.shared.SharedEvent
import io.goodmidnight.transfer.ui.feature.transfer.shared.SharedViewModel
import kotlinx.coroutines.CoroutineScope
@Composable
fun QrRoute(
    modifier: Modifier = Modifier,
    popBackStack: () -> Unit,
    sharedViewModel: SharedViewModel,
    viewModel: QrViewModel = hiltViewModel(),
) {
    val state: QrState by viewModel.state.collectAsStateWithLifecycle()
    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    val snackbarHostState: SnackbarHostState = LocalSnackbarHostState.current
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onEvent(QrEvent.OnPermissionResult(isGranted))
    }

    LaunchedEffect(Unit) {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (isGranted) {
            viewModel.onEvent(QrEvent.OnPermissionResult(true))
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Observe single-shot side effects.
        viewModel.bindEffect(scope = this) { effect ->
            when (effect) {
                is QrEffect.ShowSnackBar -> snackbarHostState.showSnackbarImmediately(
                    coroutineScope,
                    effect.message
                )

                is QrEffect.PopBackStack -> popBackStack()

                is QrEffect.ReturnScanResult -> {
                    sharedViewModel.onEvent(
                        SharedEvent.OnConnectQr(
                            ip = effect.ip,
                            ssid = effect.ssid,
                            pw = effect.pw
                        )
                    )
                    popBackStack()
                }
            }
        }
    }

    BackHandler(onBack = remember { { viewModel.onEvent(QrEvent.OnBack) } })

    if (state.hasCameraPermission) {
        QrScreen(
            modifier = modifier,
            onQrScanned = remember { { viewModel.onEvent(QrEvent.OnQrScanned(it)) } },
            onBackClick = remember { { viewModel.onEvent(QrEvent.OnBack) } }
        )
    }
}