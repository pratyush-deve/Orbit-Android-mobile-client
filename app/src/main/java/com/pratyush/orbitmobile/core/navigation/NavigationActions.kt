package com.pratyush.orbitmobile.core.navigation

import androidx.navigation.NavHostController

/**
 * Encapsulates navigation operations to keep screens and UI components decoupled
 * from direct NavController references.
 *
 * Differentiates cleanly between:
 * 1. Top-level bottom navigation tabs (Home, Devices, Settings, Connection) that preserve hierarchy.
 * 2. Nested sub-screens (Clipboard, Controls, About, QrScanner) that are pushed onto the backstack.
 */
class NavigationActions(private val navController: NavHostController) {

    /**
     * Top-level bottom navigation tab destinations.
     */
    private val tabRoutes = setOf(
        AppRoute.Home.route,
        AppRoute.Connection.route,
        AppRoute.Devices.route,
        AppRoute.Settings.route
    )

    fun navigateTo(route: AppRoute, isConnected: Boolean = true) {
        if (tabRoutes.contains(route.route)) {
            navigateToTab(route, isConnected)
        } else {
            navigateToSubScreen(route)
        }
    }

    fun navigateToTab(route: AppRoute, isConnected: Boolean = true) {
        val currentRoute = navController.currentDestination?.route
        if (currentRoute == route.route) {
            return
        }

        // Anchor popup to graph's permanent start destination to preserve tab states and back stack
        val startDest = navController.graph.startDestinationRoute ?: AppRoute.Home.route

        navController.navigate(route.route) {
            popUpTo(startDest) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToSubScreen(route: AppRoute) {
        val currentRoute = navController.currentDestination?.route
        if (currentRoute == route.route) {
            return
        }

        navController.navigate(route.route) {
            launchSingleTop = true
        }
    }

    fun navigateBack(fallbackRoute: AppRoute = AppRoute.Home) {
        if (!navController.popBackStack()) {
            navController.navigate(fallbackRoute.route) {
                launchSingleTop = true
            }
        }
    }
}
