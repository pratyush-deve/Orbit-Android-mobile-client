package com.pratyush.orbitmobile.presentation.screens.systemmonitor.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Thermostat
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.domain.model.CpuStats
import com.pratyush.orbitmobile.domain.model.GpuStats
import com.pratyush.orbitmobile.domain.model.RamStats
import com.pratyush.orbitmobile.domain.model.WifiStats
import java.util.Locale

// -------------------------------------------------------------
// 1. CPU Card (Purple Identity)
// -------------------------------------------------------------
@Composable
fun CpuHardwareCard(
    cpu: CpuStats?,
    history: List<Float>,
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(0xFFA855F7)
    val cardBg = Color(0xFF110E1C)
    val borderColor = accentColor.copy(alpha = 0.35f)
    val iconBg = Color(0xFF2D164D)

    val animatedPercent by animateFloatAsState(
        targetValue = cpu?.utilizationPercent ?: 0f,
        animationSpec = tween(durationMillis = 400),
        label = "cpuAnim"
    )

    BaseHardwareCard(
        cardBackground = cardBg,
        borderColor = borderColor,
        modifier = modifier
    ) {
        // Header
        CardHeader(
            icon = Icons.Outlined.Memory,
            iconBg = iconBg,
            iconTint = Color(0xFFC084FC),
            title = "CPU",
            subtitle = cpu?.name ?: "Processor"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Big Metric & Live Graph
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${animatedPercent.toInt()}%",
                    style = MaterialTheme.typography.displaySmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp
                    )
                )
                Text(
                    text = "Utilization",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                )
            }

            Box(
                modifier = Modifier
                    .weight(1.35f)
                    .height(76.dp)
            ) {
                LiveHardwareGraph(
                    history = history,
                    primaryColor = accentColor,
                    isLive = isLive,
                    minValue = 0f,
                    maxValue = 100f
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Stats Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(
                icon = Icons.Outlined.Memory,
                label = "${cpu?.cores ?: 8} Cores • ${cpu?.threads ?: 16} Threads",
                modifier = Modifier.weight(1f)
            )

            StatDivider()

            val tempStr = if (cpu?.temperatureCelsius != null) {
                "${cpu.temperatureCelsius.toInt()} °C"
            } else {
                "-- °C"
            }
            StatItem(
                icon = Icons.Outlined.Thermostat,
                label = tempStr,
                modifier = Modifier.weight(1f),
                tint = if (cpu?.temperatureCelsius != null) Color(0xFFF472B6) else Color(0xFF64748B)
            )
        }
    }
}

// -------------------------------------------------------------
// 2. RAM Card (Blue Identity)
// -------------------------------------------------------------
@Composable
fun RamHardwareCard(
    ram: RamStats?,
    history: List<Float>,
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(0xFF38BDF8)
    val cardBg = Color(0xFF0E1422)
    val borderColor = accentColor.copy(alpha = 0.35f)
    val iconBg = Color(0xFF132A4A)

    val animatedPercent by animateFloatAsState(
        targetValue = ram?.utilizationPercent ?: 0f,
        animationSpec = tween(durationMillis = 400),
        label = "ramAnim"
    )

    BaseHardwareCard(
        cardBackground = cardBg,
        borderColor = borderColor,
        modifier = modifier
    ) {
        // Header
        CardHeader(
            icon = Icons.Outlined.Memory,
            iconBg = iconBg,
            iconTint = Color(0xFF60A5FA),
            title = "RAM",
            subtitle = ram?.spec ?: "Memory"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Big Metric & Live Graph
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${animatedPercent.toInt()}%",
                    style = MaterialTheme.typography.displaySmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp
                    )
                )
                Text(
                    text = if (ram != null) {
                        String.format(Locale.US, "%.1f / %.1f GB", ram.usedGb, ram.totalGb)
                    } else "0.0 / 0.0 GB",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                )
            }

            Box(
                modifier = Modifier
                    .weight(1.35f)
                    .height(76.dp)
            ) {
                LiveHardwareGraph(
                    history = history,
                    primaryColor = accentColor,
                    isLive = isLive,
                    minValue = 0f,
                    maxValue = 100f
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Stats Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val inUseStr = if (ram != null) String.format(Locale.US, "In Use: %.1f GB", ram.usedGb) else "In Use: --"
            StatItem(
                icon = Icons.Outlined.Layers,
                label = inUseStr,
                modifier = Modifier.weight(1f)
            )

            StatDivider()

            val availStr = if (ram != null) String.format(Locale.US, "Available: %.1f GB", ram.availableGb) else "Available: --"
            StatItem(
                icon = Icons.Outlined.Bolt,
                label = availStr,
                modifier = Modifier.weight(1f),
                tint = Color(0xFF60A5FA)
            )
        }
    }
}

