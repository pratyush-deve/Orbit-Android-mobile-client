package com.pratyush.orbitmobile.core.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.core.designsystem.components.OrbitLogo
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import com.pratyush.orbitmobile.core.di.AppContainer
import com.pratyush.orbitmobile.core.di.ViewModelFactory
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.presentation.navigation.OrbitBottomBar
import com.pratyush.orbitmobile.presentation.screens.about.AboutScreen
import com.pratyush.orbitmobile.presentation.screens.about.AboutViewModel
import com.pratyush.orbitmobile.presentation.screens.clipboard.ClipboardScreen
import com.pratyush.orbitmobile.presentation.screens.connection.ConnectionScreen
import com.pratyush.orbitmobile.presentation.screens.connection.ConnectionViewModel
import com.pratyush.orbitmobile.presentation.screens.connection.actions.ConnectionAction
import com.pratyush.orbitmobile.presentation.screens.connection.qr.QrScannerScreen
import com.pratyush.orbitmobile.presentation.screens.controls.ControlsScreen
import com.pratyush.orbitmobile.presentation.screens.controls.ControlsViewModel
import com.pratyush.orbitmobile.presentation.screens.devices.DevicesScreen
import com.pratyush.orbitmobile.presentation.screens.devices.DevicesViewModel
import com.pratyush.orbitmobile.presentation.screens.home.HomeScreen
import com.pratyush.orbitmobile.presentation.screens.home.HomeViewModel
import com.pratyush.orbitmobile.presentation.screens.settings.SettingsScreen
import com.pratyush.orbitmobile.presentation.screens.settings.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Top-level navigation host tying routes to their respective screens and bottom bar.
 * Implements Smart App Launch: If a previously paired Windows PC is available and connection
 * is confirmed, opens directly on Home screen without showing Connection screen or re-scanning QR.
 */
