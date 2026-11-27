package com.pratyush.orbitmobile.domain.model

/**
 * Domain model for application configuration and user preferences.
 */
data class AppConfig(
    val autoConnect: Boolean = true,
    val defaultHost: String = "192.168.1.100",
    val defaultPort: Int = 8089,
    val hapticFeedbackEnabled: Boolean = true,
    val isPitchBlackTheme: Boolean = true,
    val isClipboardSyncEnabled: Boolean = false,
    val isExplicitlyDisconnected: Boolean = false,
    val lastConnectedDeviceId: String = "",
    val keepConnectedInBackground: Boolean = true,
    val trackpadSensitivity: Float = 1.0f,
    val trackpadTapToClick: Boolean = true,
    val trackpadTwoFingerTapRightClick: Boolean = true
)
