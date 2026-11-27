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
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.WbSunny
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
 * Full-width Display Brightness Card with warm amber/yellow styling.
 * Draggable horizontal slider that controls real Windows laptop screen brightness via WMI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrightnessCard(
    brightnessPercentage: Int,
    isBrightnessAvailable: Boolean,
    isBrightnessInitialized: Boolean,
    isLaptopConnected: Boolean,
    onBrightnessChanged: (Int) -> Unit,
    onBrightnessChangeFinished: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Warm amber / yellow palette
    val cardBg = Color(0xFF1E1711)
    val cardBorder = Color(0xFF452B14)
    val iconContainerBg = Color(0xFFD97706)
    val iconTint = Color(0xFFFEF3C7)

    var sliderPosition by remember(brightnessPercentage) {
        mutableFloatStateOf(brightnessPercentage.toFloat())
    }

    val isInteractive = isLaptopConnected && isBrightnessAvailable && isBrightnessInitialized

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
                            .background(
                                if (isBrightnessAvailable) iconContainerBg
                                else Color(0xFF4A3B2C)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                !isBrightnessAvailable -> Icons.Default.BrightnessLow
                                brightnessPercentage < 35 -> Icons.Default.BrightnessLow
                                brightnessPercentage < 70 -> Icons.Default.BrightnessMedium
                                else -> Icons.Default.WbSunny
                            },
                            contentDescription = "Brightness",
                            tint = if (isBrightnessAvailable) iconTint else Color(0xFF94A3B8),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Brightness",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            )

                            if (isBrightnessInitialized && !isBrightnessAvailable) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF64748B).copy(alpha = 0.25f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "UNAVAILABLE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF94A3B8),
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
                                !isBrightnessInitialized -> "Querying display..."
                                !isBrightnessAvailable -> "External display / unsupported"
                                else -> "Laptop display brightness"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isBrightnessAvailable) Color(0xFFFDE68A) else Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Text(
                    text = if (isBrightnessInitialized && isBrightnessAvailable) "${sliderPosition.roundToInt()}%" else "--%",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = if (isBrightnessAvailable) Color.White else Color(0xFF64748B),
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
                    onBrightnessChanged(newValue.roundToInt())
                },
                onValueChangeFinished = {
                    onBrightnessChangeFinished(sliderPosition.roundToInt())
                },
                valueRange = 0f..100f,
                enabled = isInteractive,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFFF59E0B),
                    inactiveTrackColor = Color(0xFF382312),
                    disabledThumbColor = Color(0xFF64748B),
                    disabledActiveTrackColor = Color(0xFF452B14),
                    disabledInactiveTrackColor = Color(0xFF1E1E28)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
