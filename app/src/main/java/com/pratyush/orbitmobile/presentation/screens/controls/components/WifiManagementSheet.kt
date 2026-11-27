package com.pratyush.orbitmobile.presentation.screens.controls.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.domain.model.WifiControlStatus
import com.pratyush.orbitmobile.domain.model.WifiNetworkItem

/**
 * Bottom Sheet for managing Windows laptop Wi-Fi networks and adapter state.
 * Allows viewing real available networks, connecting, disconnecting, and toggling adapter power.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiManagementSheet(
    wifiStatus: WifiControlStatus,
    networks: List<WifiNetworkItem>,
    isLoading: Boolean,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onTogglePower: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF101726),
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Wi-Fi Networks",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        text = "Manage laptop wireless connection",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isLoading,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF1A2438), CircleShape)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF60A5FA),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh networks",
                                tint = Color(0xFFE2E8F0),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF1A2438), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFE2E8F0),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Wi-Fi Power Master Toggle Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF162034))
                    .border(1.dp, Color(0xFF23324E), RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (wifiStatus.isEnabled) Color(0xFF1E3A8A) else Color(0xFF272733)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (wifiStatus.isEnabled) Icons.Default.Wifi else Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = if (wifiStatus.isEnabled) Color(0xFF60A5FA) else Color(0xFF94A3B8),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Wi-Fi Adapter",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = if (wifiStatus.isEnabled) "Wireless radio is enabled" else "Turned off",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (wifiStatus.isEnabled) Color(0xFF60A5FA) else Color(0xFF94A3B8),
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }

                    Switch(
                        checked = wifiStatus.isEnabled,
                        onCheckedChange = { onTogglePower(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF2563EB),
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = Color(0xFF1E293B)
                        )
                    )
                }
            }

            // Current Connected Network Card
            if (wifiStatus.isEnabled && wifiStatus.isConnected && !wifiStatus.ssid.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0F2537))
                        .border(1.dp, Color(0xFF1E4668), RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                            )

                            Column {
                                Text(
                                    text = wifiStatus.ssid,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                )
                                Text(
                                    text = "Connected" + (wifiStatus.signal?.let { " • $it% signal" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF4ADE80),
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onDisconnect,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFF87171)
                            )
                        ) {
                            Text(
                                text = "Disconnect",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }

            // Available Networks Header
            Text(
                text = "Available Networks (${networks.size})",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            )

            // Networks List
            if (networks.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (!wifiStatus.isEnabled) "Wi-Fi is turned off" else "No Wi-Fi networks found in range",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF64748B),
                            fontSize = 13.sp
                        )
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(networks, key = { it.ssid }) { network ->
                        WifiNetworkRow(
                            network = network,
                            isLoading = isLoading,
                            onConnect = { onConnect(network.ssid) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun WifiNetworkRow(
    network: WifiNetworkItem,
    isLoading: Boolean,
    onConnect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF162034))
            .border(
                1.dp,
                if (network.isConnected) Color(0xFF1E4668) else Color(0xFF223049),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = if (network.isConnected) Color(0xFF4ADE80) else Color(0xFF93C5FD),
                    modifier = Modifier.size(20.dp)
                )

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = network.ssid,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!network.authentication.contains("Open", ignoreCase = true)) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Secured",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    Text(
                        text = if (network.isConnected) "Connected • ${network.signal}%" else "${network.signal}% signal • ${network.authentication}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (network.isConnected) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    )
                }
            }

            if (network.isConnected) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF064E3B))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Active",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF4ADE80),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            } else {
                Button(
                    onClick = onConnect,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E3A8A),
                        contentColor = Color(0xFF93C5FD)
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Connect",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}
