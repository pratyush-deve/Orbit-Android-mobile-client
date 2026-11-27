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
import androidx.compose.material.icons.outlined.Computer
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
import com.pratyush.orbitmobile.domain.model.Device

/**
 * Reusable list item representing a discovered or paired desktop peer.
 */
@Composable
fun DeviceListItem(
    device: Device,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        hasGlowBorder = isSelected
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
                Icon(
                    imageVector = Icons.Outlined.Computer,
                    contentDescription = null,
                    tint = if (isSelected) OrbitColors.PurpleAccentGlow else OrbitColors.PurpleSecondary,
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
                        text = "${device.ipAddress}:${device.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OrbitColors.TextSecondary
                    )
                }
            }
            OrbitStatusBadge(
                statusText = if (device.isPaired) "Paired" else "Discovered",
                statusColor = if (device.isPaired) OrbitColors.StatusOnline else OrbitColors.StatusInfo
            )
        }
    }
}
