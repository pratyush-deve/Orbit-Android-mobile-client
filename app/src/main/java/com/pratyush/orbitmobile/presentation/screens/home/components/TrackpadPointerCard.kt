package com.pratyush.orbitmobile.presentation.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.core.designsystem.components.OrganicWaveBackground
import com.pratyush.orbitmobile.core.designsystem.components.WaveType
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors

/**
 * Trackpad feature card matching the design template.
 * Indigo/blue fluid wave, touchpad gesture icon, and circular action button.
 */
@Composable
fun TrackpadPointerCard(
    onOpenTrackpad: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = OrbitColors.Feature.Trackpad

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(175.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(tokens.cardSurface)
            .clickable { onOpenTrackpad() }
    ) {
        // Fluid blue wave in top-right corner
        OrganicWaveBackground(
            waveColor = tokens.waveColor,
            waveType = WaveType.TOP_RIGHT,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Feature Icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(tokens.iconContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.TouchApp,
                    contentDescription = "Trackpad",
                    tint = tokens.iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Bottom Content: Title, Subtitle, and Circular Arrow Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Trackpad",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            color = Color.White
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Control cursor & gestures",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        ),
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(tokens.arrowContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open",
                        tint = tokens.arrowTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
