package io.goodmidnight.transfer.ui.core.navigation.graph

import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.navigation
import io.goodmidnight.transfer.ui.core.navigation.MainGraph
import io.goodmidnight.transfer.ui.feature.transfer.home.navigation.homeScreen
import io.goodmidnight.transfer.ui.feature.transfer.progress.navigation.progressScreen
import io.goodmidnight.transfer.ui.feature.transfer.qr.navigation.qrScreen

fun NavGraphBuilder.transferGraphBuilder(
    modifier: Modifier = Modifier,
    navController: NavController,
    graphName: String,
) {
    navigation(
        route = graphName,
        startDestination = MainGraph.Transfer.Home.destinationRoute
    ) {
        homeScreen(
            modifier = modifier,
            navController = navController,
            route = MainGraph.Transfer.Home.destinationRoute
        )
        progressScreen(
            modifier = modifier,
            navController = navController,
            route = MainGraph.Transfer.Progress.destinationRoute
        )
        qrScreen(
            modifier = modifier,
            navController = navController,
            route = MainGraph.Transfer.Qr.destinationRoute
        )
    }
}
