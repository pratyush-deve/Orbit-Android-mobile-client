package com.pratyush.orbitmobile.presentation.screens.home

import com.pratyush.orbitmobile.domain.model.ClipboardItem
import com.pratyush.orbitmobile.domain.model.ConnectionState

/**
 * Result state for manual clipboard sync operations.
 */
sealed interface ManualSyncStatus {
    data object Idle : ManualSyncStatus
    data object InProgress : ManualSyncStatus
    data object Success : ManualSyncStatus
    data class Failure(val message: String) : ManualSyncStatus
}

/**
 * Immutable UI state for the Home screen.
 */
data class HomeUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val lastKnownDeviceName: String = "ORBIT Windows PC",
    val isReconnecting: Boolean = false,
    val isClipboardSyncActive: Boolean = true,
    val isManualSyncing: Boolean = false,
    val manualSyncStatus: ManualSyncStatus = ManualSyncStatus.Idle,
    val currentClipText: String? = null,
    val clipboardHistory: List<ClipboardItem> = emptyList(),
    val isMicMuted: Boolean = false,
    val currentVolumePercent: Int = 65,
    val isMediaPlaying: Boolean = true,
    val lastActionFeedback: String? = null
)
