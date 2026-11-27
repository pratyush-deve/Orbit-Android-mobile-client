package com.pratyush.orbitmobile.presentation.screens.settings.actions

/**
 * User actions and events for Settings screen.
 */
sealed interface SettingsAction {
    data class ToggleAutoConnect(val enabled: Boolean) : SettingsAction
    data class ToggleHapticFeedback(val enabled: Boolean) : SettingsAction
    data class ToggleKeepConnectedInBackground(val enabled: Boolean) : SettingsAction
    data class UpdateDefaultHost(val host: String) : SettingsAction
    data class UpdateDefaultPort(val port: Int) : SettingsAction
    data class UpdateTrackpadSensitivity(val sensitivity: Float) : SettingsAction
    data class ToggleTrackpadTapToClick(val enabled: Boolean) : SettingsAction
    data class ToggleTrackpadTwoFingerTapRightClick(val enabled: Boolean) : SettingsAction
}
