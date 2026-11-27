package com.pratyush.orbitmobile.presentation.screens.connection

import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.Device

/**
 * Immutable UI state for Connection screen.
 */
data class ConnectionUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val savedDevices: List<Device> = emptyList(),
    val hostInput: String = "192.168.31.147",
    val portInput: String = "8765",
    val isConnecting: Boolean = false,
    val isNetworkAvailable: Boolean = true,
    val errorMessage: String? = null
)
