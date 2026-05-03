package io.goodmidnight.transfer.ui.core.component

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.goodmidnight.transfer.core.utils.getRequiredPermissions
import io.goodmidnight.transfer.designsystem.component.Snackbar
import io.goodmidnight.transfer.designsystem.theme.Theme
import io.goodmidnight.transfer.ui.core.navigation.MainGraph
import io.goodmidnight.transfer.ui.core.utils.LocalSnackbarHostState

@Composable
fun MainApp(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val requiredPermissions = remember { getRequiredPermissions() }

    // 권한 검증 헬퍼 함수
    fun checkPermissions() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    // 중복되던 상태 변수를 단일 변수로 통합
    var hasAllPermissions by remember { mutableStateOf(checkPermissions()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { hasAllPermissions = checkPermissions() }
    )

    // 앱 진입 시 권한이 없다면 즉시 시스템 권한 요청
    LaunchedEffect(Unit) {
        if (!hasAllPermissions) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    // 설정 앱을 통해 권한을 허용하고 돌아오는 경우를 감지하여 처리
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentlyGranted = checkPermissions()
                hasAllPermissions = currentlyGranted

                // [핵심 해결책] currentDestination이 null이면 앱 최초 실행 시점이므로 네비게이션 동작을 스킵합니다.
                // 최초 실행 라우팅은 NavHost의 startDestination이 안전하게 처리합니다.
                val currentDestination = navController.currentDestination

                if (currentDestination != null) {
                    val currentRoute = currentDestination.route

                    if (currentlyGranted) {
                        // 권한이 허용되었고, 현재 권한 화면이라면 홈으로 이동
                        if (currentRoute == MainGraph.Permission.route) {
                            navController.navigate(MainGraph.Transfer.route) {
                                popUpTo(MainGraph.Permission.route) { inclusive = true }
                            }
                        }
                    } else {
                        // 권한이 없는데, 현재 권한 화면이 아니라면 권한 화면으로 강제 리다이렉트
                        if (currentRoute != MainGraph.Permission.route) {
                            navController.navigate(MainGraph.Permission.route) {
                                popUpTo(0) { inclusive = true } // 백스택 완전 초기화
                            }
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val startDestination = remember(hasAllPermissions) {
        if (hasAllPermissions) MainGraph.Transfer.route else MainGraph.Permission.route
    }

    CompositionLocalProvider(
        LocalSnackbarHostState provides snackbarHostState,
    ) {
        Scaffold(
            modifier = modifier,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { Snackbar(snackbarHostState) },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Theme.colorScheme.background)
                    .padding(paddingValues)
            ) {
                NavHost(
                    modifier = modifier,
                    startDestination = startDestination,
                    navController = navController
                )
            }
        }
    }
}