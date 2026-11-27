package com.pratyush.orbitmobile.domain.model

/**
 * Mouse button classification for trackpad clicks.
 */
enum class MouseButton(val wireName: String) {
    LEFT("left"),
    RIGHT("right"),
    MIDDLE("middle")
}

/**
 * Button state transitions for click down and click up.
 */
enum class ButtonState(val wireName: String) {
    DOWN("down"),
    UP("up")
}

/**
 * Strongly typed trackpad input events dispatched to ORBIT Windows.
 */
sealed interface TrackpadInputEvent {
    data class Move(val dx: Int, val dy: Int) : TrackpadInputEvent
    data class Button(val button: MouseButton, val state: ButtonState) : TrackpadInputEvent
    data class Scroll(val dx: Int, val dy: Int) : TrackpadInputEvent
    data class Zoom(val delta: Double) : TrackpadInputEvent
    data object ReleaseAll : TrackpadInputEvent
}
