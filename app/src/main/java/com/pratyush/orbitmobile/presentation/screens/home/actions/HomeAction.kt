package com.pratyush.orbitmobile.presentation.screens.home.actions

/**
 * User actions and events for the Home screen controls.
 */
sealed interface HomeAction {
    data object ReconnectRequested : HomeAction
    data object DisconnectRequested : HomeAction
    data object AppResumed : HomeAction
    data class ToggleClipboardSync(val enabled: Boolean) : HomeAction
    data object SyncClipboardNow : HomeAction
    data object OpenAiAssistant : HomeAction
    data object OpenRemoteControls : HomeAction
    data object OpenTrackpad : HomeAction
    data object OpenFileAccess : HomeAction
    data object OpenSystemMonitor : HomeAction
    data object ToggleMuteMic : HomeAction
    data object TogglePlayPause : HomeAction
    data class AdjustVolume(val deltaPercent: Int) : HomeAction
}