@Composable
fun AppNavigation(
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val navActions = remember(navController) { NavigationActions(navController) }
    val viewModelFactory = remember(appContainer) { ViewModelFactory(appContainer) }
    val scope = rememberCoroutineScope()

    val initialStartDestination = rememberSaveable {
        val paired = runCatching {
            (appContainer.deviceRepository.getPairedDevices() as? kotlinx.coroutines.flow.StateFlow<List<com.pratyush.orbitmobile.domain.model.Device>>)?.value
        }.getOrNull()
        if (!paired.isNullOrEmpty()) AppRoute.Home.route else AppRoute.Connection.route
    }

    var isStartupSplashVisible by rememberSaveable { mutableStateOf(true) }

    // Brief initial brand splash (dismissed once activity is interactive, survives screen off/on)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400L)
        isStartupSplashVisible = false
    }

    // Observe global connection state
    val connectionStatus by appContainer.getConnectionStatusUseCase.execute().collectAsState()
    val isConnected = connectionStatus is ConnectionState.Connected

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Permanent 3 bottom destinations: Home, Devices, Settings (never swapped on transient disconnect)
    val bottomDestinations = remember { AppRoute.bottomDestinations }

    // Fullscreen for QR scanner, dedicated Trackpad, System Monitor, AI Chat, and Send Files flows without bottom navigation
    val showBottomBar = currentRoute != AppRoute.QrScanner.route &&
            currentRoute != AppRoute.Trackpad.route &&
            currentRoute != AppRoute.SystemMonitor.route &&
            currentRoute != AppRoute.AiChat.route &&
            currentRoute != AppRoute.SendFiles.route

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitColors.PitchBlack)
    ) {
        // NavHost remains permanently mounted across all Activity lifecycle states
        NavHost(
            navController = navController,
            startDestination = initialStartDestination,
            modifier = Modifier.fillMaxSize()
        ) {
            // STATE B: Connected Desktop Companion Home Screen
            composable(AppRoute.Home.route) {
                val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToConnection = {
                        navController.navigate(AppRoute.Connection.route) {
                            popUpTo(AppRoute.Home.route) { inclusive = true }
                        }
                    },
                    onNavigateToClipboard = {
                        navActions.navigateToSubScreen(AppRoute.Clipboard)
                    },
                    onNavigateToDevices = {
                        navActions.navigateToTab(AppRoute.Devices, isConnected)
                    },
                    onNavigateToControls = {
                        navActions.navigateToSubScreen(AppRoute.Controls)
                    },
                    onNavigateToTrackpad = {
                        navActions.navigateToSubScreen(AppRoute.Trackpad)
                    },
                    onNavigateToSystemMonitor = {
                        navActions.navigateToSubScreen(AppRoute.SystemMonitor)
                    },
                    onNavigateToAiChat = {
                        navActions.navigateToSubScreen(AppRoute.AiChat)
                    },
                    onNavigateToSendFiles = {
                        navActions.navigateToSubScreen(AppRoute.SendFiles)
                    }
                )
            }

            // Dedicated Send Files to Laptop Screen
            composable(AppRoute.SendFiles.route) {
                val sendFilesViewModel: com.pratyush.orbitmobile.presentation.screens.filesystem.send.SendFilesViewModel = viewModel(factory = viewModelFactory)
                com.pratyush.orbitmobile.presentation.screens.filesystem.send.SendFilesScreen(
                    viewModel = sendFilesViewModel,
                    onNavigateBack = {
                        navActions.navigateBack(AppRoute.Home)
                    }
                )
            }

            // Dedicated Antigravity AI Chat Screen
            composable(AppRoute.AiChat.route) {
                val aiViewModel: com.pratyush.orbitmobile.presentation.screens.ai.AiChatViewModel = viewModel(factory = viewModelFactory)
                com.pratyush.orbitmobile.presentation.screens.ai.AiChatScreen(
                    viewModel = aiViewModel,
                    onNavigateBack = {
                        navActions.navigateBack(AppRoute.Home)
                    }
                )
            }

            // Dedicated System Monitor Screen
            composable(AppRoute.SystemMonitor.route) {
                val monitorViewModel: com.pratyush.orbitmobile.presentation.screens.systemmonitor.SystemMonitorViewModel = viewModel(factory = viewModelFactory)
                com.pratyush.orbitmobile.presentation.screens.systemmonitor.SystemMonitorScreen(
                    viewModel = monitorViewModel,
                    onNavigateBack = {
                        navActions.navigateBack(AppRoute.Home)
                    }
                )
            }

            // Dedicated Full-Screen Trackpad Screen
            composable(AppRoute.Trackpad.route) {
                val trackpadViewModel: com.pratyush.orbitmobile.presentation.screens.trackpad.TrackpadViewModel = viewModel(factory = viewModelFactory)
                com.pratyush.orbitmobile.presentation.screens.trackpad.TrackpadScreen(
                    viewModel = trackpadViewModel,
                    onNavigateBack = {
                        navActions.navigateBack(AppRoute.Home)
                    }
                )
            }

            // Dedicated Controls Screen
            composable(AppRoute.Controls.route) {
                val controlsViewModel: ControlsViewModel = viewModel(factory = viewModelFactory)
                ControlsScreen(
                    viewModel = controlsViewModel,
                    onNavigateBack = {
                        navActions.navigateBack(AppRoute.Home)
                    }
                )
            }

            // Dedicated Clipboard Screen
            composable(AppRoute.Clipboard.route) {
                val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
                ClipboardScreen(
                    viewModel = homeViewModel,
                    onNavigateBack = {
                        navActions.navigateBack(AppRoute.Home)
                    }
                )
            }

            // STATE A: Unconnected Entry / Dedicated Connection Screen
            composable(AppRoute.Connection.route) { backStackEntry ->
                val connectionViewModel: ConnectionViewModel = viewModel(factory = viewModelFactory)

                // Observe QR code payload passed back from QrScannerScreen via savedStateHandle
                val scannedQrPayload by backStackEntry.savedStateHandle
                    .getStateFlow<String?>("scanned_qr_payload", null)
                    .collectAsState()

                LaunchedEffect(scannedQrPayload) {
                    val payload = scannedQrPayload
                    if (!payload.isNullOrBlank()) {
                        backStackEntry.savedStateHandle.remove<String>("scanned_qr_payload")
                        connectionViewModel.onAction(ConnectionAction.QrCodeScanned(payload))
                    }
                }

                ConnectionScreen(
                    viewModel = connectionViewModel,
                    onNavigateToQrScanner = {
                        navActions.navigateToSubScreen(AppRoute.QrScanner)
                    },
                    onConnected = {
                        scope.launch {
                            appContainer.preferencesManager.setExplicitlyDisconnected(false)
                        }
                        navController.navigate(AppRoute.Home.route) {
                            popUpTo(AppRoute.Connection.route) { inclusive = true }
                        }
                    }
                )
            }

            // Dedicated QR Scanner Screen
            composable(AppRoute.QrScanner.route) {
                QrScannerScreen(
                    onQrScanned = { qrData ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("scanned_qr_payload", qrData)
                        navActions.navigateBack(AppRoute.Connection)
                    },
                    onNavigateBack = {
                        navActions.navigateBack(AppRoute.Connection)
                    },
                    onNavigateToManual = {
                        navActions.navigateBack(AppRoute.Connection)
                    }
                )
            }

            // Dedicated Devices Management Screen
            composable(AppRoute.Devices.route) {
                val devicesViewModel: DevicesViewModel = viewModel(factory = viewModelFactory)
                DevicesScreen(
                    viewModel = devicesViewModel,
                    onNavigateToHome = {
                        scope.launch {
                            appContainer.preferencesManager.setExplicitlyDisconnected(false)
                        }
                        navActions.navigateToTab(AppRoute.Home, isConnected)
                    }
                )
            }

            // Settings Screen
            composable(AppRoute.Settings.route) {
                val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateToAbout = {
                        navActions.navigateToSubScreen(AppRoute.About)
                    }
                )
            }

            // About Screen
            composable(AppRoute.About.route) {
                val aboutViewModel: AboutViewModel = viewModel(factory = viewModelFactory)
                AboutScreen(
                    viewModel = aboutViewModel,
                    onNavigateBack = {
                        navActions.navigateBack(AppRoute.Settings)
                    }
                )
            }
        }

        if (showBottomBar) {
            OrbitBottomBar(
                currentRoute = currentRoute,
                destinations = bottomDestinations,
                onNavigateToRoute = { destination ->
                    navActions.navigateToTab(destination)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(99f)
            )
        }

        // Ephemeral cold-launch overlay with latest ORBIT branding (never tears down NavHost)
        AnimatedVisibility(
            visible = isStartupSplashVisible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(400)),
            modifier = Modifier.zIndex(100f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OrbitColors.PitchBlack),
                contentAlignment = Alignment.Center
            ) {
                OrbitLogo(size = 140.dp)
            }
        }
    }
}
