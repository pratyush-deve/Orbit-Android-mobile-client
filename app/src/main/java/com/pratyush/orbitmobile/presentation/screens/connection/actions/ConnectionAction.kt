package com.pratyush.orbitmobile.presentation.screens.connection.actions

import com.pratyush.orbitmobile.domain.model.Device

/**
 * User actions and events for Connection screen.
 */
sealed interface ConnectionAction {
    data class HostInputChanged(val host: String) : ConnectionAction
    data class PortInputChanged(val port: String) : ConnectionAction
    data object ConnectManualRequested : ConnectionAction
    data class ConnectDeviceRequested(val device: Device) : ConnectionAction
    data class QrCodeScanned(val rawData: String) : ConnectionAction
    data object DisconnectRequested : ConnectionAction
}
