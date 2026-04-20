package io.goodmidnight.transfer.ui.feature.transfer.room.navigation

import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.goodmidnight.transfer.ui.core.utils.sharedViewModel
import io.goodmidnight.transfer.ui.feature.transfer.shared.SharedViewModel

fun NavGraphBuilder.roomScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    route: String,
) {
    val popBackStack: () -> Unit = { navController.popBackStack() }

    composable(route) {
        val sharedViewModel = it.sharedViewModel<SharedViewModel>(navController)
        RoomRoute(modifier, popBackStack, sharedViewModel)
    }
}
