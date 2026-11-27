package com.pratyush.orbitmobile.domain.repository

import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.domain.model.BluetoothDevice
import com.pratyush.orbitmobile.domain.model.BrightnessControlStatus
import com.pratyush.orbitmobile.domain.model.CameraControlStatus
import com.pratyush.orbitmobile.domain.model.LaptopControlsStatus
import com.pratyush.orbitmobile.domain.model.MicrophoneControlStatus
import com.pratyush.orbitmobile.domain.model.VolumeControlStatus
import com.pratyush.orbitmobile.domain.model.WifiNetworkItem
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain repository contract for managing real Windows laptop controls.
 */
interface ControlsRepository {
    val controlsStatus: StateFlow<LaptopControlsStatus>
    val wifiNetworks: StateFlow<List<WifiNetworkItem>>
    val bluetoothDevices: StateFlow<List<BluetoothDevice>>
    val isLoading: StateFlow<Boolean>
    val isConnected: StateFlow<Boolean>
    val errorMessage: StateFlow<String?>

    suspend fun refreshControlsStatus(): OrbitResult<LaptopControlsStatus>
    suspend fun refreshWifiNetworks(): OrbitResult<List<WifiNetworkItem>>
    suspend fun connectWifi(ssid: String): OrbitResult<Boolean>
    suspend fun disconnectWifi(): OrbitResult<Boolean>
    suspend fun toggleWifiPower(enabled: Boolean? = null): OrbitResult<Boolean>
    suspend fun toggleBluetoothPower(enabled: Boolean? = null): OrbitResult<Boolean>
    suspend fun refreshBluetoothDevices(): OrbitResult<List<BluetoothDevice>>
    suspend fun connectBluetoothDevice(deviceId: String): OrbitResult<Boolean>
    suspend fun disconnectBluetoothDevice(deviceId: String? = null): OrbitResult<Boolean>
    suspend fun toggleMicrophone(mute: Boolean? = null): OrbitResult<MicrophoneControlStatus>
    suspend fun toggleCamera(enabled: Boolean? = null): OrbitResult<CameraControlStatus>
    suspend fun setVolume(percentage: Int): OrbitResult<VolumeControlStatus>
    suspend fun toggleVolumeMute(mute: Boolean? = null): OrbitResult<VolumeControlStatus>
    suspend fun setBrightness(percentage: Int): OrbitResult<BrightnessControlStatus>
    suspend fun refreshBrightness(): OrbitResult<BrightnessControlStatus>
}