// -------------------------------------------------------------
// 3. Wi-Fi Card (Emerald / Teal Green Identity)
// -------------------------------------------------------------
@Composable
fun WifiHardwareCard(
    wifi: WifiStats?,
    downloadHistory: List<Double>,
    uploadHistory: List<Double>,
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    val emeraldColor = Color(0xFF10B981)
    val tealColor = Color(0xFF2DD4BF)
    val cardBg = Color(0xFF0C1717)
    val borderColor = emeraldColor.copy(alpha = 0.35f)
    val iconBg = Color(0xFF11382B)

    // Normalize speeds for graph (0 to max seen or 10KB/s minimum range)
    val maxDl = downloadHistory.maxOrNull() ?: 1000.0
    val maxUl = uploadHistory.maxOrNull() ?: 1000.0
    val rawMaxSpeed = maxOf(10000.0, maxOf(maxDl, maxUl)).toFloat()
    val animatedMaxSpeed by animateFloatAsState(
        targetValue = rawMaxSpeed,
        animationSpec = tween(durationMillis = 600),
        label = "wifiScaleAnim"
    )
    val maxSpeed = maxOf(1000f, animatedMaxSpeed)

    val dlFloatList = downloadHistory.map { (it.toFloat() / maxSpeed) * 100f }
    val ulFloatList = uploadHistory.map { (it.toFloat() / maxSpeed) * 100f }

    BaseHardwareCard(
        cardBackground = cardBg,
        borderColor = borderColor,
        modifier = modifier
    ) {
        // Header
        CardHeader(
            icon = Icons.Outlined.Wifi,
            iconBg = iconBg,
            iconTint = Color(0xFF34D399),
            title = "Wi-Fi",
            subtitle = wifi?.name ?: "LAN Direct Link"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Two Speed Lines & Dual Animated Curves
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Download
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "↓",
                        color = Color(0xFF34D399),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatSpeed(wifi?.downloadSpeedBps ?: 0.0),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Upload
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "↑",
                        color = Color(0xFF38BDF8),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatSpeed(wifi?.uploadSpeedBps ?: 0.0),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1.35f)
                    .height(76.dp)
            ) {
                LiveHardwareGraph(
                    history = dlFloatList,
                    primaryColor = tealColor,
                    secondaryHistory = ulFloatList,
                    secondaryColor = emeraldColor,
                    isLive = isLive,
                    minValue = 0f,
                    maxValue = 100f
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Stats Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val signalStr = if (wifi?.signalDbm != null) "Signal: ${wifi.signalDbm} dBm" else "Signal: --"
            StatItem(
                icon = Icons.Outlined.SignalCellularAlt,
                label = signalStr,
                modifier = Modifier.weight(1f),
                tint = Color(0xFF34D399)
            )

            StatDivider()

            val latencyStr = if (wifi?.latencyMs != null) "Latency: ${wifi.latencyMs} ms" else "Latency: --"
            StatItem(
                icon = Icons.Outlined.NetworkCheck,
                label = latencyStr,
                modifier = Modifier.weight(1f),
                tint = Color(0xFF2DD4BF)
            )
        }
    }
}

// -------------------------------------------------------------
// 4. GPU Card (Amber / Orange Identity)
// -------------------------------------------------------------
@Composable
fun GpuHardwareCard(
    gpu: GpuStats?,
    history: List<Float>,
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(0xFFF59E0B)
    val cardBg = Color(0xFF19120B)
    val borderColor = accentColor.copy(alpha = 0.35f)
    val iconBg = Color(0xFF3D2712)

    val animatedPercent by animateFloatAsState(
        targetValue = gpu?.utilizationPercent ?: 0f,
        animationSpec = tween(durationMillis = 400),
        label = "gpuAnim"
    )

    BaseHardwareCard(
        cardBackground = cardBg,
        borderColor = borderColor,
        modifier = modifier
    ) {
        // Header
        CardHeader(
            icon = Icons.Outlined.Computer,
            iconBg = iconBg,
            iconTint = Color(0xFFFBBF24),
            title = "GPU",
            subtitle = gpu?.name ?: "Graphics"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Big Metric & Live Graph
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${animatedPercent.toInt()}%",
                    style = MaterialTheme.typography.displaySmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp
                    )
                )
                Text(
                    text = "Utilization",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                )
            }

            Box(
                modifier = Modifier
                    .weight(1.35f)
                    .height(76.dp)
            ) {
                LiveHardwareGraph(
                    history = history,
                    primaryColor = accentColor,
                    isLive = isLive,
                    minValue = 0f,
                    maxValue = 100f
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Stats Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val vramStr = if (gpu?.vramMb != null && gpu.vramMb > 0) {
                if (gpu.vramMb >= 1024) "${gpu.vramMb / 1024} GB" else "${gpu.vramMb} MB"
            } else "-- MB"

            StatItem(
                icon = Icons.Outlined.Memory,
                label = "VRAM: $vramStr",
                modifier = Modifier.weight(1f)
            )

            StatDivider()

            val tempStr = if (gpu?.temperatureCelsius != null) {
                "${gpu.temperatureCelsius.toInt()} °C"
            } else {
                "-- °C"
            }
            StatItem(
                icon = Icons.Outlined.Thermostat,
                label = tempStr,
                modifier = Modifier.weight(1f),
                tint = if (gpu?.temperatureCelsius != null) Color(0xFFFB923C) else Color(0xFF64748B)
            )
        }
    }
}

// -------------------------------------------------------------
// Common Card Foundations
// -------------------------------------------------------------
@Composable
private fun BaseHardwareCard(
    cardBackground: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cardBackground)
            .border(BorderStroke(1.2.dp, borderColor), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun CardHeader(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(iconBg, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF94A3B8)
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFFE2E8F0),
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(16.dp)
            .background(Color(0xFF252D3F))
    )
    Spacer(modifier = Modifier.width(12.dp))
}

private fun formatSpeed(bytesPerSec: Double): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / (1024 * 1024))
        bytesPerSec >= 1024 -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1024)
        else -> String.format(Locale.US, "%.0f B/s", bytesPerSec)
    }
}
