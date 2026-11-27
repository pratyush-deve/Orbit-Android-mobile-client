package com.pratyush.orbitmobile.domain.model

data class WifiNetworkItem(
    val ssid: String,
    val signal: Int = 0,
    val authentication: String = "",
    val isConnected: Boolean = false
)

data class WifiControlStatus(
    val isAvailable: Boolean = true,
    val isEnabled: Boolean = true,
    val isConnected: Boolean = true,
    val ssid: String? = null,
    val signal: Int? = null,
    val detail: String? = null
)

data class BluetoothControlStatus(
    val isAvailable: Boolean = true,
    val isEnabled: Boolean = true,
    val statusLabel: String = "No Devices",
    val detail: String? = null
)

data class BluetoothDevice(
    val id: String,
    val name: String,
    val isPaired: Boolean = false,
    val isConnected: Boolean = false,
    val deviceType: String? = null
)

data class MicrophoneControlStatus(
    val isAvailable: Boolean = true,
    val isEnabled: Boolean = true, // true = active/unmuted, false = muted
    val isMuted: Boolean = false,
    val statusLabel: String = "ON"
)

data class CameraControlStatus(
    val isAvailable: Boolean = true,
    val isEnabled: Boolean = true, // true = access allowed, false = access blocked
    val statusLabel: String = "Allowed",
    val detail: String? = null
)

data class VolumeControlStatus(
    val isAvailable: Boolean = true,
    val percentage: Int = 50,
    val isMuted: Boolean = false
)

data class BrightnessControlStatus(
    val isAvailable: Boolean = true,
    val percentage: Int = 100,
    val statusLabel: String = "100%",
    val detail: String? = null
)

data class LaptopControlsStatus(
    val wifi: WifiControlStatus = WifiControlStatus(),
    val bluetooth: BluetoothControlStatus = BluetoothControlStatus(),
    val microphone: MicrophoneControlStatus = MicrophoneControlStatus(),
    val camera: CameraControlStatus = CameraControlStatus(),
    val volume: VolumeControlStatus = VolumeControlStatus(),
    val brightness: BrightnessControlStatus = BrightnessControlStatus()
)

sealed interface ControlEvent {
    data class Snapshot(val status: LaptopControlsStatus) : ControlEvent
    data class VolumeUpdate(val percentage: Int, val isMuted: Boolean) : ControlEvent
    data class BrightnessUpdate(val percentage: Int, val isAvailable: Boolean, val statusLabel: String) : ControlEvent
    data class GenericControlUpdate(
        val type: String,
        val isAvailable: Boolean,
        val isEnabled: Boolean,
        val statusLabel: String,
        val detail: String?
    ) : ControlEvent
}
