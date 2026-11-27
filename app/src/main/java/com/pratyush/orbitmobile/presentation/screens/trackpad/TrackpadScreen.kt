package com.pratyush.orbitmobile.presentation.screens.trackpad

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import com.pratyush.orbitmobile.domain.model.ButtonState
import com.pratyush.orbitmobile.domain.model.MouseButton

/**
 * Full-Screen Trackpad implementation for ORBIT Mobile.
 *
 * Dedicated interaction surface providing single-finger relative cursor movement,
 * multi-touch gesture processing, and physical rectangular Left & Right click zones.
 */
@Composable
fun TrackpadScreen(
    viewModel: TrackpadViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // Safety: release any held buttons if user presses back or leaves screen
    BackHandler {
        viewModel.onScreenDisposed()
        onNavigateBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onScreenDisposed()
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitColors.PitchBlack)
            .padding(
                top = topInset + 6.dp,
                bottom = maxOf(bottomInset, 10.dp) + 6.dp,
                start = 14.dp,
                end = 14.dp
            ),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Sleek Minimal Header (Back button, Title, Connection Status Pill)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = {
                        viewModel.onScreenDisposed()
                        onNavigateBack()
                    },
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

                Column {
                    Text(
                        text = "Trackpad",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        text = if (uiState.isConnected) uiState.deviceName else "Disconnected",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (uiState.isConnected) Color(0xFF94A3B8) else Color(0xFFEF4444),
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // Connection Status and Gesture Indicator Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF141824))
                    .border(1.dp, Color(0xFF273248), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (uiState.isConnected) OrbitColors.StatusOnline else OrbitColors.StatusOffline,
                            shape = CircleShape
                        )
                )
                Text(
                    text = if (uiState.isDragging) "DRAGGING" else if (uiState.isConnected) "ACTIVE" else "OFFLINE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (uiState.isDragging) Color(0xFFC084FC) else if (uiState.isConnected) Color(0xFF93C5FD) else Color(0xFFFCA5A5),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Main Full-Screen Touchpad Surface
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0F121C))
                .border(1.dp, Color(0xFF1E2538), RoundedCornerShape(24.dp))
                .trackpadGestureHandler(
                    sensitivity = uiState.sensitivity,
                    tapToClickEnabled = uiState.tapToClickEnabled,
                    twoFingerTapRightClickEnabled = uiState.twoFingerTapRightClickEnabled,
                    onMove = { dx, dy -> viewModel.onAction(TrackpadAction.MoveEvent(dx, dy)) },
                    onButton = { button, state -> viewModel.onAction(TrackpadAction.ButtonEvent(button, state)) },
                    onScroll = { dx, dy -> viewModel.onAction(TrackpadAction.ScrollEvent(dx, dy)) },
                    onZoom = { delta -> viewModel.onAction(TrackpadAction.ZoomEvent(delta)) },
                    onDragStateChanged = { isDragging -> viewModel.onAction(TrackpadAction.DragStateChanged(isDragging)) }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Subtle watermarked icon & gesture cues
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.TouchApp,
                    contentDescription = null,
                    tint = Color(0xFF273552).copy(alpha = 0.5f),
                    modifier = Modifier.size(54.dp)
                )
                Text(
                    text = "Single finger move • Tap to click\nTwo fingers scroll & right-click",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF3B4864).copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Permanent Physical Click Zones (Left Click & Right Click)
        // Generous tactile buttons that comfortably sit at the bottom of the screen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(108.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Click Zone
            PhysicalClickZone(
                label = "LEFT CLICK",
                subLabel = "Primary • Tap or Hold",
                icon = Icons.Outlined.Mouse,
                isPressedExternally = uiState.isLeftButtonPressed,
                onPress = {
                    viewModel.onAction(TrackpadAction.ButtonEvent(MouseButton.LEFT, ButtonState.DOWN))
                },
                onRelease = {
                    viewModel.onAction(TrackpadAction.ButtonEvent(MouseButton.LEFT, ButtonState.UP))
                },
                modifier = Modifier.weight(1f)
            )

            // Right Click Zone
            PhysicalClickZone(
                label = "RIGHT CLICK",
                subLabel = "Secondary • Menu",
                icon = Icons.Outlined.Mouse,
                isPressedExternally = uiState.isRightButtonPressed,
                onPress = {
                    viewModel.onAction(TrackpadAction.ButtonEvent(MouseButton.RIGHT, ButtonState.DOWN))
                },
                onRelease = {
                    viewModel.onAction(TrackpadAction.ButtonEvent(MouseButton.RIGHT, ButtonState.UP))
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Prominent physical click zone with subtle borders, rounded corners,
 * tactile indicator bar, Material 3 styling, and pressed-state visual feedback.
 */
@Composable
private fun PhysicalClickZone(
    label: String,
    subLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPressedExternally: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLocallyPressed by remember { mutableStateOf(false) }
    val isPressed = isLocallyPressed || isPressedExternally

    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)

    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) Color(0xFF1E2D4A) else Color(0xFF111520),
        animationSpec = tween(durationMillis = 60),
        label = "clickZoneBg"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isPressed) Color(0xFF60A5FA) else Color(0xFF222C3E),
        animationSpec = tween(durationMillis = 60),
        label = "clickZoneBorder"
    )

    val accentColor by animateColorAsState(
        targetValue = if (isPressed) Color(0xFF60A5FA) else Color(0xFF334155),
        animationSpec = tween(durationMillis = 60),
        label = "clickZoneAccent"
    )

    val textColor by animateColorAsState(
        targetValue = if (isPressed) Color.White else Color(0xFFCBD5E1),
        animationSpec = tween(durationMillis = 60),
        label = "clickZoneText"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .border(BorderStroke(1.5.dp, borderColor), RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isLocallyPressed = true
                    currentOnPress()

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val myPointer = event.changes.firstOrNull { it.id == down.id }
                            if (myPointer == null || !myPointer.pressed) {
                                myPointer?.consume()
                                break
                            }
                        }
                    } finally {
                        isLocallyPressed = false
                        currentOnRelease()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Subtle top indicator pill
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isPressed) Color(0xFF93C5FD) else Color(0xFF64748B),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 0.5.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isPressed) Color(0xFF93C5FD).copy(alpha = 0.85f) else Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

