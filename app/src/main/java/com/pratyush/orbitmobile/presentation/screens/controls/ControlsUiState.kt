package com.pratyush.orbitmobile.presentation.screens.controls

import com.pratyush.orbitmobile.domain.model.BluetoothControlStatus
import com.pratyush.orbitmobile.domain.model.BluetoothDevice
import com.pratyush.orbitmobile.domain.model.CameraControlStatus
import com.pratyush.orbitmobile.domain.model.MicrophoneControlStatus
import com.pratyush.orbitmobile.domain.model.WifiControlStatus
import com.pratyush.orbitmobile.domain.model.WifiNetworkItem

data class ControlsUiState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val wifi: WifiControlStatus = WifiControlStatus(),
    val wifiNetworks: List<WifiNetworkItem> = emptyList(),
    val isWifiSheetOpen: Boolean = false,
    val isWifiActionLoading: Boolean = false,
    val bluetooth: BluetoothControlStatus = BluetoothControlStatus(),
    val bluetoothDevices: List<BluetoothDevice> = emptyList(),
    val isBluetoothSheetOpen: Boolean = false,
    val isBluetoothActionLoading: Boolean = false,
    val microphone: MicrophoneControlStatus = MicrophoneControlStatus(),
    val camera: CameraControlStatus = CameraControlStatus(),
    val isVolumeInitialized: Boolean = false,
    val volumePercentage: Int = 0,
    val isVolumeMuted: Boolean = false,
    val isBrightnessInitialized: Boolean = false,
    val brightnessPercentage: Int = 100,
    val isBrightnessAvailable: Boolean = true,
    val brightnessStatusLabel: String = "100%",
    val errorMessage: String? = null
)
