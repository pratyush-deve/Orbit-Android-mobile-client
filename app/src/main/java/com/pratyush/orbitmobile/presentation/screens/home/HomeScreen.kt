package com.pratyush.orbitmobile.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.presentation.screens.home.actions.HomeAction
import com.pratyush.orbitmobile.presentation.screens.home.components.AiAssistantCard
import com.pratyush.orbitmobile.presentation.screens.home.components.ClipboardSyncCard
import com.pratyush.orbitmobile.presentation.screens.home.components.ConnectedLaptopStatusHeader
import com.pratyush.orbitmobile.presentation.screens.home.components.DisconnectedWarningBanner
import com.pratyush.orbitmobile.presentation.screens.home.components.FileAccessCard
import com.pratyush.orbitmobile.presentation.screens.home.components.RemoteControlsCard
import com.pratyush.orbitmobile.presentation.screens.home.components.SystemMonitorShortcutCard
import com.pratyush.orbitmobile.presentation.screens.home.components.TrackpadPointerCard

/**
 * Compact, polished remote-control Home Dashboard.
 * Replaces old bulky branding header with a sleek dashboard bar and overflow menu.
 * Features a redesigned compact laptop status card and the 2-column feature card grid.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToConnection: () -> Unit,
    onNavigateToClipboard: () -> Unit = {},
    onNavigateToDevices: () -> Unit = {},
    onNavigateToControls: () -> Unit = {},
    onNavigateToTrackpad: () -> Unit = {},
    onNavigateToSystemMonitor: () -> Unit = {},
    onNavigateToAiChat: () -> Unit = {},
    onNavigateToSendFiles: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isConnected = uiState.connectionState is ConnectionState.Connected
    val laptopName = when (val state = uiState.connectionState) {
        is ConnectionState.Connected -> state.deviceName
        is ConnectionState.Connecting -> state.deviceName
        is ConnectionState.TemporarilyUnavailable -> state.deviceName
        else -> "your Windows PC"
    }

    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var showDisconnectConfirmDialog by remember { mutableStateOf(false) }

    // Foreground lifecycle observer: refresh clipboard state whenever app returns to foreground
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAction(HomeAction.AppResumed)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 104.dp

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Sleek Dashboard Top Bar with repositioned overflow/hamburger menu
        item(span = { GridItemSpan(2) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                )

                Box {
                    IconButton(
                        onClick = { overflowMenuExpanded = true },
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF1E1E28), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options Menu",
                            tint = Color(0xFFE2E8F0),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = overflowMenuExpanded,
                        onDismissRequest = { overflowMenuExpanded = false },
                        modifier = Modifier.background(Color(0xFF1E1E28))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clipboard History", color = Color.White) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.ContentPaste,
                                    contentDescription = null,
                                    tint = Color(0xFF5EEAD4)
                                )
                            },
                            onClick = {
                                overflowMenuExpanded = false
                                onNavigateToClipboard()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Manage Devices", color = Color.White) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Devices,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8)
                                )
                            },
                            onClick = {
                                overflowMenuExpanded = false
                                onNavigateToDevices()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Disconnect Laptop", color = Color(0xFFEF4444)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.PowerSettingsNew,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444)
                                )
                            },
                            onClick = {
                                overflowMenuExpanded = false
                                showDisconnectConfirmDialog = true
                            }
                        )
                    }
                }
            }
        }

        // 2. Full-width Redesigned Connected Laptop Status Card
        item(span = { GridItemSpan(2) }) {
            ConnectedLaptopStatusHeader(
                connectionState = uiState.connectionState,
                deviceName = uiState.lastKnownDeviceName,
                effectiveState = uiState.effectiveConnectionState
            )
        }

        // 3. Reconnection Banner (displayed only when neither LAN nor Remote transport is active, or while reconnecting)
        if (!uiState.effectiveConnectionState.isConnected || uiState.isReconnecting) {
            item(span = { GridItemSpan(2) }) {
                DisconnectedWarningBanner(
                    isReconnecting = uiState.isReconnecting,
                    onReconnect = { viewModel.onAction(HomeAction.ReconnectRequested) }
                )
            }
        }

        if (uiState.hasPendingRemoteClipboard) {
            item(span = { GridItemSpan(2) }) {
                androidx.compose.material3.Surface(
                    onClick = { viewModel.applyPendingRemoteClipboard() },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    color = Color(0xFF1A1528),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA855F7))
                ) {
                    Text(
                        text = "Clipboard ready from PC — Tap to copy",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // 4. Feature Card 1: Remote Controls (Warm coral / terracotta)
        item {
            RemoteControlsCard(
                onOpenFullControls = {
                    viewModel.onAction(HomeAction.OpenRemoteControls)
                    onNavigateToControls()
                }
            )
        }

        // 5. Feature Card 2: Clipboard Sync (Mint / teal with real shared toggle)
        item {
            ClipboardSyncCard(
                isSyncEnabled = uiState.isClipboardSyncActive,
                onToggleSync = { viewModel.onAction(HomeAction.ToggleClipboardSync(it)) },
                onClickCard = onNavigateToClipboard
            )
        }

        // 6. Feature Card 3: AI Assistant (Purple / lavender)
        item {
            AiAssistantCard(
                onOpenAiChat = onNavigateToAiChat
            )
        }

        // 7. Feature Card 4: Trackpad (Indigo / periwinkle blue)
        item {
            TrackpadPointerCard(
                onOpenTrackpad = {
                    viewModel.onAction(HomeAction.OpenTrackpad)
                    onNavigateToTrackpad()
                }
            )
        }

        // 8. Feature Card 5: File Access (Amber / soft yellow)
        item {
            FileAccessCard(
                onOpenFileAccess = {
                    viewModel.onAction(HomeAction.OpenFileAccess)
                    onNavigateToSendFiles()
                }
            )
        }

        // 9. Feature Card 6: System Monitor (Forest green)
        item {
            SystemMonitorShortcutCard(
                onOpenSystemMonitor = onNavigateToSystemMonitor
            )
        }
    }

    // Disconnect Confirmation Dialog
    if (showDisconnectConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirmDialog = false },
            title = {
                Text(
                    text = "Disconnect Laptop",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to disconnect from $laptopName? You can reconnect at any time without scanning again.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectConfirmDialog = false
                        viewModel.onAction(HomeAction.DisconnectRequested)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Disconnect", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirmDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E1E28)
        )
    }
}
