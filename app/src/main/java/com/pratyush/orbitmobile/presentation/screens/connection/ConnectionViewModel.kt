package com.pratyush.orbitmobile.presentation.screens.connection

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pratyush.orbitmobile.core.platform.NetworkMonitor
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.Device
import com.pratyush.orbitmobile.domain.usecase.ConnectDeviceUseCase
import com.pratyush.orbitmobile.domain.usecase.DisconnectDeviceUseCase
import com.pratyush.orbitmobile.domain.usecase.GetConnectionStatusUseCase
import com.pratyush.orbitmobile.domain.usecase.GetDevicesUseCase
import com.pratyush.orbitmobile.presentation.screens.connection.actions.ConnectionAction
import com.pratyush.orbitmobile.util.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing connection state, QR pairing parsing, and manual endpoints.
 */
class ConnectionViewModel(
    private val getConnectionStatusUseCase: GetConnectionStatusUseCase,
    private val getDevicesUseCase: GetDevicesUseCase,
    private val connectDeviceUseCase: ConnectDeviceUseCase,
    private val disconnectDeviceUseCase: DisconnectDeviceUseCase,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    init {
        observeConnectionStatus()
        observeSavedDevices()
        observeNetworkAvailability()
    }

    private fun observeConnectionStatus() {
        getConnectionStatusUseCase.execute()
            .onEach { status ->
                _uiState.update {
                    it.copy(
                        connectionState = status,
                        isConnecting = status is ConnectionState.Connecting
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeSavedDevices() {
        getDevicesUseCase.executePaired()
            .onEach { devices ->
                _uiState.update { it.copy(savedDevices = devices) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeNetworkAvailability() {
        networkMonitor.isOnline
            .onEach { isOnline ->
                _uiState.update { it.copy(isNetworkAvailable = isOnline) }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: ConnectionAction) {
        when (action) {
            is ConnectionAction.HostInputChanged -> {
                _uiState.update { it.copy(hostInput = action.host, errorMessage = null) }
            }
            is ConnectionAction.PortInputChanged -> {
                _uiState.update { it.copy(portInput = action.port, errorMessage = null) }
            }
            is ConnectionAction.ConnectManualRequested -> {
                initiateManualConnection()
            }
            is ConnectionAction.ConnectDeviceRequested -> {
                viewModelScope.launch {
                    connectDeviceUseCase.execute(action.device)
                }
            }
            is ConnectionAction.QrCodeScanned -> {
                handleQrScanned(action.rawData)
            }
            is ConnectionAction.DisconnectRequested -> {
                viewModelScope.launch {
                    disconnectDeviceUseCase.execute()
                }
            }
        }
    }

    private fun handleQrScanned(rawData: String) {
        if (_uiState.value.isConnecting) {
            Log.w("ConnectionVM", "Pairing already in progress; ignoring duplicate QR scan.")
            return
        }
        viewModelScope.launch {
            Log.i("ConnectionVM", "Processing scanned QR pairing code...")
            _uiState.update { it.copy(isConnecting = true, errorMessage = null) }
            val result = connectDeviceUseCase.executeQrPairing(rawData)
            when (result) {
                is com.pratyush.orbitmobile.core.common.OrbitResult.Success -> {
                    Log.i("ConnectionVM", "QR Pairing succeeded with device: ${result.data.name}")
                    _uiState.update { it.copy(isConnecting = false, errorMessage = null) }
                }
                is com.pratyush.orbitmobile.core.common.OrbitResult.Error -> {
                    val errMsg = result.exception.message ?: "QR Pairing failed"
                    Log.e("ConnectionVM", "QR Pairing failed: $errMsg", result.exception)
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            errorMessage = errMsg
                        )
                    }
                }
                else -> {
                    _uiState.update { it.copy(isConnecting = false) }
                }
            }
        }
    }

    private fun initiateManualConnection() {
        val host = _uiState.value.hostInput.trim()
        val port = _uiState.value.portInput.trim().toIntOrNull() ?: 8089

        if (!NetworkUtils.isValidIpv4(host)) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid IPv4 address") }
            return
        }

        if (!NetworkUtils.isValidPort(port)) {
            _uiState.update { it.copy(errorMessage = "Port must be between 1024 and 65535") }
            return
        }

        viewModelScope.launch {
            connectDeviceUseCase.executeManual(host, port)
        }
    }
}
