package io.goodmidnight.transfer.ui.feature.transfer.progress.navigation

import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import io.goodmidnight.transfer.ui.core.navigation.MainGraph
import io.goodmidnight.transfer.ui.core.navigation.sharedViewModel
import io.goodmidnight.transfer.ui.feature.transfer.shared.SharedViewModel

fun NavGraphBuilder.progressScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    route: String,
) {
    val popBackStack: () -> Unit = { navController.popBackStack() }

    composable(
        route,
        deepLinks = listOf(
            navDeepLink { uriPattern = MainGraph.Transfer.Progress.deepLinkUriPattern() }
        ),
    ) {
        val sharedViewModel = it.sharedViewModel<SharedViewModel>(navController)
        ProgressRoute(modifier, popBackStack, sharedViewModel)
    }
}