/**
 * Custom Modifier extension connecting pointerInput events directly to [TrackpadGestureProcessor].
 */
private fun Modifier.trackpadGestureHandler(
    sensitivity: Float,
    tapToClickEnabled: Boolean,
    twoFingerTapRightClickEnabled: Boolean,
    onMove: (Int, Int) -> Unit,
    onButton: (MouseButton, ButtonState) -> Unit,
    onScroll: (Int, Int) -> Unit,
    onZoom: (Double) -> Unit,
    onDragStateChanged: (Boolean) -> Unit
): Modifier = pointerInput(sensitivity, tapToClickEnabled, twoFingerTapRightClickEnabled) {
    val processor = TrackpadGestureProcessor(
        sensitivity = sensitivity,
        tapToClickEnabled = tapToClickEnabled,
        twoFingerTapRightClickEnabled = twoFingerTapRightClickEnabled,
        listener = object : TrackpadGestureListener {
            override fun onMove(dx: Int, dy: Int) = onMove(dx, dy)
            override fun onButton(button: MouseButton, state: ButtonState) = onButton(button, state)
            override fun onScroll(dx: Int, dy: Int) = onScroll(dx, dy)
            override fun onZoom(delta: Double) = onZoom(delta)
            override fun onDragStateChanged(isDragging: Boolean) = onDragStateChanged(isDragging)
        }
    )

    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val changes = event.changes

            when (event.type) {
                PointerEventType.Press -> {
                    changes.forEach { change ->
                        if (!change.previousPressed && change.pressed) {
                            processor.onPointerDown(
                                id = change.id.value,
                                x = change.position.x,
                                y = change.position.y,
                                timestampMs = change.uptimeMillis
                            )
                            change.consume()
                        }
                    }
                }

                PointerEventType.Move -> {
                    changes.forEach { change ->
                        if (change.pressed) {
                            processor.onPointerMove(
                                id = change.id.value,
                                x = change.position.x,
                                y = change.position.y,
                                timestampMs = change.uptimeMillis
                            )
                            change.consume()
                        }
                    }
                }

                PointerEventType.Release -> {
                    changes.forEach { change ->
                        if (change.changedToUp()) {
                            processor.onPointerUp(
                                id = change.id.value,
                                timestampMs = change.uptimeMillis
                            )
                            change.consume()
                        }
                    }
                }

                else -> {
                    if (changes.all { !it.pressed }) {
                        processor.onPointerCancel()
                    }
                }
            }
        }
    }
}
