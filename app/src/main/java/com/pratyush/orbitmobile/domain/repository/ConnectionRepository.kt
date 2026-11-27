package com.pratyush.orbitmobile.domain.repository

import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.Device
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain repository contract for managing connection lifecycle to ORBIT Windows.
 */
interface ConnectionRepository {
    val connectionState: StateFlow<ConnectionState>
    val activeAuthToken: String?
    val activeHost: String?
    val activePort: Int
    suspend fun connectToDevice(device: Device): OrbitResult<Unit>
    suspend fun connectManual(host: String, port: Int): OrbitResult<Unit>
    suspend fun pairWithQr(qrRawPayload: String): OrbitResult<Device>
    suspend fun attemptReconnect(): OrbitResult<Unit>
    suspend fun disconnect(): OrbitResult<Unit>
    suspend fun sendPing(): OrbitResult<Long>
    suspend fun sendTelemetry(
        batteryPercent: Int,
        isCharging: Boolean,
        batteryStatusText: String,
        batteryHealth: String? = null,
        batteryTemperature: String? = null,
        chargeSource: String? = null,
        wifiConnected: Boolean,
        wifiSsid: String?,
        wifiFrequency: String?,
        wifiLinkSpeed: String? = null,
        bluetoothEnabled: Boolean,
        bluetoothConnected: Boolean,
        bluetoothStatusText: String,
        usedStorageGb: Double = 0.0,
        totalStorageGb: Double = 0.0,
        manufacturer: String? = null
    ): OrbitResult<Unit>
    suspend fun sendClipboard(
        type: String,
        textContent: String?,
        urlContent: String?,
        imageBase64: String?,
        imageFileName: String?
    ): OrbitResult<Unit>
    val clipboardHistory: StateFlow<List<com.pratyush.orbitmobile.domain.model.ClipboardItem>>
    suspend fun updateClipboardSyncState(enabled: Boolean): OrbitResult<Unit>
    suspend fun clearClipboardHistory(): OrbitResult<Unit>
    fun receiveIncomingClipboardFromWindows(
        id: String,
        type: String,
        content: String,
        timestamp: Long,
        origin: String = "Windows"
    )
    suspend fun refreshClipboardHistoryFromPc(): OrbitResult<Unit>
}
