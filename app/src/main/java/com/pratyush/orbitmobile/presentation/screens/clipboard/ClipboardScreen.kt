package com.pratyush.orbitmobile.presentation.screens.clipboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import com.pratyush.orbitmobile.domain.model.ClipboardItem
import com.pratyush.orbitmobile.presentation.screens.home.HomeViewModel
import com.pratyush.orbitmobile.presentation.screens.home.ManualSyncStatus
import com.pratyush.orbitmobile.presentation.screens.home.actions.HomeAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ORBIT Mobile Clipboard Sync Screen (Dark Pixel Material You).
 * Premium dark-themed interface with organic shapes, strict section color identities,
 * compact header, independent Automatic Toggle and Manual Sync Now controls,
 * and full clipboard synchronization functionality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardScreen(
    viewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val theme = OrbitColors.ClipboardTheme

    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var selectedInspectItem by remember { mutableStateOf<ClipboardItem?>(null) }
    var isSortReversed by remember { mutableStateOf(false) }

    // Explicit back handler guaranteeing predictable Android system back navigation
    BackHandler {
        if (selectedInspectItem != null) {
            selectedInspectItem = null
        } else if (showClearConfirmDialog) {
            showClearConfirmDialog = false
        } else {
            onNavigateBack()
        }
    }

    // Refresh current clipboard content whenever screen is viewed
    LaunchedEffect(Unit) {
        viewModel.onAction(HomeAction.AppResumed)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.ScreenBackground)
    ) {
        // Organic Material You Blobs Background (Behind content)
        PixelOrganicBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Compact Header Row
            CompactClipboardHeader(
                onBack = onNavigateBack,
                hasHistory = uiState.clipboardHistory.isNotEmpty(),
                onClearHistory = { showClearConfirmDialog = true }
            )

            // 2. Main Scrollable Content
            val displayedHistory = remember(uiState.clipboardHistory, isSortReversed) {
                if (isSortReversed) uiState.clipboardHistory.reversed() else uiState.clipboardHistory
            }

            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val bottomPadding = navBarBottom + 104.dp

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: Sync Active Card (Violet / Lavender) with both Automatic Toggle and Manual Sync Now
                item {
                    SyncActiveCard(
                        isActive = uiState.isClipboardSyncActive,
                        isSyncing = uiState.isManualSyncing,
                        syncStatus = uiState.manualSyncStatus,
                        onToggle = { viewModel.onAction(HomeAction.ToggleClipboardSync(it)) },
                        onSyncNow = { viewModel.onAction(HomeAction.SyncClipboardNow) }
                    )
                }

                // Section 2: Current Phone Clipboard Card (Blue / Cyan) - focused on preview & copy
                item {
                    CurrentPhoneClipboardCard(
                        clipText = uiState.currentClipText,
                        onCopy = { text -> viewModel.copyToLocalClipboard(text) }
                    )
                }

                // Section 3: Android Security Notice (Muted Amber / Gold)
                item {
                    AndroidSecurityNotice()
                }

                // Section 4: Synced History Header
                item {
                    SyncedHistoryHeader(
                        count = uiState.clipboardHistory.size,
                        isSortReversed = isSortReversed,
                        onToggleSort = { isSortReversed = !isSortReversed }
                    )
                }

                // Section 5: Synced History Cards (Indigo / Blue Base)
                if (displayedHistory.isEmpty()) {
                    item {
                        EmptyHistoryState()
                    }
                } else {
                    items(displayedHistory, key = { it.id }) { item ->
                        SyncedHistoryCard(
                            item = item,
                            onClick = { selectedInspectItem = item },
                            onCopy = { viewModel.copyToLocalClipboard(item.content) }
                        )
                    }
                }
            }
        }

        // Full Content Inspection Dialog
        selectedInspectItem?.let { item ->
            ClipboardInspectionDialog(
                item = item,
                onDismiss = { selectedInspectItem = null },
                onCopy = {
                    viewModel.copyToLocalClipboard(item.content)
                    selectedInspectItem = null
                }
            )
        }

        // Clear History Confirmation Dialog
        if (showClearConfirmDialog) {
            ClearHistoryConfirmDialog(
                onDismiss = { showClearConfirmDialog = false },
                onConfirm = {
                    showClearConfirmDialog = false
                    viewModel.clearClipboardHistory()
                }
            )
        }
    }
}

