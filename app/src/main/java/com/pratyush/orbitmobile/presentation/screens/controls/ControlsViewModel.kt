package com.pratyush.orbitmobile.presentation.screens.controls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.domain.repository.ControlsRepository
import com.pratyush.orbitmobile.presentation.screens.controls.actions.ControlsAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ControlsViewModel(
    private val controlsRepository: ControlsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControlsUiState())
    val uiState: StateFlow<ControlsUiState> = _uiState.asStateFlow()

    private var volumeDebounceJob: Job? = null
    private var brightnessDebounceJob: Job? = null
    private var isUserInteractingWithVolume = false
    private var isUserInteractingWithBrightness = false
    private var lastNonZeroVolume = 25

    init {
        // Observe connection state
        controlsRepository.isConnected
            .onEach { connected ->
                _uiState.update { it.copy(isConnected = connected) }
                if (connected) {
                    refreshControls()
                }
            }
            .launchIn(viewModelScope)

        // Observe repository controls state
        controlsRepository.controlsStatus
            .onEach { status ->
                if (status.volume.percentage > 0) {
                    lastNonZeroVolume = status.volume.percentage
                }
                _uiState.update { current ->
                    current.copy(
                        wifi = status.wifi,
                        bluetooth = status.bluetooth,
                        microphone = status.microphone,
                        camera = status.camera,
                        isVolumeInitialized = true,
                        volumePercentage = if (isUserInteractingWithVolume) current.volumePercentage else status.volume.percentage,
                        isVolumeMuted = if (isUserInteractingWithVolume) current.isVolumeMuted else status.volume.isMuted,
                        isBrightnessInitialized = true,
                        brightnessPercentage = if (isUserInteractingWithBrightness) current.brightnessPercentage else status.brightness.percentage,
                        isBrightnessAvailable = status.brightness.isAvailable,
                        brightnessStatusLabel = status.brightness.statusLabel
                    )
                }
            }
            .launchIn(viewModelScope)

        // Observe Wi-Fi networks
        controlsRepository.wifiNetworks
            .onEach { nets ->
                _uiState.update { it.copy(wifiNetworks = nets) }
            }
            .launchIn(viewModelScope)

        // Observe Bluetooth devices list
        controlsRepository.bluetoothDevices
            .onEach { devs ->
                _uiState.update { it.copy(bluetoothDevices = devs) }
            }
            .launchIn(viewModelScope)

        // Observe loading state
        controlsRepository.isLoading
            .onEach { loading ->
                _uiState.update { it.copy(isLoading = loading) }
            }
            .launchIn(viewModelScope)

        // Initial refresh
        refreshControls()
    }

    fun onAction(action: ControlsAction) {
        when (action) {
            is ControlsAction.RefreshRequested -> refreshControls()

            // Wi-Fi actions
            is ControlsAction.OpenWifiSheet -> {
                _uiState.update { it.copy(isWifiSheetOpen = true) }
                viewModelScope.launch {
                    controlsRepository.refreshWifiNetworks()
                }
            }

            is ControlsAction.CloseWifiSheet -> {
                _uiState.update { it.copy(isWifiSheetOpen = false) }
            }

            is ControlsAction.RefreshWifiNetworks -> {
                viewModelScope.launch {
                    controlsRepository.refreshWifiNetworks()
                }
            }

            is ControlsAction.ConnectWifiNetwork -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isWifiActionLoading = true) }
                    val result = controlsRepository.connectWifi(action.ssid)
                    _uiState.update { it.copy(isWifiActionLoading = false) }
                    if (result is OrbitResult.Error) {
                        _uiState.update { it.copy(errorMessage = "Failed to connect to ${action.ssid}: ${result.exception.message}") }
                    }
                }
            }

            is ControlsAction.DisconnectWifiNetwork -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isWifiActionLoading = true) }
                    val result = controlsRepository.disconnectWifi()
                    _uiState.update { it.copy(isWifiActionLoading = false) }
                    if (result is OrbitResult.Error) {
                        _uiState.update { it.copy(errorMessage = "Failed to disconnect Wi-Fi: ${result.exception.message}") }
                    }
                }
            }

            is ControlsAction.ToggleWifiPower -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isWifiActionLoading = true) }
                    val result = controlsRepository.toggleWifiPower(action.enabled)
                    _uiState.update { it.copy(isWifiActionLoading = false) }
                    if (result is OrbitResult.Error) {
                        _uiState.update { it.copy(errorMessage = "Failed to toggle Wi-Fi power: ${result.exception.message}") }
                    }
                }
            }

            // Bluetooth actions
            is ControlsAction.OpenBluetoothSheet -> {
                _uiState.update { it.copy(isBluetoothSheetOpen = true) }
                viewModelScope.launch {
                    controlsRepository.refreshBluetoothDevices()
                }
            }

            is ControlsAction.CloseBluetoothSheet -> {
                _uiState.update { it.copy(isBluetoothSheetOpen = false) }
            }

            is ControlsAction.RefreshBluetoothDevices -> {
                viewModelScope.launch {
                    controlsRepository.refreshBluetoothDevices()
                }
            }

            is ControlsAction.ConnectBluetoothDevice -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isBluetoothActionLoading = true) }
                    val result = controlsRepository.connectBluetoothDevice(action.deviceId)
                    _uiState.update { it.copy(isBluetoothActionLoading = false) }
                    if (result is OrbitResult.Error) {
                        _uiState.update { it.copy(errorMessage = "Connection error: ${result.exception.message}") }
                    }
                }
            }

            is ControlsAction.DisconnectBluetoothDevice -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isBluetoothActionLoading = true) }
                    val result = controlsRepository.disconnectBluetoothDevice(action.deviceId)
                    _uiState.update { it.copy(isBluetoothActionLoading = false) }
                    if (result is OrbitResult.Error) {
                        _uiState.update { it.copy(errorMessage = "Disconnection error: ${result.exception.message}") }
                    }
                }
            }

            is ControlsAction.ToggleBluetoothPower -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isBluetoothActionLoading = true) }
                    val result = controlsRepository.toggleBluetoothPower(action.enabled)
                    _uiState.update { it.copy(isBluetoothActionLoading = false) }
                    if (result is OrbitResult.Error) {
                        _uiState.update { it.copy(errorMessage = "Failed to toggle Bluetooth power: ${result.exception.message}") }
                    }
                }
            }

            // Microphone actions
            is ControlsAction.ToggleMicrophone -> {
                viewModelScope.launch {
                    val currentMuted = _uiState.value.microphone.isMuted
                    val targetMute = !currentMuted
                    _uiState.update {
                        it.copy(microphone = it.microphone.copy(isMuted = targetMute, isEnabled = !targetMute))
                    }
                    val result = controlsRepository.toggleMicrophone(targetMute)
                    if (result is OrbitResult.Error) {
                        _uiState.update {
                            it.copy(
                                errorMessage = "Failed to toggle microphone: ${result.exception.message}",
                                microphone = it.microphone.copy(isMuted = currentMuted, isEnabled = !currentMuted)
                            )
                        }
                    }
                }
            }

            // Camera actions
            is ControlsAction.ToggleCamera -> {
                viewModelScope.launch {
                    val currentEnabled = _uiState.value.camera.isEnabled
                    val targetEnabled = !currentEnabled
                    _uiState.update {
                        it.copy(camera = it.camera.copy(isEnabled = targetEnabled))
                    }
                    val result = controlsRepository.toggleCamera(targetEnabled)
                    if (result is OrbitResult.Error) {
                        _uiState.update {
                            it.copy(
                                errorMessage = "Failed to toggle camera: ${result.exception.message}",
                                camera = it.camera.copy(isEnabled = currentEnabled)
                            )
                        }
                    }
                }
            }

            // Volume actions
            is ControlsAction.SetVolume -> {
                isUserInteractingWithVolume = true
                val clamped = action.percentage.coerceIn(0, 100)
                if (clamped > 0) {
                    lastNonZeroVolume = clamped
                }
                val targetMuted = (clamped == 0)
                _uiState.update { it.copy(volumePercentage = clamped, isVolumeMuted = targetMuted) }

                volumeDebounceJob?.cancel()
                volumeDebounceJob = viewModelScope.launch {
                    delay(80)
                    controlsRepository.setVolume(clamped)
                    controlsRepository.toggleVolumeMute(targetMuted)
                }
            }

            is ControlsAction.CommitVolume -> {
                volumeDebounceJob?.cancel()
                val clamped = action.percentage.coerceIn(0, 100)
                if (clamped > 0) {
                    lastNonZeroVolume = clamped
                }
                val targetMuted = (clamped == 0)
                _uiState.update { it.copy(volumePercentage = clamped, isVolumeMuted = targetMuted) }
                viewModelScope.launch {
                    controlsRepository.setVolume(clamped)
                    controlsRepository.toggleVolumeMute(targetMuted)
                    delay(300)
                    isUserInteractingWithVolume = false
                }
            }

            is ControlsAction.ToggleVolumeMute -> {
                viewModelScope.launch {
                    val currentMuted = _uiState.value.isVolumeMuted
                    val targetMuted = !currentMuted
                    val currentVol = _uiState.value.volumePercentage
                    val targetVol = if (!targetMuted && currentVol == 0) {
                        if (lastNonZeroVolume > 0) lastNonZeroVolume else 25
                    } else {
                        currentVol
                    }

                    _uiState.update { it.copy(isVolumeMuted = targetMuted, volumePercentage = targetVol) }

                    if (targetVol != currentVol) {
                        controlsRepository.setVolume(targetVol)
                    }
                    val result = controlsRepository.toggleVolumeMute(targetMuted)
                    if (result is OrbitResult.Error) {
                        _uiState.update {
                            it.copy(
                                errorMessage = "Failed to toggle volume mute: ${result.exception.message}",
                                isVolumeMuted = currentMuted,
                                volumePercentage = currentVol
                            )
                        }
                    }
                }
            }

            // Brightness actions
            is ControlsAction.SetBrightness -> {
                isUserInteractingWithBrightness = true
                val clamped = action.percentage.coerceIn(0, 100)
                _uiState.update { it.copy(brightnessPercentage = clamped, brightnessStatusLabel = "$clamped%") }

                brightnessDebounceJob?.cancel()
                brightnessDebounceJob = viewModelScope.launch {
                    delay(80)
                    controlsRepository.setBrightness(clamped)
                }
            }

            is ControlsAction.CommitBrightness -> {
                brightnessDebounceJob?.cancel()
                val clamped = action.percentage.coerceIn(0, 100)
                _uiState.update { it.copy(brightnessPercentage = clamped, brightnessStatusLabel = "$clamped%") }
                viewModelScope.launch {
                    controlsRepository.setBrightness(clamped)
                    delay(300)
                    isUserInteractingWithBrightness = false
                }
            }

            is ControlsAction.QuickActionMute -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isVolumeMuted = true) }
                    val result = controlsRepository.toggleVolumeMute(true)
                    if (result is OrbitResult.Error) {
                        _uiState.update { it.copy(errorMessage = "Mute failed: ${result.exception.message}") }
                    }
                }
            }

            is ControlsAction.QuickActionUnmute -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isVolumeMuted = false) }
                    val result = controlsRepository.toggleVolumeMute(false)
                    if (result is OrbitResult.Error) {
                        _uiState.update { it.copy(errorMessage = "Unmute failed: ${result.exception.message}") }
                    }
                }
            }

            is ControlsAction.DismissError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun refreshControls() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            controlsRepository.refreshControlsStatus()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
