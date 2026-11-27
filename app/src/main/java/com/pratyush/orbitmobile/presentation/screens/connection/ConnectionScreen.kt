package com.pratyush.orbitmobile.presentation.screens.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.presentation.screens.connection.actions.ConnectionAction
import com.pratyush.orbitmobile.presentation.screens.connection.components.ConnectionSecurityFooter
import com.pratyush.orbitmobile.presentation.screens.connection.components.ManualConnectionSection
import com.pratyush.orbitmobile.presentation.screens.connection.components.OrbitBrandingHeader
import com.pratyush.orbitmobile.presentation.screens.connection.components.SavedDevicesQuickConnect
import com.pratyush.orbitmobile.presentation.screens.connection.components.ScanQrActionCard

/**
 * Thin screen orchestrator for the Connection experience.
 * First screen for new sessions without an active connection.
 */
@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel,
    onNavigateToQrScanner: () -> Unit,
    onConnected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // Automatically navigate to Home when connection is established
    LaunchedEffect(uiState.connectionState) {
        if (uiState.connectionState is ConnectionState.Connected) {
            onConnected()
        }
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = navBarBottom + 104.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1, 2, 3: Branding, logo, heading and description
        OrbitBrandingHeader()

        // 4: Prominent QR code scanning action
        ScanQrActionCard(
            onScanQrClicked = onNavigateToQrScanner,
            isConnecting = uiState.isConnecting,
            errorMessage = uiState.errorMessage
        )

        // 5: Secondary manual connection fallback
        ManualConnectionSection(
            hostInput = uiState.hostInput,
            portInput = uiState.portInput,
            isConnecting = uiState.isConnecting,
            errorMessage = uiState.errorMessage,
            onHostChanged = { viewModel.onAction(ConnectionAction.HostInputChanged(it)) },
            onPortChanged = { viewModel.onAction(ConnectionAction.PortInputChanged(it)) },
            onConnectManual = { viewModel.onAction(ConnectionAction.ConnectManualRequested) }
        )

        // 6: Previously paired devices section (quick connect)
        SavedDevicesQuickConnect(
            savedDevices = uiState.savedDevices,
            onConnectDevice = { viewModel.onAction(ConnectionAction.ConnectDeviceRequested(it)) }
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 7: Security and encryption footer
        ConnectionSecurityFooter()
    }
}
