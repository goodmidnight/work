package io.goodmidnight.transfer.ui.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

/**
 * Retrieves a scoped [ViewModel] attached to the parent navigation graph.
 * This allows sharing a single ViewModel instance across multiple screens within the same nested graph.
 *
 * @param navController The [NavController] managing the navigation graph.
 * @return An instance of [T] scoped to the parent route, or a standard scoped ViewModel if no parent exists.
 */
@Composable
inline fun <reified T : ViewModel> NavBackStackEntry.sharedViewModel(
    navController: NavController,
): T {
    val navGraphRoute = destination.parent?.route ?: return hiltViewModel()

    val parentEntry = remember(this) {
        navController.getBackStackEntry(navGraphRoute)
    }

    return hiltViewModel(parentEntry)
}