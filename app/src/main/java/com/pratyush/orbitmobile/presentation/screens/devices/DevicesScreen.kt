package com.pratyush.orbitmobile.presentation.screens.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pratyush.orbitmobile.core.designsystem.components.OrbitButton
import com.pratyush.orbitmobile.presentation.components.EmptyStateView
import com.pratyush.orbitmobile.presentation.components.ScreenHeader
import com.pratyush.orbitmobile.presentation.screens.devices.actions.DevicesAction
import com.pratyush.orbitmobile.presentation.screens.devices.components.DeviceDeleteDialog
import com.pratyush.orbitmobile.presentation.screens.devices.components.SavedDeviceCard

/**
 * Dedicated Devices screen orchestrator for managing previously paired laptops.
 * Respects status bar and navigation bar insets; scrolls vertically when content exceeds height.
 */
@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // Delete confirmation dialog
    uiState.devicePendingDeletion?.let { device ->
        DeviceDeleteDialog(
            device = device,
            onConfirm = { viewModel.onAction(DevicesAction.DeleteDeviceConfirmed(device.id)) },
            onDismiss = { viewModel.onAction(DevicesAction.DismissRemoveDialog) }
        )
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = navBarBottom + 104.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(
            title = "Paired PCs",
            subtitle = "Switch between and manage authorized laptops"
        )

        if (uiState.pairedDevices.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = bottomPadding)
            ) {
                EmptyStateView(
                    icon = Icons.Outlined.DevicesOther,
                    title = "No Paired Laptops",
                    message = "Scan the QR code on ORBIT Windows to save and pair your PC."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.pairedDevices, key = { it.id }) { device ->
                    SavedDeviceCard(
                        device = device,
                        isSelected = device.id == uiState.selectedDeviceId,
                        onConnect = {
                            viewModel.onAction(DevicesAction.ConnectRequested(device))
                            onNavigateToHome()
                        },
                        onRemove = {
                            viewModel.onAction(DevicesAction.ConfirmRemoveDevice(device))
                        }
                    )
                }
            }
        }
    }
}