/**
 * Organic curved ambient blobs drawn on background Canvas.
 * Creates smooth soft tonal lighting without heavy PNG assets.
 */
@Composable
private fun PixelOrganicBackground(
    modifier: Modifier = Modifier
) {
    val theme = OrbitColors.ClipboardTheme

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Top-right organic violet blob
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    theme.BlobTopRight.copy(alpha = 0.85f),
                    theme.BlobTopRight.copy(alpha = 0.35f),
                    Color.Transparent
                ),
                center = Offset(width * 0.95f, height * 0.04f),
                radius = width * 0.72f
            )
        )

        // Bottom-left organic indigo blob
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    theme.BlobBottomLeft.copy(alpha = 0.95f),
                    theme.BlobBottomLeft.copy(alpha = 0.4f),
                    Color.Transparent
                ),
                center = Offset(width * 0.05f, height * 0.92f),
                radius = width * 0.85f
            )
        )

        // Bottom-right subtle violet blob
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    theme.BlobBottomRight.copy(alpha = 0.7f),
                    Color.Transparent
                ),
                center = Offset(width * 0.92f, height * 0.88f),
                radius = width * 0.65f
            )
        )
    }
}

/**
 * Compact top row: Back button, bold title, and delete/clear action.
 */
@Composable
private fun CompactClipboardHeader(
    onBack: () -> Unit,
    hasHistory: Boolean,
    onClearHistory: () -> Unit
) {
    val theme = OrbitColors.ClipboardTheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Back Button
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF141826))
                .border(1.dp, Color(0xFF252D45), CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Title
        Text(
            text = "Clipboard Sync",
            style = MaterialTheme.typography.titleLarge.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        )

        // Clear History Action (Coral / Red)
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (hasHistory) theme.DeleteButtonBg else Color(0xFF1A1519))
                .border(1.dp, if (hasHistory) theme.DeleteBorder else Color(0xFF332024), CircleShape)
                .clickable(enabled = hasHistory, onClick = onClearHistory),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "Clear History",
                tint = if (hasHistory) theme.DeleteButtonTint else Color(0xFF75454C),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Sync Active Card (Violet / Lavender Identity).
 * Combines independent Automatic Sync Toggle and Manual Sync Now button in a clean arrangement.
 */
@Composable
private fun SyncActiveCard(
    isActive: Boolean,
    isSyncing: Boolean,
    syncStatus: ManualSyncStatus,
    onToggle: (Boolean) -> Unit,
    onSyncNow: () -> Unit
) {
    val theme = OrbitColors.ClipboardTheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(theme.SyncActiveCardBg)
            .border(1.dp, theme.SyncActiveCardBorder, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Squircle Icon Container
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(theme.SyncActiveIconBg)
                .border(1.dp, Color(0xFF2B3F78), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Sync,
                contentDescription = null,
                tint = theme.SyncActiveIconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        // Text Content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            Text(
                text = if (isActive) "Sync Active" else "Sync Paused",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isActive)
                    "Foreground copy events automatically sync to Windows"
                else
                    "Automatic clipboard synchronization is paused",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF8F9BB3),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            )
        }

        // Right column: Automatic toggle on top, manual Sync Now button below
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Automatic Sync Toggle
            SmoothPillSwitch(
                checked = isActive,
                onCheckedChange = onToggle
            )

            // Independent Manual Sync Now Button
            SyncNowButton(
                isSyncing = isSyncing,
                syncStatus = syncStatus,
                onClick = onSyncNow
            )
        }
    }
}

/**
 * Independent Manual "Sync Now" Button.
 * Shows loading indicator, success check, or failure message, and disables while syncing.
 */
