package io.goodmidnight.transfer.ui.core.navigation

sealed class MainGraph(
    val route: String,
) {
    data object Transfer : MainGraph("transfer") {
        data object Home : MainGraph("transfer/home"), NavigationRoute {
            override val destinationRoute: String = route
            fun buildNavigationRoute(): String = super.buildNavigationRoute(route)

            fun deepLinkUriPattern(): String = "${SCHEME_DOMAIN}$route"
        }

        data object Progress : MainGraph("transfer/progress"), NavigationRoute {
            override val destinationRoute: String = route
            fun buildNavigationRoute(): String = super.buildNavigationRoute(route)

            fun deepLinkUriPattern(): String = "${SCHEME_DOMAIN}$route"
        }

        data object Qr : MainGraph("transfer/qr"), NavigationRoute {
            override val destinationRoute: String = route
            fun buildNavigationRoute(): String = super.buildNavigationRoute(route)
        }
    }

    data object Settings : MainGraph("settings"), NavigationRoute {
        override val destinationRoute: String = route
        fun buildNavigationRoute(): String = super.buildNavigationRoute(route)
    }

    data object Permission: MainGraph("permission"), NavigationRoute {
        override val destinationRoute: String = route
        fun buildNavigationRoute(): String = super.buildNavigationRoute(route)
    }

    companion object {
        const val SCHEME_DOMAIN = "transfer://io.goodmidnight.transfer/"
    }
}