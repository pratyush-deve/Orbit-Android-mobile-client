package com.pratyush.orbitmobile.presentation.screens.controls

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import com.pratyush.orbitmobile.presentation.screens.controls.actions.ControlsAction
import com.pratyush.orbitmobile.presentation.screens.controls.components.BluetoothCard
import com.pratyush.orbitmobile.presentation.screens.controls.components.BluetoothManagementSheet
import com.pratyush.orbitmobile.presentation.screens.controls.components.BrightnessCard
import com.pratyush.orbitmobile.presentation.screens.controls.components.CameraCard
import com.pratyush.orbitmobile.presentation.screens.controls.components.MicrophoneCard
import com.pratyush.orbitmobile.presentation.screens.controls.components.NetworkInfoBanner
import com.pratyush.orbitmobile.presentation.screens.controls.components.VolumeCard
import com.pratyush.orbitmobile.presentation.screens.controls.components.WifiCard
import com.pratyush.orbitmobile.presentation.screens.controls.components.WifiManagementSheet

/**
 * ORBIT Mobile Remote Controls Screen.
 * Faithful implementation of the dark Material 3 Expressive visual reference.
 * Real authenticated LAN remote controls for Windows laptop:
 * - Wi-Fi: Visibility only
 * - Bluetooth: Device management and active connection toggle
 * - Microphone: Real Windows WASAPI Core Audio mute/unmute
 * - Camera: Real Windows Camera privacy consent toggle
 * - Volume: Real Windows Core Audio master volume slider & mute
 * - Quick Actions: Mute, Unmute, Disconnect BT
 */
@Composable
fun ControlsScreen(
    viewModel: ControlsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface error message via snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onAction(ControlsAction.DismissError)
        }
    }

    // Intercept system Back button: dismiss any open bottom sheet first, then navigate back
    BackHandler {
        when {
            uiState.isBluetoothSheetOpen -> viewModel.onAction(ControlsAction.CloseBluetoothSheet)
            uiState.isWifiSheetOpen -> viewModel.onAction(ControlsAction.CloseWifiSheet)
            else -> onNavigateBack()
        }
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomContentPadding = navBarBottom + 104.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitColors.PitchBlack)
    ) {
        // Organic violet/purple ambient corner background blobs (matching visual reference)
        ControlsOrganicBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, bottom = bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Compact Header Row respecting status bar insets
            ControlsHeader(onBack = onNavigateBack)

            // 2. Two-Column Grid: Row 1 (Wi-Fi & Bluetooth)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                WifiCard(
                    wifi = uiState.wifi,
                    isLaptopConnected = uiState.isConnected,
                    onOpenManagement = { viewModel.onAction(ControlsAction.OpenWifiSheet) },
                    modifier = Modifier.weight(1f)
                )

                BluetoothCard(
                    bluetooth = uiState.bluetooth,
                    isLaptopConnected = uiState.isConnected,
                    onOpenManagement = { viewModel.onAction(ControlsAction.OpenBluetoothSheet) },
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. Two-Column Grid: Row 2 (Microphone & Camera)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MicrophoneCard(
                    microphone = uiState.microphone,
                    isLaptopConnected = uiState.isConnected,
                    onToggle = { viewModel.onAction(ControlsAction.ToggleMicrophone) },
                    modifier = Modifier.weight(1f)
                )

                CameraCard(
                    camera = uiState.camera,
                    isLaptopConnected = uiState.isConnected,
                    onToggle = { viewModel.onAction(ControlsAction.ToggleCamera) },
                    modifier = Modifier.weight(1f)
                )
            }

            // 4. Full-Width Volume Card
            VolumeCard(
                volumePercentage = uiState.volumePercentage,
                isMuted = uiState.isVolumeMuted,
                isVolumeInitialized = uiState.isVolumeInitialized,
                isLaptopConnected = uiState.isConnected,
                onVolumeChanged = { pct -> viewModel.onAction(ControlsAction.SetVolume(pct)) },
                onVolumeChangeFinished = { pct -> viewModel.onAction(ControlsAction.CommitVolume(pct)) },
                onToggleMute = { viewModel.onAction(ControlsAction.ToggleVolumeMute) }
            )

            // 5. Full-Width Brightness Card (beneath Volume card)
            BrightnessCard(
                brightnessPercentage = uiState.brightnessPercentage,
                isBrightnessAvailable = uiState.isBrightnessAvailable,
                isBrightnessInitialized = uiState.isBrightnessInitialized,
                isLaptopConnected = uiState.isConnected,
                onBrightnessChanged = { pct -> viewModel.onAction(ControlsAction.SetBrightness(pct)) },
                onBrightnessChangeFinished = { pct -> viewModel.onAction(ControlsAction.CommitBrightness(pct)) }
            )

            // 6. LAN Notice Banner
            NetworkInfoBanner()
        }

        // Bluetooth Management Sheet
        if (uiState.isBluetoothSheetOpen) {
            BluetoothManagementSheet(
                devices = uiState.bluetoothDevices,
                isLoading = uiState.isBluetoothActionLoading,
                onConnect = { id -> viewModel.onAction(ControlsAction.ConnectBluetoothDevice(id)) },
                onDisconnect = { id -> viewModel.onAction(ControlsAction.DisconnectBluetoothDevice(id)) },
                onRefresh = { viewModel.onAction(ControlsAction.RefreshBluetoothDevices) },
                onDismiss = { viewModel.onAction(ControlsAction.CloseBluetoothSheet) }
            )
        }

        // Wi-Fi Management Sheet
        if (uiState.isWifiSheetOpen) {
            WifiManagementSheet(
                wifiStatus = uiState.wifi,
                networks = uiState.wifiNetworks,
                isLoading = uiState.isWifiActionLoading,
                onConnect = { ssid -> viewModel.onAction(ControlsAction.ConnectWifiNetwork(ssid)) },
                onDisconnect = { viewModel.onAction(ControlsAction.DisconnectWifiNetwork) },
                onTogglePower = { enabled -> viewModel.onAction(ControlsAction.ToggleWifiPower(enabled)) },
                onRefresh = { viewModel.onAction(ControlsAction.RefreshWifiNetworks) },
                onDismiss = { viewModel.onAction(ControlsAction.CloseWifiSheet) }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomContentPadding)
        )
    }
}

@Composable
private fun ControlsHeader(
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1E28))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Column {
            Text(
                text = "Controls",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Control your laptop remotely",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF94A3B8),
                    fontSize = 13.5.sp
                )
            )
        }
    }
}

@Composable
private fun ControlsOrganicBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Top right violet ambient glow
        drawCircle(
            color = Color(0x334C1D95),
            radius = w * 0.45f,
            center = Offset(w * 0.95f, h * 0.08f)
        )

        // Middle right dark purple wave
        drawCircle(
            color = Color(0x1F581C87),
            radius = w * 0.35f,
            center = Offset(w * 0.9f, h * 0.45f)
        )

        // Bottom right subtle bronze/amber glow
        drawCircle(
            color = Color(0x2278350F),
            radius = w * 0.35f,
            center = Offset(w * 0.98f, h * 0.88f)
        )
    }
}
