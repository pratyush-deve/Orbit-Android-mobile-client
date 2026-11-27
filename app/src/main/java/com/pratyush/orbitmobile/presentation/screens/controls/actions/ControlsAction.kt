package com.pratyush.orbitmobile.presentation.screens.controls.actions

sealed interface ControlsAction {
    data object RefreshRequested : ControlsAction
    data object ToggleMicrophone : ControlsAction
    data object ToggleCamera : ControlsAction
    data class SetVolume(val percentage: Int) : ControlsAction
    data class CommitVolume(val percentage: Int) : ControlsAction
    data object ToggleVolumeMute : ControlsAction
    data class SetBrightness(val percentage: Int) : ControlsAction
    data class CommitBrightness(val percentage: Int) : ControlsAction
    data object QuickActionMute : ControlsAction
    data object QuickActionUnmute : ControlsAction

    // Wi-Fi actions
    data object OpenWifiSheet : ControlsAction
    data object CloseWifiSheet : ControlsAction
    data object RefreshWifiNetworks : ControlsAction
    data class ConnectWifiNetwork(val ssid: String) : ControlsAction
    data object DisconnectWifiNetwork : ControlsAction
    data class ToggleWifiPower(val enabled: Boolean) : ControlsAction

    // Bluetooth actions
    data object OpenBluetoothSheet : ControlsAction
    data object CloseBluetoothSheet : ControlsAction
    data object RefreshBluetoothDevices : ControlsAction
    data class ConnectBluetoothDevice(val deviceId: String) : ControlsAction
    data class DisconnectBluetoothDevice(val deviceId: String?) : ControlsAction
    data class ToggleBluetoothPower(val enabled: Boolean) : ControlsAction

    data object DismissError : ControlsAction
}
