package io.goodmidnight.transfer.ui.core.navigation

/**
 * [NavigationRoute]
 * - A route that defines navigation within the application.
 * @property destinationRoute The destination path for navigation.
 */
interface NavigationRoute {
    val destinationRoute: String

    /**
     * [buildNavigationRoute]
     * - Constructs the full navigation route for the target destination.
     * - Appends a query string to the route if additional arguments are provided.
     * @param route The base route path.
     * @param args A set of key-value pairs representing navigation parameters.
     */
    fun buildNavigationRoute(route: String, vararg args: Pair<String, String>) =
        route + (args.takeIf { it.isNotEmpty() }?.let { "?" + args.joinToString("&") { "${it.first}=${it.second}" } }
            ?: "")
}