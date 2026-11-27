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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import com.pratyush.orbitmobile.domain.model.BluetoothDevice

/**
 * Bottom Sheet for managing Windows laptop Bluetooth devices.
 * Shows real paired/available devices with Connect/Disconnect actions and refresh capability.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothManagementSheet(
    devices: List<BluetoothDevice>,
    isLoading: Boolean,
    onConnect: (String) -> Unit,
    onDisconnect: (String?) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF131B28),
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
                        text = "Bluetooth Devices",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        text = "Manage your laptop's Bluetooth connections",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isLoading,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF1E2E42), CircleShape)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFF67E8F9),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color(0xFF67E8F9),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.size(8.dp))

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF1E2E42), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Devices List
            if (devices.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No Bluetooth devices found",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp
                            )
                        )
                        Text(
                            text = "Tap refresh to scan for paired devices",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(devices, key = { it.id }) { device ->
                        BluetoothDeviceItemRow(
                            device = device,
                            onConnect = { onConnect(device.id) },
                            onDisconnect = { onDisconnect(device.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BluetoothDeviceItemRow(
    device: BluetoothDevice,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val rowBg = Color(0xFF192436)
    val rowBorder = if (device.isConnected) Color(0xFF0E7490) else Color(0xFF243248)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(rowBg)
            .border(1.dp, rowBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (device.isConnected) Color(0xFF0E7490) else Color(0xFF1E2E42)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (device.isConnected) Icons.Filled.BluetoothConnected else Icons.Filled.Bluetooth,
                        contentDescription = null,
                        tint = if (device.isConnected) Color(0xFF67E8F9) else Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = when {
                            device.isConnected -> "Connected"
                            device.isPaired -> "Paired"
                            else -> "Available"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (device.isConnected) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            fontWeight = if (device.isConnected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.size(8.dp))

            if (device.isConnected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFF472B6)
                    ),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Disconnect", fontSize = 11.5.sp)
                }
            } else {
                Button(
                    onClick = onConnect,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0E7490)
                    ),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Connect", fontSize = 11.5.sp, color = Color.White)
                }
            }
        }
    }
}