@Composable
private fun SyncNowButton(
    isSyncing: Boolean,
    syncStatus: ManualSyncStatus,
    onClick: () -> Unit
) {
    val (bg, border, contentColor) = when (syncStatus) {
        is ManualSyncStatus.Success -> Triple(Color(0xFF132E22), Color(0xFF1E523A), Color(0xFF34D399))
        is ManualSyncStatus.Failure -> Triple(Color(0xFF2E1519), Color(0xFF57222B), Color(0xFFF87171))
        is ManualSyncStatus.InProgress -> Triple(Color(0xFF221A3D), Color(0xFF3B2968), Color(0xFFA78BFA))
        is ManualSyncStatus.Idle -> Triple(Color(0xFF1E1736), Color(0xFF33245B), Color(0xFFA78BFA))
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .clickable(enabled = !isSyncing, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            when {
                isSyncing || syncStatus is ManualSyncStatus.InProgress -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        color = contentColor,
                        strokeWidth = 1.8.dp
                    )
                    Text(
                        text = "Syncing...",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = contentColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }
                syncStatus is ManualSyncStatus.Success -> {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Success",
                        tint = contentColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Synced!",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = contentColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }
                syncStatus is ManualSyncStatus.Failure -> {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Failed",
                        tint = contentColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Failed",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = contentColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Outlined.Sync,
                        contentDescription = "Sync Now",
                        tint = contentColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Sync Now",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = contentColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Animated Material You Pill Switch.
 */
@Composable
private fun SmoothPillSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val theme = OrbitColors.ClipboardTheme

    val trackColor by animateColorAsState(
        targetValue = if (checked) theme.SyncActiveSwitchTrackOn else Color(0xFF22283D),
        animationSpec = tween(250),
        label = "switch_track"
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 4.dp,
        animationSpec = tween(250),
        label = "switch_thumb"
    )

    Box(
        modifier = Modifier
            .size(width = 54.dp, height = 30.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onCheckedChange(!checked)
            }
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) theme.SyncActiveSwitchThumbOn else Color(0xFF7E8AAB))
        )
    }
}

/**
 * Current Phone Clipboard Card (Blue / Cyan Identity).
 * Focused exclusively on displaying and copying the current phone clipboard.
 */
@Composable
private fun CurrentPhoneClipboardCard(
    clipText: String?,
    onCopy: (String) -> Unit
) {
    val theme = OrbitColors.ClipboardTheme
    val hasContent = !clipText.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(theme.CurrentClipCardBg)
            .border(1.dp, theme.CurrentClipCardBorder, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        // Header Row (without Sync to PC button)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.CurrentClipIconBg)
                    .border(1.dp, Color(0xFF1E355E), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Smartphone,
                    contentDescription = null,
                    tint = theme.CurrentClipIconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = "Current Phone Clipboard",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontSize = 15.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Inner Content Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.InnerClipContainerBg)
                .border(1.dp, theme.InnerClipContainerBorder, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Content Type Icon
                val isUrl = clipText?.startsWith("http://", ignoreCase = true) == true ||
                        clipText?.startsWith("https://", ignoreCase = true) == true

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.InnerClipIconBg)
                        .border(1.dp, Color(0xFF223B69), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isUrl) Icons.Outlined.Link else Icons.Outlined.TextFields,
                        contentDescription = null,
                        tint = theme.InnerClipIconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Text Content Preview (Wrapped, no overflow)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    if (hasContent) {
                        Text(
                            text = clipText ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFFE2E8F0),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            ),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "Clipboard is currently empty",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF64748B),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                fontSize = 13.sp
                            )
                        )
                    }
                }

                // Copy Action Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF16233F))
                        .clickable(enabled = hasContent) {
                            if (clipText != null) onCopy(clipText)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy",
                        tint = if (hasContent) Color(0xFF94A3B8) else Color(0xFF4A5568),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Android Background Security Information Notice (Muted Amber / Gold Identity).
 */
@Composable
private fun AndroidSecurityNotice() {
    val theme = OrbitColors.ClipboardTheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(theme.InfoNoticeBg)
            .border(1.dp, theme.InfoNoticeBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFF292015)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = theme.InfoNoticeIconTint,
                modifier = Modifier.size(15.dp)
            )
        }

        Text(
            text = "Android restricts background clipboard reading. Text copied while outside ORBIT Mobile is synchronized when you return to the application.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = theme.InfoNoticeText,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Synced History Section Header with count and sort toggle.
 */
@Composable
private fun SyncedHistoryHeader(
    count: Int,
    isSortReversed: Boolean,
    onToggleSort: () -> Unit
) {
    val theme = OrbitColors.ClipboardTheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1B2342)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentPaste,
                    contentDescription = null,
                    tint = theme.HistoryIconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = "Synced History ($count)",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
        }

        // Sort / Filter Action Icon
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF151A2E))
                .border(1.dp, Color(0xFF232B47), RoundedCornerShape(10.dp))
                .clickable(onClick = onToggleSort),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = "Sort",
                tint = if (isSortReversed) theme.HistoryBadgeText else Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Synced History Card (Consistent Indigo / Blue Identity).
 */
