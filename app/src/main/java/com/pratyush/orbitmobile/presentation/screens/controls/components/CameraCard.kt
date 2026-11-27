package com.pratyush.orbitmobile.presentation.screens.controls.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.domain.model.CameraControlStatus

/**
 * Camera Remote Privacy Control Card.
 * Coral / Pink colour identity.
 * ON: Enables camera access system-wide in Windows Camera privacy settings.
 * OFF: Disables camera access.
 */
@Composable
fun CameraCard(
    camera: CameraControlStatus,
    isLaptopConnected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = Color(0xFF24141E)
    val cardBorder = Color(0xFF452033)
    val iconContainerBg = Color(0xFF9D174D)
    val iconTint = Color(0xFFFECDD3)
    val isCameraAllowed = isLaptopConnected && camera.isEnabled

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(26.dp))
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
                    imageVector = if (isCameraAllowed) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                    contentDescription = "Camera",
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Titles
            Column {
                Text(
                    text = "Camera",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Allow camera access",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFFFECDD3),
                        fontSize = 12.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Switch toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = isCameraAllowed,
                    onCheckedChange = { onToggle() },
                    enabled = isLaptopConnected,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFE11D48),
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFF381F2C),
                        disabledCheckedTrackColor = Color(0xFF6B2D4A),
                        disabledUncheckedTrackColor = Color(0xFF1E1E28)
                    )
                )
            }
        }
    }
}
