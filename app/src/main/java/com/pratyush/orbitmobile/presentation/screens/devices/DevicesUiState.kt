package com.pratyush.orbitmobile.presentation.screens.devices

import com.pratyush.orbitmobile.domain.model.Device

/**
 * Immutable UI state for the Devices management screen.
 */
data class DevicesUiState(
    val pairedDevices: List<Device> = emptyList(),
    val discoveredDevices: List<Device> = emptyList(),
    val isScanning: Boolean = false,
    val selectedDeviceId: String? = null,
    val devicePendingDeletion: Device? = null,
    val errorMessage: String? = null
)
