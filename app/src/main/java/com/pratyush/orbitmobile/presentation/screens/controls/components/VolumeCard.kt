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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Full-width Volume Control Card with responsive draggable horizontal slider.
 * Authoritative Windows Core Audio master volume representation with live bidirectional sync.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeCard(
    volumePercentage: Int,
    isMuted: Boolean,
    isVolumeInitialized: Boolean,
    isLaptopConnected: Boolean,
    onVolumeChanged: (Int) -> Unit,
    onVolumeChangeFinished: (Int) -> Unit,
    onToggleMute: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cardBg = Color(0xFF1D152D)
    val cardBorder = if (isMuted) Color(0xFF4C2840) else Color(0xFF382256)
    val iconContainerBg = if (isMuted) Color(0xFF6B21A8).copy(alpha = 0.5f) else Color(0xFF7C3AED)
    val iconTint = Color(0xFFEDE9FE)

    // Local float state for silky-smooth slider interaction while dragging
    var sliderPosition by remember(volumePercentage) {
        mutableFloatStateOf(volumePercentage.toFloat())
    }

    val isInteractive = isLaptopConnected && isVolumeInitialized

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(26.dp))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Icon + Title + Status + Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(iconContainerBg)
                            .clickable(enabled = isInteractive, onClick = onToggleMute),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isMuted || (isVolumeInitialized && volumePercentage == 0) -> Icons.AutoMirrored.Filled.VolumeOff
                                volumePercentage < 35 -> Icons.AutoMirrored.Filled.VolumeMute
                                else -> Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = if (isMuted) "Unmute Volume" else "Mute Volume",
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Volume",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            )

                            if (isVolumeInitialized) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isMuted) Color(0xFFEF4444).copy(alpha = 0.2f)
                                            else Color(0xFF10B981).copy(alpha = 0.15f)
                                        )
                                        .clickable(enabled = isInteractive, onClick = onToggleMute)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isMuted) "MUTED" else "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isMuted) Color(0xFFF87171) else Color(0xFF34D399),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = when {
                                !isLaptopConnected -> "Laptop offline"
                                !isVolumeInitialized -> "Reading laptop volume..."
                                isMuted -> "Output muted (tap icon to unmute)"
                                else -> "Laptop audio (tap icon to mute)"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isMuted) Color(0xFFFCA5A5) else Color(0xFFDDD6FE),
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Text(
                    text = if (isVolumeInitialized) "${sliderPosition.roundToInt()}%" else "--%",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = if (isMuted) Color(0xFF94A3B8) else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                )
            }

            // Draggable Slider
            Slider(
                value = sliderPosition,
                onValueChange = { newValue ->
                    sliderPosition = newValue
                    onVolumeChanged(newValue.roundToInt())
                },
                onValueChangeFinished = {
                    onVolumeChangeFinished(sliderPosition.roundToInt())
                },
                valueRange = 0f..100f,
                enabled = isInteractive,
                colors = SliderDefaults.colors(
                    thumbColor = if (isMuted) Color(0xFFF87171) else Color.White,
                    activeTrackColor = if (isMuted) Color(0xFF9333EA) else Color(0xFFA78BFA),
                    inactiveTrackColor = Color(0xFF32244A),
                    disabledThumbColor = Color(0xFF64748B),
                    disabledActiveTrackColor = Color(0xFF473A60),
                    disabledInactiveTrackColor = Color(0xFF1E1E28)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
