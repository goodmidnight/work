package io.goodmidnight.transfer.ui.feature.settings.main.navigation

//import androidx.compose.ui.Modifier
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

fun NavGraphBuilder.settingsScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    route: String,
) {
    val popBackStack: () -> Unit = { navController.popBackStack() }

    composable(route) {
        SettingsRoute(modifier, popBackStack)
    }
}
