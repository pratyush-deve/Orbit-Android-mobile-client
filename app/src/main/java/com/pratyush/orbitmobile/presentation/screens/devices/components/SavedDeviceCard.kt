package com.pratyush.orbitmobile.presentation.screens.devices.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * Dedicated card displaying a previously paired laptop with reconnection and removal actions.
 */
@Composable
fun SavedDeviceCard(
    device: Device,
    isSelected: Boolean,
    onConnect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitCard(
        modifier = modifier.fillMaxWidth(),
        hasGlowBorder = isSelected
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Laptop,
                    contentDescription = null,
                    tint = if (device.isOnline) OrbitColors.PurpleAccentGlow else OrbitColors.PurpleSecondary,
                    modifier = Modifier.size(32.dp)
                )

                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = OrbitColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${device.osInfo} · ${device.ipAddress}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OrbitColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Last connected: ${device.lastConnectedText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OrbitColors.TextTertiary
                    )
                }
            }

            OrbitStatusBadge(
                statusText = if (device.isOnline) "ONLINE" else "OFFLINE",
                statusColor = if (device.isOnline) OrbitColors.StatusOnline else OrbitColors.TextTertiary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onConnect,
                shape = OrbitShapes.small,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = OrbitColors.PurplePrimary,
                    contentColor = OrbitColors.TextPrimary
                )
            ) {
                Text(
                    text = "Connect / Reconnect",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Remove Paired Laptop",
                    tint = OrbitColors.TextTertiary
                )
            }
        }
    }
}
