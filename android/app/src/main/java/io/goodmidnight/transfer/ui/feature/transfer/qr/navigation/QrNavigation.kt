package io.goodmidnight.transfer.ui.feature.transfer.qr.navigation

import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.goodmidnight.transfer.ui.core.navigation.sharedViewModel
import io.goodmidnight.transfer.ui.feature.transfer.shared.SharedViewModel

fun NavGraphBuilder.qrScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    route: String,
) {
    val popBackStack: () -> Unit = { navController.popBackStack() }

    composable(route) {
        val sharedViewModel = it.sharedViewModel<SharedViewModel>(navController)
        QrRoute(modifier, popBackStack, sharedViewModel)
    }
}
