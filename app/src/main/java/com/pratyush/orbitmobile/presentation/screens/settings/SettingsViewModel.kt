package com.pratyush.orbitmobile.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pratyush.orbitmobile.domain.usecase.GetSettingsUseCase
import com.pratyush.orbitmobile.domain.usecase.SaveSettingsUseCase
import com.pratyush.orbitmobile.presentation.screens.settings.actions.SettingsAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for application preferences and user configuration.
 */
class SettingsViewModel(
    private val getSettingsUseCase: GetSettingsUseCase,
    private val saveSettingsUseCase: SaveSettingsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
    }

    private fun observeSettings() {
        getSettingsUseCase.execute()
            .onEach { config ->
                _uiState.update { it.copy(config = config) }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.ToggleAutoConnect -> {
                viewModelScope.launch {
                    saveSettingsUseCase.updateAutoConnect(action.enabled)
                }
            }
            is SettingsAction.ToggleHapticFeedback -> {
                viewModelScope.launch {
                    saveSettingsUseCase.updateHapticFeedback(action.enabled)
                }
            }
            is SettingsAction.ToggleKeepConnectedInBackground -> {
                viewModelScope.launch {
                    saveSettingsUseCase.updateKeepConnectedInBackground(action.enabled)
                }
            }
            is SettingsAction.UpdateDefaultHost -> {
                viewModelScope.launch {
                    saveSettingsUseCase.updateEndpoint(action.host, _uiState.value.config.defaultPort)
                }
            }
            is SettingsAction.UpdateDefaultPort -> {
                viewModelScope.launch {
                    saveSettingsUseCase.updateEndpoint(_uiState.value.config.defaultHost, action.port)
                }
            }
            is SettingsAction.UpdateTrackpadSensitivity -> {
                viewModelScope.launch {
                    saveSettingsUseCase.updateTrackpadSensitivity(action.sensitivity)
                }
            }
            is SettingsAction.ToggleTrackpadTapToClick -> {
                viewModelScope.launch {
                    saveSettingsUseCase.updateTrackpadTapToClick(action.enabled)
                }
            }
            is SettingsAction.ToggleTrackpadTwoFingerTapRightClick -> {
                viewModelScope.launch {
                    saveSettingsUseCase.updateTrackpadTwoFingerTapRightClick(action.enabled)
                }
            }
        }
    }
}
