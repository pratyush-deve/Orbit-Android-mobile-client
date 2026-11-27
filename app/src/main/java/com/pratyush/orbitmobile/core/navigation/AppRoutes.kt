package com.pratyush.orbitmobile.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Centralized navigation routes for ORBIT Mobile.
 */
sealed class AppRoute(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Home : AppRoute("home", "Home", Icons.Filled.Home)
    data object Connection : AppRoute("connection", "Connect", Icons.Outlined.QrCodeScanner)
    data object QrScanner : AppRoute("qr_scanner", "Scan QR", Icons.Outlined.QrCodeScanner)
    data object Devices : AppRoute("devices", "Devices", Icons.Outlined.Devices)
    data object Controls : AppRoute("controls", "Controls", Icons.Filled.Widgets)
    data object Trackpad : AppRoute("trackpad", "Trackpad", Icons.Outlined.TouchApp)
    data object SystemMonitor : AppRoute("system_monitor", "System Monitor", Icons.Outlined.MonitorHeart)
    data object AiChat : AppRoute("ai_chat", "AI Chat", Icons.Outlined.AutoAwesome)
    data object SendFiles : AppRoute("send_files", "Send Files", Icons.Outlined.FolderOpen)
    data object Clipboard : AppRoute("clipboard", "Clipboard", Icons.Outlined.ContentPaste)
    data object Settings : AppRoute("settings", "Settings", Icons.Outlined.Settings)
    data object About : AppRoute("about", "About", Icons.Outlined.Info)

    companion object {
        /**
         * Permanent navigation destinations for floating bottom navigation:
         * 1. Home
         * 2. Devices
         * 3. Settings
         *
         * Declared with a getter rather than an eagerly initialized static field to
         * eliminate JVM subclass-to-superclass class initialization circularity,
         * ensuring Home, Devices, and Settings are fully initialized non-null singletons.
         */
        val bottomDestinations: List<AppRoute>
            get() = listOf(
                Home,
                Devices,
                Settings
            )

        // Aliases retained for backwards compatibility
        val connectedDestinations: List<AppRoute>
            get() = bottomDestinations

        val disconnectedDestinations: List<AppRoute>
            get() = bottomDestinations
    }
}
