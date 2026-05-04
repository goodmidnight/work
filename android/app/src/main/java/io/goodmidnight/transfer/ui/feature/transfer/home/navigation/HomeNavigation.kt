package io.goodmidnight.transfer.ui.feature.transfer.home.navigation

import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import io.goodmidnight.transfer.ui.core.navigation.MainGraph
import io.goodmidnight.transfer.ui.core.navigation.sharedViewModel
import io.goodmidnight.transfer.ui.feature.transfer.shared.SharedViewModel

fun NavGraphBuilder.homeScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    route: String,
) {
    val popBackStack: () -> Unit = { navController.popBackStack() }
    val navigateToProgress: () -> Unit =
        { navController.navigate(MainGraph.Transfer.Progress.buildNavigationRoute()) }
    val navigateToSettings: () -> Unit =
        { navController.navigate(MainGraph.Settings.buildNavigationRoute()) }
    val navigateToQr: () -> Unit =
        { navController.navigate(MainGraph.Transfer.Qr.buildNavigationRoute()) }




    composable(
        route,
        deepLinks = listOf(
            navDeepLink { uriPattern = MainGraph.Transfer.Home.deepLinkUriPattern() }
        ),
    ) {
        val sharedViewModel = it.sharedViewModel<SharedViewModel>(navController)
        HomeRoute(
            modifier,
            popBackStack,
            navigateToProgress,
            navigateToSettings,
            navigateToQr,
            sharedViewModel
        )
    }
}
