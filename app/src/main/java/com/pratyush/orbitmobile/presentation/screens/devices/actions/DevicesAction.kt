package com.pratyush.orbitmobile.presentation.screens.devices.actions

import com.pratyush.orbitmobile.domain.model.Device

/**
 * User actions and events for the Devices management screen.
 */
sealed interface DevicesAction {
    data object ScanRequested : DevicesAction
    data class DeviceSelected(val device: Device) : DevicesAction
    data class ConnectRequested(val device: Device) : DevicesAction
    data class ConfirmRemoveDevice(val device: Device) : DevicesAction
    data object DismissRemoveDialog : DevicesAction
    data class DeleteDeviceConfirmed(val deviceId: String) : DevicesAction
}
