package com.pratyush.orbitmobile.presentation.screens.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.core.designsystem.components.OrganicWaveBackground
import com.pratyush.orbitmobile.core.designsystem.components.WaveType
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.EffectiveConnectionState
import com.pratyush.orbitmobile.domain.model.resolveEffectiveConnectionState

/**
 * Compact Connected Laptop Card.
 * Status is visually conveyed via border color and laptop icon tint (Green = Online, Red = Offline, Amber = Paused/Reconnecting)
 * without repeating "Online" / "Offline" text strings.
 * Details are split into tiny lines stacked one below another: Service type, latency ms, and OS.
 */
@Composable
fun ConnectedLaptopStatusHeader(
    connectionState: ConnectionState,
    deviceName: String? = null,
    effectiveState: EffectiveConnectionState? = null,
    modifier: Modifier = Modifier
) {
    val resolvedState = effectiveState ?: resolveEffectiveConnectionState(
        lanState = connectionState,
        peerPresence = null,
        hasRemoteLink = false,
        fallbackDeviceName = deviceName?.ifBlank { "ORBIT Windows PC" } ?: "ORBIT Windows PC"
    )

    val laptopName = when (resolvedState) {
        is EffectiveConnectionState.ConnectedLan -> resolvedState.deviceName.ifBlank { deviceName ?: "ORBIT Windows PC" }
        is EffectiveConnectionState.ConnectedRemote -> resolvedState.deviceName.ifBlank { deviceName ?: "ORBIT Windows PC" }
        is EffectiveConnectionState.Reconnecting -> resolvedState.deviceName.ifBlank { deviceName ?: "ORBIT Windows PC" }
        is EffectiveConnectionState.TemporarilyUnavailable -> resolvedState.deviceName.ifBlank { deviceName ?: "ORBIT Windows PC" }
        EffectiveConnectionState.Disconnected -> deviceName?.ifBlank { "ORBIT Windows PC" } ?: "ORBIT Windows PC"
    }

    val isOnline = resolvedState.isConnected
    val isPaused = resolvedState is EffectiveConnectionState.Reconnecting ||
        resolvedState is EffectiveConnectionState.TemporarilyUnavailable
    val latencyMs = when (resolvedState) {
        is EffectiveConnectionState.ConnectedLan -> resolvedState.latencyMs
        else -> 0L
    }

    val connType = when (resolvedState) {
        is EffectiveConnectionState.ConnectedLan -> "LAN"
        is EffectiveConnectionState.ConnectedRemote -> "REMOTE"
        is EffectiveConnectionState.Reconnecting -> "RECONNECTING"
        is EffectiveConnectionState.TemporarilyUnavailable -> "UNAVAILABLE"
        EffectiveConnectionState.Disconnected -> "UNAVAILABLE"
    }

    // Dynamic status color: Green for online, Amber for paused/reconnecting, Red for offline
    val statusColor by animateColorAsState(
        targetValue = when {
            isOnline -> Color(0xFF10B981)
            isPaused -> Color(0xFFF59E0B)
            else -> Color(0xFFEF4444)
        },
        animationSpec = tween(300),
        label = "status_color"
    )

    val iconBgColor by animateColorAsState(
        targetValue = when {
            isOnline -> Color(0xFF132B1E)
            isPaused -> Color(0xFF2C2210)
            else -> Color(0xFF2D1616)
        },
        animationSpec = tween(300),
        label = "icon_bg_color"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF161521))
            .border(1.5.dp, statusColor, RoundedCornerShape(20.dp))
    ) {
        // Subtle ambient organic wave in bottom-right corner
        OrganicWaveBackground(
            waveColor = Color(0xFF271A3B),
            waveType = WaveType.HEADER_DOUBLE_WAVE,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Top row: Laptop Icon (colored red/green/amber) + Laptop Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(iconBgColor, RoundedCornerShape(12.dp))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Laptop,
                        contentDescription = "Laptop",
                        tint = statusColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = laptopName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 16.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Details split into tiny text stacked one below another:
            // 1. service type LAN
            // 2. latency in ms
            // 3. windows 11
            Column(
                modifier = Modifier.padding(start = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Wifi,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "Service: $connType",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Speed,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = when {
                            resolvedState is EffectiveConnectionState.ConnectedRemote -> "Firebase"
                            latencyMs > 0 -> "$latencyMs ms"
                            isOnline -> "< 1 ms"
                            else -> "— ms"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Devices,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "Windows 11",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    )
                }
            }
        }
    }
}
