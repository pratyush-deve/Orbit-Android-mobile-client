package com.pratyush.orbitmobile.presentation.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.core.designsystem.components.OrbitCard
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import com.pratyush.orbitmobile.presentation.components.ScreenHeader
import com.pratyush.orbitmobile.presentation.screens.settings.actions.SettingsAction
import com.pratyush.orbitmobile.presentation.screens.settings.components.SettingsToggleRow

/**
 * Settings screen orchestrator.
 * Fully scrollable with status bar and navigation bar inset protection.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToAbout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = navBarBottom + 104.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(
            title = "Settings",
            subtitle = "Preferences, connections, and system options"
        )

        OrbitCard(modifier = Modifier.fillMaxWidth()) {
            SettingsToggleRow(
                title = "Keep ORBIT connected in background",
                description = "Maintain live telemetry and clipboard sync even when app is closed",
                checked = uiState.config.keepConnectedInBackground,
                onCheckedChange = { viewModel.onAction(SettingsAction.ToggleKeepConnectedInBackground(it)) }
            )

            HorizontalDivider(
                color = OrbitColors.BorderSubtle,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            SettingsToggleRow(
                title = "Auto-Connect",
                description = "Attempt automatic connection to primary host on start",
                checked = uiState.config.autoConnect,
                onCheckedChange = { viewModel.onAction(SettingsAction.ToggleAutoConnect(it)) }
            )

            HorizontalDivider(
                color = OrbitColors.BorderSubtle,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            SettingsToggleRow(
                title = "Haptic Feedback",
                description = "Vibrate on button clicks and status transitions",
                checked = uiState.config.hapticFeedbackEnabled,
                onCheckedChange = { viewModel.onAction(SettingsAction.ToggleHapticFeedback(it)) }
            )
        }

        // Trackpad Settings Card
        OrbitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.TouchApp,
                        contentDescription = null,
                        tint = Color(0xFF93C5FD),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Trackpad & Gestures",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    )
                }

                // Cursor Sensitivity Slider
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cursor Sensitivity",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%.1fx", uiState.config.trackpadSensitivity),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color(0xFF93C5FD),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Slider(
                        value = uiState.config.trackpadSensitivity,
                        onValueChange = { viewModel.onAction(SettingsAction.UpdateTrackpadSensitivity(it)) },
                        valueRange = 0.5f..3.0f,
                        steps = 24,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF93C5FD),
                            activeTrackColor = Color(0xFF3B82F6),
                            inactiveTrackColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(
                    color = OrbitColors.BorderSubtle,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                SettingsToggleRow(
                    title = "Tap to Click",
                    description = "Single tap on trackpad surface performs left click",
                    checked = uiState.config.trackpadTapToClick,
                    onCheckedChange = { viewModel.onAction(SettingsAction.ToggleTrackpadTapToClick(it)) }
                )

                HorizontalDivider(
                    color = OrbitColors.BorderSubtle,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                SettingsToggleRow(
                    title = "Two-Finger Tap Right Click",
                    description = "Two-finger tap on trackpad surface performs right click",
                    checked = uiState.config.trackpadTwoFingerTapRightClick,
                    onCheckedChange = { viewModel.onAction(SettingsAction.ToggleTrackpadTwoFingerTapRightClick(it)) }
                )
            }
        }

        // Background Battery Optimization Info Card
        OrbitCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BatteryChargingFull,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Background Connectivity & Battery",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    )
                }

                Text(
                    text = "ORBIT runs an efficient background foreground service with a persistent notification to report battery, Wi-Fi, and storage telemetry to your Windows PC without requiring the app UI to stay open.\n\n" +
                           "Notice for Xiaomi, Samsung, OnePlus, and Oppo users: Aggressive OEM battery management can freeze background LAN network traffic. If telemetry stops updating on your PC when your phone is locked, set ORBIT's App Battery Usage to 'Unrestricted' in system settings.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                )
            }
        }

        // About ORBIT Navigation Card
        OrbitCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onNavigateToAbout
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "About",
                        tint = Color(0xFFA78BFA),
                        modifier = Modifier.size(24.dp)
                    )

                    Column {
                        Text(
                            text = "About ORBIT",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Architecture & Companion Information",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
