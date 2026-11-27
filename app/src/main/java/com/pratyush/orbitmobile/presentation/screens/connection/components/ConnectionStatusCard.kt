package com.pratyush.orbitmobile.presentation.screens.connection.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pratyush.orbitmobile.core.designsystem.components.OrbitCard
import com.pratyush.orbitmobile.core.designsystem.components.OrbitStatusBadge
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import com.pratyush.orbitmobile.domain.model.ConnectionState

/**
 * Detailed connection status card displaying the active state of the 8 logical connection phases.
 */
@Composable
fun ConnectionStatusCard(
    connectionState: ConnectionState,
    isNetworkAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    val (statusLabel, statusColor) = when (connectionState) {
        is ConnectionState.Connected -> Pair("CONNECTED", OrbitColors.StatusOnline)
        is ConnectionState.Connecting -> Pair("CONNECTING", OrbitColors.StatusConnecting)
        is ConnectionState.Discovering -> Pair("DISCOVERING", OrbitColors.StatusInfo)
        is ConnectionState.TemporarilyUnavailable -> Pair("PAUSED", OrbitColors.StatusConnecting)
        is ConnectionState.Error -> Pair("FAILED", OrbitColors.StatusOffline)
        ConnectionState.Disconnected -> Pair("IDLE", OrbitColors.TextTertiary)
    }

    OrbitCard(
        modifier = modifier.fillMaxWidth(),
        hasGlowBorder = connectionState is ConnectionState.Connected
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Link State",
                style = MaterialTheme.typography.titleMedium,
                color = OrbitColors.TextPrimary
            )
            OrbitStatusBadge(statusText = statusLabel, statusColor = statusColor)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!isNetworkAvailable) {
            Text(
                text = "Device has no active network connectivity.",
                style = MaterialTheme.typography.bodySmall,
                color = OrbitColors.StatusOffline
            )
        } else {
            when (connectionState) {
                is ConnectionState.Connected -> {
                    Text(
                        text = "Connected to ${connectionState.deviceName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OrbitColors.TextPrimary
                    )
                    Text(
                        text = "Target: ${connectionState.host}:${connectionState.port} • ${connectionState.latencyMs} ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = OrbitColors.TextSecondary
                    )
                }
                is ConnectionState.Connecting -> {
                    Text(
                        text = "Connecting to ${connectionState.deviceName} (${connectionState.host})...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OrbitColors.TextSecondary
                    )
                }
                is ConnectionState.TemporarilyUnavailable -> {
                    Text(
                        text = "Connection paused (${connectionState.reason}). Reconnecting to ${connectionState.deviceName}...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OrbitColors.StatusConnecting
                    )
                }
                is ConnectionState.Error -> {
                    Text(
                        text = connectionState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OrbitColors.StatusOffline
                    )
                }
                else -> {
                    Text(
                        text = "Ready to establish handshake with ORBIT Desktop.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OrbitColors.TextSecondary
                    )
                }
            }
        }
    }
}