@Composable
private fun SyncedHistoryCard(
    item: ClipboardItem,
    onClick: () -> Unit,
    onCopy: () -> Unit
) {
    val theme = OrbitColors.ClipboardTheme
    val dateFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeStr = remember(item.timestamp) { dateFormat.format(Date(item.timestamp)) }

    val isWindows = item.origin.equals("Windows", ignoreCase = true)
    val isUrl = item.content.startsWith("http://", ignoreCase = true) ||
            item.content.startsWith("https://", ignoreCase = true) ||
            item.type.equals("URL", ignoreCase = true)
    val isCode = item.type.equals("Code", ignoreCase = true) ||
            item.content.contains("{") && item.content.contains("}") ||
            item.content.startsWith("import ") ||
            item.content.startsWith("package ")

    val iconVector: ImageVector = when {
        isUrl -> Icons.Outlined.Link
        isCode -> Icons.Outlined.Code
        else -> Icons.Outlined.TextFields
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(theme.HistoryCardBg)
            .border(1.dp, theme.HistoryCardBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Content-Type Squircle Icon
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(theme.HistoryIconBg)
                .border(1.dp, Color(0xFF26335C), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = theme.HistoryIconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        // Content & Meta Column
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Type badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme.HistoryBadgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = when {
                            isUrl -> "URL"
                            isCode -> "Code"
                            else -> "Text"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = theme.HistoryBadgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Text(text = "•", color = Color(0xFF4B5563), fontSize = 10.sp)

                // Source badge (Phone vs Windows PC)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isWindows) theme.SourceWindowsBg else theme.SourcePhoneBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isWindows) "Windows PC" else "Phone",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isWindows) theme.SourceWindowsText else theme.SourcePhoneText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Text(text = "•", color = Color(0xFF4B5563), fontSize = 10.sp)

                // Timestamp
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF8F9BB3),
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Clipboard preview
            Text(
                text = item.content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFFF1F5F9),
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Copy Button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(theme.HistoryCopyBtnBg)
                .clickable(onClick = onCopy),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy",
                tint = theme.HistoryCopyBtnTint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Empty History State.
 */
@Composable
private fun EmptyHistoryState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentPaste,
                contentDescription = null,
                tint = Color(0xFF334155),
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "No clipboard history yet",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF8F9BB3),
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = "Copy text on this device or PC to synchronize automatically",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF475569),
                    fontSize = 12.sp
                )
            )
        }
    }
}

/**
 * Full Content Inspection Material 3 Dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipboardInspectionDialog(
    item: ClipboardItem,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF131828))
                .border(1.dp, Color(0xFF26335C), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            // Dialog Header with Close Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Clipboard Item",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )
                    Text(
                        text = "Origin: ${item.origin} • Type: ${item.type}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF8F9BB3),
                            fontSize = 11.5.sp
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1D2644))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Scrollable Content Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0C101C))
                    .border(1.dp, Color(0xFF1B233D), RoundedCornerShape(14.dp))
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFFF1F5F9),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Copy to Clipboard Action Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(Color(0xFF2563EB))
                    .clickable(onClick = onCopy)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Copy to Clipboard",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Clear History Confirmation Material 3 Dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearHistoryConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF161A29))
                .border(1.dp, Color(0xFF28324D), RoundedCornerShape(24.dp))
                .padding(22.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2D161A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "Clear History",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Are you sure you want to clear all synchronized clipboard items on this phone? This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color(0xFF8F9BB3),
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFDC2626))
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}
