package com.pratyush.orbitmobile.presentation.screens.trackpad

import com.pratyush.orbitmobile.domain.model.ButtonState
import com.pratyush.orbitmobile.domain.model.MouseButton
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Listener for high-level trackpad gestures produced by [TrackpadGestureProcessor].
 */
interface TrackpadGestureListener {
    fun onMove(dx: Int, dy: Int)
    fun onButton(button: MouseButton, state: ButtonState)
    fun onScroll(dx: Int, dy: Int)
    fun onZoom(delta: Double)
    fun onDragStateChanged(isDragging: Boolean)
}

/**
 * Pure, fully testable gesture engine for ORBIT Mobile Touchpad.
 * Converts multi-pointer touch interactions into relative cursor deltas, taps, scrolls,
 * clicks, and drags with sensitivity scaling and jitter reduction.
 */
class TrackpadGestureProcessor(
    var sensitivity: Float = 1.0f,
    var tapToClickEnabled: Boolean = true,
    var twoFingerTapRightClickEnabled: Boolean = true,
    private val touchSlop: Float = 14f,
    private val listener: TrackpadGestureListener
) {
    companion object {
        const val TAP_TIMEOUT_MS = 250L
        const val DOUBLE_TAP_TIMEOUT_MS = 320L
        const val LONG_PRESS_TIMEOUT_MS = 400L
        const val PINCH_THRESHOLD_PX = 6f
    }

    private data class PointerInfo(
        val id: Long,
        var startX: Float,
        var startY: Float,
        var currentX: Float,
        var currentY: Float,
        var previousX: Float,
        var previousY: Float,
        val startTimeMs: Long,
        var hasMovedBeyondSlop: Boolean = false
    )

    private val activePointers = mutableMapOf<Long, PointerInfo>()

    private var lastSingleTapTimeMs: Long = 0L
    private var lastSingleTapX: Float = 0f
    private var lastSingleTapY: Float = 0f

    private var isDragging: Boolean = false
    private var longPressTriggered: Boolean = false
    private var maxPointersInCurrentGesture: Int = 0
    private var twoFingerTapEmitted: Boolean = false

    private var previousTwoFingerDistance: Float = 0f

    fun onPointerDown(id: Long, x: Float, y: Float, timestampMs: Long) {
        val pointer = PointerInfo(
            id = id,
            startX = x,
            startY = y,
            currentX = x,
            currentY = y,
            previousX = x,
            previousY = y,
            startTimeMs = timestampMs
        )
        activePointers[id] = pointer
        maxPointersInCurrentGesture = maxOf(maxPointersInCurrentGesture, activePointers.size)

        if (activePointers.size == 2) {
            val p = activePointers.values.toList()
            previousTwoFingerDistance = hypot(p[0].currentX - p[1].currentX, p[0].currentY - p[1].currentY)
        }
    }

    fun onPointerMove(id: Long, x: Float, y: Float, timestampMs: Long) {
        val pointer = activePointers[id] ?: return
        pointer.previousX = pointer.currentX
        pointer.previousY = pointer.currentY
        pointer.currentX = x
        pointer.currentY = y

        val totalDistFromStart = hypot(pointer.currentX - pointer.startX, pointer.currentY - pointer.startY)
        if (totalDistFromStart > touchSlop) {
            pointer.hasMovedBeyondSlop = true
        }

        when (activePointers.size) {
            1 -> {
                // Check for long-press drag activation
                val elapsedSinceDown = timestampMs - pointer.startTimeMs
                if (!isDragging && !pointer.hasMovedBeyondSlop && elapsedSinceDown >= LONG_PRESS_TIMEOUT_MS) {
                    isDragging = true
                    longPressTriggered = true
                    listener.onButton(MouseButton.LEFT, ButtonState.DOWN)
                    listener.onDragStateChanged(true)
                }

                // Single finger relative motion
                val rawDx = (pointer.currentX - pointer.previousX)
                val rawDy = (pointer.currentY - pointer.previousY)

                // Sub-pixel jitter filter
                if (abs(rawDx) < 0.15f && abs(rawDy) < 0.15f) {
                    return
                }

                val scaledDx = (rawDx * sensitivity).roundToInt()
                val scaledDy = (rawDy * sensitivity).roundToInt()

                if (scaledDx != 0 || scaledDy != 0) {
                    listener.onMove(scaledDx, scaledDy)
                }
            }

            2 -> {
                // Two fingers: Scrolling and Pinch Zoom
                val pointers = activePointers.values.toList()
                val p1 = pointers[0]
                val p2 = pointers[1]

                val currentDistance = hypot(p1.currentX - p2.currentX, p1.currentY - p2.currentY)
                val distanceDiff = currentDistance - previousTwoFingerDistance

                if (abs(distanceDiff) > PINCH_THRESHOLD_PX) {
                    // Pinch Zoom
                    val zoomDelta = if (distanceDiff > 0) 1.0 else -1.0
                    listener.onZoom(zoomDelta)
                    previousTwoFingerDistance = currentDistance
                } else {
                    // Two-finger Scroll (Vertical and Horizontal)
                    val avgDx = ((p1.currentX - p1.previousX) + (p2.currentX - p2.previousX)) / 2f
                    val avgDy = ((p1.currentY - p1.previousY) + (p2.currentY - p2.previousY)) / 2f

                    val scrollX = (avgDx * 1.5f).roundToInt()
                    // Invert vertical scroll for natural feel (swipe up scrolls down / content moves up)
                    val scrollY = (-avgDy * 1.5f).roundToInt()

                    if (scrollX != 0 || scrollY != 0) {
                        listener.onScroll(scrollX, scrollY)
                    }
                }
            }
        }
    }

    fun onPointerUp(id: Long, timestampMs: Long) {
        val pointer = activePointers.remove(id) ?: return
        val pointersCountBeforeRemoval = activePointers.size + 1

        if (maxPointersInCurrentGesture >= 2) {
            // Two or more fingers were part of this gesture
            if (!twoFingerTapEmitted && pointersCountBeforeRemoval >= 2) {
                val elapsed = timestampMs - pointer.startTimeMs
                if (!pointer.hasMovedBeyondSlop && elapsed < TAP_TIMEOUT_MS && twoFingerTapRightClickEnabled) {
                    twoFingerTapEmitted = true
                    listener.onButton(MouseButton.RIGHT, ButtonState.DOWN)
                    listener.onButton(MouseButton.RIGHT, ButtonState.UP)
                }
            }
        } else if (pointersCountBeforeRemoval == 1) {
            if (isDragging) {
                isDragging = false
                listener.onButton(MouseButton.LEFT, ButtonState.UP)
                listener.onDragStateChanged(false)
                longPressTriggered = false
                return
            }

            val elapsed = timestampMs - pointer.startTimeMs
            if (!pointer.hasMovedBeyondSlop && elapsed < TAP_TIMEOUT_MS) {
                // Single or double tap check
                val isDoubleTap = (timestampMs - lastSingleTapTimeMs) < DOUBLE_TAP_TIMEOUT_MS &&
                        hypot(pointer.currentX - lastSingleTapX, pointer.currentY - lastSingleTapY) < touchSlop * 2

                if (isDoubleTap) {
                    // Double Click
                    listener.onButton(MouseButton.LEFT, ButtonState.DOWN)
                    listener.onButton(MouseButton.LEFT, ButtonState.UP)
                    listener.onButton(MouseButton.LEFT, ButtonState.DOWN)
                    listener.onButton(MouseButton.LEFT, ButtonState.UP)
                    lastSingleTapTimeMs = 0L
                } else {
                    if (tapToClickEnabled) {
                        // Single Left Click
                        listener.onButton(MouseButton.LEFT, ButtonState.DOWN)
                        listener.onButton(MouseButton.LEFT, ButtonState.UP)
                    }
                    lastSingleTapTimeMs = timestampMs
                    lastSingleTapX = pointer.currentX
                    lastSingleTapY = pointer.currentY
                }
            }
        }

        if (activePointers.isEmpty()) {
            previousTwoFingerDistance = 0f
            longPressTriggered = false
            maxPointersInCurrentGesture = 0
            twoFingerTapEmitted = false
        }
    }

    fun onPointerCancel() {
        if (isDragging) {
            isDragging = false
            listener.onButton(MouseButton.LEFT, ButtonState.UP)
            listener.onDragStateChanged(false)
        }
        activePointers.clear()
        previousTwoFingerDistance = 0f
        longPressTriggered = false
        maxPointersInCurrentGesture = 0
        twoFingerTapEmitted = false
    }
}
