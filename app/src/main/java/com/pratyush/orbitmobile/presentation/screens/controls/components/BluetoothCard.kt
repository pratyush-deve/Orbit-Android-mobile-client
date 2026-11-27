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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.domain.model.BluetoothControlStatus

/**
 * Bluetooth Device Management Card.
 * Cyan / Deep Slate Blue colour identity distinct from Wi-Fi.
 * Displays Bluetooth status and chevron affordance to open the Bluetooth device sheet.
 */
@Composable
fun BluetoothCard(
    bluetooth: BluetoothControlStatus,
    isLaptopConnected: Boolean,
    onOpenManagement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = Color(0xFF111E2A)
    val cardBorder = Color(0xFF163246)
    val iconContainerBg = Color(0xFF0E7490)
    val iconTint = Color(0xFF67E8F9)
    val isBtActive = isLaptopConnected && bluetooth.isEnabled

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
            // Top icon container
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconContainerBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isBtActive) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
                    contentDescription = "Bluetooth",
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Titles
            Column {
                Text(
                    text = "Bluetooth",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Connect to devices",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFFA5F3FC),
                        fontSize = 12.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Bottom row with chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2E3E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Manage Bluetooth",
                        tint = Color(0xFFE2E8F0),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
