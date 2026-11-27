package com.pratyush.orbitmobile.domain.repository

import com.pratyush.orbitmobile.domain.model.ButtonState
import com.pratyush.orbitmobile.domain.model.MouseButton
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain repository contract for controlling Windows PC mouse cursor and physical click zones.
 */
interface TrackpadRepository {
    val isConnected: StateFlow<Boolean>
    val isLeftButtonDown: StateFlow<Boolean>
    val isRightButtonDown: StateFlow<Boolean>

    fun sendMove(dx: Int, dy: Int)
    fun sendButton(button: MouseButton, state: ButtonState)
    fun sendScroll(dx: Int, dy: Int)
    fun sendZoom(delta: Double)
    fun releaseHeldButtons()
    suspend fun flushPendingEvents()
}
