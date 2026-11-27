package com.pratyush.orbitmobile.presentation.screens.trackpad

import com.pratyush.orbitmobile.domain.model.ButtonState
import com.pratyush.orbitmobile.domain.model.MouseButton

/**
 * Live state for the full-screen Trackpad screen.
 */
data class TrackpadUiState(
    val isConnected: Boolean = false,
    val deviceName: String = "Windows PC",
    val sensitivity: Float = 1.0f,
    val tapToClickEnabled: Boolean = true,
    val twoFingerTapRightClickEnabled: Boolean = true,
    val isLeftButtonPressed: Boolean = false,
    val isRightButtonPressed: Boolean = false,
    val isDragging: Boolean = false,
    val activeGestureFeedback: String? = null
)

/**
 * Interactions and events dispatched from the trackpad surface and click zones.
 */
sealed interface TrackpadAction {
    data class ButtonEvent(val button: MouseButton, val state: ButtonState) : TrackpadAction
    data class MoveEvent(val dx: Int, val dy: Int) : TrackpadAction
    data class ScrollEvent(val dx: Int, val dy: Int) : TrackpadAction
    data class ZoomEvent(val delta: Double) : TrackpadAction
    data class DragStateChanged(val isDragging: Boolean) : TrackpadAction
    data object ReleaseAll : TrackpadAction
    data class SetSensitivity(val sensitivity: Float) : TrackpadAction
    data class SetTapToClick(val enabled: Boolean) : TrackpadAction
    data class SetTwoFingerTap(val enabled: Boolean) : TrackpadAction
}
