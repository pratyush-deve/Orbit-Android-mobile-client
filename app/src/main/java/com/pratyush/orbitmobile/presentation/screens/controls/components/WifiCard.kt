package com.pratyush.orbitmobile.presentation.screens.controls.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.domain.model.WifiControlStatus

/**
 * Wi-Fi Status & Control Card following dark Material 3 Expressive design.
 * Deep blue color identity. Displays live adapter state, SSID, and signal.
 * Clicking opens Wi-Fi Management sheet for scanning, connecting, and disconnecting.
 */
@Composable
fun WifiCard(
    wifi: WifiControlStatus,
    isLaptopConnected: Boolean,
    onOpenManagement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = Color(0xFF131A2E)
    val cardBorder = Color(0xFF1E294B)
    val iconContainerBg = Color(0xFF1E3A8A)
    val iconTint = Color(0xFF60A5FA)
    val isWifiActive = isLaptopConnected && wifi.isEnabled && wifi.isConnected

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(26.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenManagement
            )
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top row with icon container and chevron affordance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(iconContainerBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isWifiActive) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                        contentDescription = "Wi-Fi",
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1C2844)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Manage Wi-Fi",
                        tint = Color(0xFFE2E8F0),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Titles
            Column {
                Text(
                    text = "Wi-Fi",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (!isLaptopConnected) {
                        "Offline"
                    } else if (!wifi.isEnabled) {
                        "Disabled"
                    } else if (wifi.isConnected && !wifi.ssid.isNullOrBlank()) {
                        wifi.ssid + (wifi.signal?.let { " • $it%" } ?: "")
                    } else {
                        "Not connected"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF93C5FD),
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Status Pill
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isWifiActive) Color(0xFF064E3B) else Color(0xFF1E293B))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                color = if (isWifiActive) Color(0xFF22C55E) else Color(0xFF94A3B8),
                                shape = CircleShape
                            )
                    )

                    Text(
                        text = if (!isLaptopConnected) {
                            "Offline"
                        } else if (!wifi.isEnabled) {
                            "Disabled"
                        } else if (isWifiActive) {
                            "Connected"
                        } else {
                            "Not connected"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isWifiActive) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
