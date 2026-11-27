package com.pratyush.orbitmobile.presentation.screens.systemmonitor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import com.pratyush.orbitmobile.domain.model.TelemetryFreshness
import com.pratyush.orbitmobile.presentation.screens.systemmonitor.components.CpuHardwareCard
import com.pratyush.orbitmobile.presentation.screens.systemmonitor.components.GpuHardwareCard
import com.pratyush.orbitmobile.presentation.screens.systemmonitor.components.RamHardwareCard
import com.pratyush.orbitmobile.presentation.screens.systemmonitor.components.WifiHardwareCard

/**
 * Redesigned System Monitor screen for ORBIT Mobile.
 * Follows the visual reference closely:
 * - Pitch-black background.
 * - Sleek minimal header with back button, title, and Live indicator pill.
 * - Four vertically stacked hardware cards with vibrant identities:
 *   1. CPU (Vibrant Purple)
 *   2. RAM (Electric Blue)
 *   3. Wi-Fi (Emerald / Teal Green with dual curve)
 *   4. GPU (Amber / Orange)
 * - Smooth live animated Canvas spline graphs with glowing strokes and illuminated end-markers.
 * - Respects system insets; no bottom navigation bar.
 */
@Composable
fun SystemMonitorScreen(
    viewModel: SystemMonitorViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(Unit) {
        viewModel.onScreenStarted()
        onDispose {
            viewModel.onScreenDisposed()
        }
    }

    BackHandler {
        onNavigateBack()
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val isLive = uiState.freshness == TelemetryFreshness.LIVE

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A0F))
            .padding(
                top = topInset + 6.dp,
                start = 14.dp,
                end = 14.dp
            )
    ) {
        // 1. Sleek Minimal Header (Back button, Title, Freshness Pill)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xFF181B26), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFFE2E8F0),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "System Monitor",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                )
            }

            // Freshness Indicator Pill
            FreshnessPill(freshness = uiState.freshness)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Vertically Stacked Hardware Cards
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = maxOf(bottomInset, 16.dp) + 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // CPU Card (Purple identity)
            CpuHardwareCard(
                cpu = uiState.cpu,
                history = uiState.cpuHistory,
                isLive = isLive
            )

            // RAM Card (Blue identity)
            RamHardwareCard(
                ram = uiState.ram,
                history = uiState.ramHistory,
                isLive = isLive
            )

            // Wi-Fi Card (Emerald Green / Teal identity)
            WifiHardwareCard(
                wifi = uiState.wifi,
                downloadHistory = uiState.wifiDownloadHistory,
                uploadHistory = uiState.wifiUploadHistory,
                isLive = isLive
            )

            // GPU Card (Amber identity)
            GpuHardwareCard(
                gpu = uiState.gpu,
                history = uiState.gpuHistory,
                isLive = isLive
            )
        }
    }
}

@Composable
private fun FreshnessPill(freshness: TelemetryFreshness) {
    val (pillBg, borderColor, dotColor, textColor, text) = when (freshness) {
        TelemetryFreshness.LIVE -> Tuple5(
            Color(0xFF0F2E22),
            Color(0xFF10B981).copy(alpha = 0.5f),
            Color(0xFF10B981),
            Color(0xFF34D399),
            "Live"
        )
        TelemetryFreshness.STALE -> Tuple5(
            Color(0xFF2E220F),
            Color(0xFFF59E0B).copy(alpha = 0.5f),
            Color(0xFFF59E0B),
            Color(0xFFFBBF24),
            "Stale"
        )
        TelemetryFreshness.OFFLINE -> Tuple5(
            Color(0xFF221518),
            Color(0xFFEF4444).copy(alpha = 0.4f),
            Color(0xFFEF4444),
            Color(0xFFFCA5A5),
            "Offline"
        )
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(pillBg)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, CircleShape)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        )
    }
}

private data class Tuple5<A, B, C, D, E>(
    val a: A,
    val b: B,
    val c: C,
    val d: D,
    val e: E
)
