package com.pratyush.orbitmobile.presentation.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.pratyush.orbitmobile.core.navigation.AppRoute

/**
 * Data representation of a bottom navigation bar destination.
 */
data class BottomBarItem(
    val route: AppRoute,
    val title: String,
    val icon: ImageVector
)
