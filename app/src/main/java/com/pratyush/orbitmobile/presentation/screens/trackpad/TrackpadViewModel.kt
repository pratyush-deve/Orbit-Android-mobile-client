package com.pratyush.orbitmobile.presentation.screens.trackpad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pratyush.orbitmobile.data.local.preferences.PreferencesManager
import com.pratyush.orbitmobile.domain.model.ButtonState
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.MouseButton
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository
import com.pratyush.orbitmobile.domain.repository.TrackpadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel orchestrating the Trackpad screen state and input dispatching.
 */
class TrackpadViewModel(
    private val trackpadRepository: TrackpadRepository,
    private val preferencesManager: PreferencesManager,
    private val connectionRepository: ConnectionRepository
) : ViewModel() {

    private val _isDragging = MutableStateFlow(false)
    private val _activeGestureFeedback = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TrackpadUiState> = combine(
        connectionRepository.connectionState,
        preferencesManager.appConfig,
        trackpadRepository.isLeftButtonDown,
        trackpadRepository.isRightButtonDown,
        _isDragging
    ) { connState, appConfig, isLeftDown, isRightDown, isDragging ->
        val name = when (connState) {
            is ConnectionState.Connected -> connState.deviceName
            is ConnectionState.Connecting -> connState.deviceName
            is ConnectionState.TemporarilyUnavailable -> connState.deviceName
            else -> "Windows PC"
        }

        TrackpadUiState(
            isConnected = connState is ConnectionState.Connected,
            deviceName = name,
            sensitivity = appConfig.trackpadSensitivity,
            tapToClickEnabled = appConfig.trackpadTapToClick,
            twoFingerTapRightClickEnabled = appConfig.trackpadTwoFingerTapRightClick,
            isLeftButtonPressed = isLeftDown,
            isRightButtonPressed = isRightDown,
            isDragging = isDragging,
            activeGestureFeedback = null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TrackpadUiState()
    )

    fun onAction(action: TrackpadAction) {
        when (action) {
            is TrackpadAction.MoveEvent -> {
                trackpadRepository.sendMove(action.dx, action.dy)
            }

            is TrackpadAction.ButtonEvent -> {
                trackpadRepository.sendButton(action.button, action.state)
            }

            is TrackpadAction.ScrollEvent -> {
                trackpadRepository.sendScroll(action.dx, action.dy)
            }

            is TrackpadAction.ZoomEvent -> {
                trackpadRepository.sendZoom(action.delta)
            }

            is TrackpadAction.DragStateChanged -> {
                _isDragging.value = action.isDragging
            }

            is TrackpadAction.ReleaseAll -> {
                _isDragging.value = false
                trackpadRepository.releaseHeldButtons()
            }

            is TrackpadAction.SetSensitivity -> {
                viewModelScope.launch {
                    preferencesManager.setTrackpadSensitivity(action.sensitivity)
                }
            }

            is TrackpadAction.SetTapToClick -> {
                viewModelScope.launch {
                    preferencesManager.setTrackpadTapToClick(action.enabled)
                }
            }

            is TrackpadAction.SetTwoFingerTap -> {
                viewModelScope.launch {
                    preferencesManager.setTrackpadTwoFingerTapRightClick(action.enabled)
                }
            }
        }
    }

    fun onScreenDisposed() {
        _isDragging.value = false
        trackpadRepository.releaseHeldButtons()
    }

    override fun onCleared() {
        super.onCleared()
        onScreenDisposed()
    }
}
