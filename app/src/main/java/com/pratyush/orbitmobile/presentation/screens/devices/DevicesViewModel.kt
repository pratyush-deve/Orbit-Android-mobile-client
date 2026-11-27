package com.pratyush.orbitmobile.presentation.screens.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pratyush.orbitmobile.domain.usecase.ConnectDeviceUseCase
import com.pratyush.orbitmobile.domain.usecase.GetDevicesUseCase
import com.pratyush.orbitmobile.domain.usecase.RemoveDeviceUseCase
import com.pratyush.orbitmobile.presentation.screens.devices.actions.DevicesAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing paired laptops, reconnection, and device removal.
 */
class DevicesViewModel(
    private val getDevicesUseCase: GetDevicesUseCase,
    private val connectDeviceUseCase: ConnectDeviceUseCase,
    private val removeDeviceUseCase: RemoveDeviceUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    init {
        observeDevices()
    }

    private fun observeDevices() {
        getDevicesUseCase.executePaired()
            .onEach { paired ->
                _uiState.update { it.copy(pairedDevices = paired) }
            }
            .launchIn(viewModelScope)

        getDevicesUseCase.executeDiscovered()
            .onEach { discovered ->
                _uiState.update { it.copy(discoveredDevices = discovered) }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: DevicesAction) {
        when (action) {
            is DevicesAction.ScanRequested -> {
                _uiState.update { it.copy(isScanning = true) }
            }
            is DevicesAction.DeviceSelected -> {
                _uiState.update { it.copy(selectedDeviceId = action.device.id) }
            }
            is DevicesAction.ConnectRequested -> {
                viewModelScope.launch {
                    connectDeviceUseCase.execute(action.device)
                }
            }
            is DevicesAction.ConfirmRemoveDevice -> {
                _uiState.update { it.copy(devicePendingDeletion = action.device) }
            }
            is DevicesAction.DismissRemoveDialog -> {
                _uiState.update { it.copy(devicePendingDeletion = null) }
            }
            is DevicesAction.DeleteDeviceConfirmed -> {
                viewModelScope.launch {
                    removeDeviceUseCase.execute(action.deviceId)
                    _uiState.update { it.copy(devicePendingDeletion = null) }
                }
            }
        }
    }
}
