package com.pratyush.orbitmobile.presentation.screens.connection.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pratyush.orbitmobile.core.designsystem.components.OrbitCard
import com.pratyush.orbitmobile.core.designsystem.components.OrbitStatusBadge
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitShapes
import com.pratyush.orbitmobile.domain.model.Device

/**
 * Section presenting previously paired laptops for quick 1-tap reconnection.
 */
@Composable
fun SavedDevicesQuickConnect(
    savedDevices: List<Device>,
    onConnectDevice: (Device) -> Unit,
    modifier: Modifier = Modifier
) {
    if (savedDevices.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Previously Paired PCs",
            style = MaterialTheme.typography.titleMedium,
            color = OrbitColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(10.dp))

        savedDevices.forEach { device ->
            OrbitCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onConnectDevice(device) }
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
                        Icon(
                            imageVector = Icons.Outlined.Laptop,
                            contentDescription = null,
                            tint = OrbitColors.PurpleSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = OrbitColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${device.ipAddress} · ${device.lastConnectedText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = OrbitColors.TextSecondary
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = { onConnectDevice(device) },
                        shape = OrbitShapes.small,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = OrbitColors.PurpleDarkContainer,
                            contentColor = OrbitColors.PurpleAccentGlow
                        )
                    ) {
                        Text(
                            text = "Connect",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
