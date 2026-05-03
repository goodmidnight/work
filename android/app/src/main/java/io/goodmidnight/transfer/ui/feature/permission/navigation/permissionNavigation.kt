package io.goodmidnight.transfer.ui.feature.permission.navigation

import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.goodmidnight.transfer.ui.core.navigation.MainGraph

fun NavGraphBuilder.permissionScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    route: String,
) {
    val navigateToNext: () -> Unit = {
        navController.navigate(MainGraph.Transfer.route) {
            popUpTo(MainGraph.Permission.route) { inclusive = true }
        }
    }

    composable(route) {
        PermissionRoute(
            modifier = modifier,
            navigateToNext = navigateToNext
        )
    }
}