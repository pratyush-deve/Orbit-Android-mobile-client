package com.pratyush.orbitmobile

import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.data.remote.RemoteDataSource
import com.pratyush.orbitmobile.data.repository.implementations.SystemMonitorRepositoryImpl
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.CpuStats
import com.pratyush.orbitmobile.domain.model.GpuStats
import com.pratyush.orbitmobile.domain.model.RamStats
import com.pratyush.orbitmobile.domain.model.SystemTelemetry
import com.pratyush.orbitmobile.domain.model.TelemetryFreshness
import com.pratyush.orbitmobile.domain.model.TrackpadInputEvent
import com.pratyush.orbitmobile.domain.model.WifiStats
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemMonitorUnitTest {

    private fun createSampleTelemetry(cpuPercent: Float = 41.2f, dlBps: Double = 21800.0, ulBps: Double = 73600.0): SystemTelemetry {
        return SystemTelemetry(
            cpu = CpuStats(
                name = "AMD Ryzen 7 8840HS",
                utilizationPercent = cpuPercent,
                cores = 8,
                threads = 16,
                temperatureCelsius = 72f
            ),
            ram = RamStats(
                spec = "DDR5 • 32 GB",
                utilizationPercent = 68f,
                usedGb = 21.4f,
                totalGb = 31.3f,
                availableGb = 9.9f
            ),
            wifi = WifiStats(
                name = "LAN Direct Link",
                downloadSpeedBps = dlBps,
                uploadSpeedBps = ulBps,
                signalDbm = -42,
                latencyMs = 43
            ),
            gpu = GpuStats(
                name = "AMD Radeon 780M",
                utilizationPercent = 97f,
                vramMb = 512,
                temperatureCelsius = 74f
            ),
            timestampMs = System.currentTimeMillis()
        )
    }

    private class MockConnectionRepository(
        val stateFlow: MutableStateFlow<ConnectionState> = MutableStateFlow(
            ConnectionState.Connected(
                deviceId = "test-device",
                deviceName = "Test Laptop",
                host = "192.168.1.100",
                port = 8765
            )
        )
    ) : ConnectionRepository {
        override val connectionState: StateFlow<ConnectionState> = stateFlow
        override val activeHost: String? get() = "192.168.1.100"
        override val activePort: Int get() = 8765
        override val activeAuthToken: String? get() = "test-token"

        override suspend fun connectToDevice(device: com.pratyush.orbitmobile.domain.model.Device) = OrbitResult.Success(Unit)
        override suspend fun connectManual(host: String, port: Int) = OrbitResult.Success(Unit)
        override suspend fun pairWithQr(qrRawPayload: String) = OrbitResult.Error(Exception("Not used"))
        override suspend fun attemptReconnect() = OrbitResult.Success(Unit)
        override suspend fun disconnect() = OrbitResult.Success(Unit)
        override suspend fun sendPing() = OrbitResult.Success(10L)
        override suspend fun sendTelemetry(batteryPercent: Int, isCharging: Boolean, batteryStatusText: String, batteryHealth: String?, batteryTemperature: String?, chargeSource: String?, wifiConnected: Boolean, wifiSsid: String?, wifiFrequency: String?, wifiLinkSpeed: String?, bluetoothEnabled: Boolean, bluetoothConnected: Boolean, bluetoothStatusText: String, usedStorageGb: Double, totalStorageGb: Double, manufacturer: String?) = OrbitResult.Success(Unit)
        override suspend fun sendClipboard(type: String, textContent: String?, urlContent: String?, imageBase64: String?, imageFileName: String?) = OrbitResult.Success(Unit)
        override val clipboardHistory = MutableStateFlow<List<com.pratyush.orbitmobile.domain.model.ClipboardItem>>(emptyList())
        override suspend fun updateClipboardSyncState(enabled: Boolean) = OrbitResult.Success(Unit)
        override suspend fun clearClipboardHistory() = OrbitResult.Success(Unit)
        override fun receiveIncomingClipboardFromWindows(id: String, type: String, content: String, timestamp: Long, origin: String) {}
        override suspend fun refreshClipboardHistoryFromPc() = OrbitResult.Success(Unit)
    }

    private class MockRemoteDataSource(
        var telemetryToReturn: SystemTelemetry? = null
    ) : RemoteDataSource {
        var fetchCount = 0

        override suspend fun ping(host: String, port: Int) = OrbitResult.Success(10L)
        override suspend fun fetchRemoteDeviceInfo(host: String, port: Int) = OrbitResult.Error(Exception("Not used"))
        override suspend fun requestPairing(endpointUrl: String, sessionId: String, nonce: String, clientDeviceId: String, clientDeviceName: String, clientDeviceModel: String, osVersion: String, appVersion: String) = OrbitResult.Error(Exception("Not used"))
        override suspend fun sendTelemetry(host: String, port: Int, authToken: String, clientDeviceId: String, batteryPercent: Int, isCharging: Boolean, batteryStatusText: String, batteryHealth: String?, batteryTemperature: String?, chargeSource: String?, wifiConnected: Boolean, wifiSsid: String?, wifiFrequency: String?, wifiLinkSpeed: String?, bluetoothEnabled: Boolean, bluetoothConnected: Boolean, bluetoothStatusText: String, usedStorageGb: Double, totalStorageGb: Double, manufacturer: String?) = OrbitResult.Success(Unit)
        override suspend fun sendClipboard(host: String, port: Int, authToken: String, clientDeviceId: String, type: String, textContent: String?, urlContent: String?, imageBase64: String?, imageFileName: String?, deviceOrigin: String, id: String?, timestamp: Long?, contentHash: String?) = OrbitResult.Success(Unit)
        override suspend fun updateClipboardSyncState(host: String, port: Int, authToken: String, enabled: Boolean) = OrbitResult.Success(Unit)
        override suspend fun fetchClipboardHistory(host: String, port: Int, authToken: String) = OrbitResult.Success(emptyList<com.pratyush.orbitmobile.domain.model.ClipboardItem>())
        override suspend fun disconnect(host: String, port: Int, authToken: String) = OrbitResult.Success(Unit)
        override suspend fun reconnect(host: String, port: Int, authToken: String, clientDeviceId: String, clientDeviceName: String) = OrbitResult.Error(Exception("Not used"))
        override suspend fun fetchControlsStatus(host: String, port: Int, authToken: String) = OrbitResult.Error(Exception("Not used"))
        override suspend fun fetchWifiNetworks(host: String, port: Int, authToken: String) = OrbitResult.Error(Exception("Not used"))
        override suspend fun connectWifi(host: String, port: Int, authToken: String, ssid: String) = OrbitResult.Success(true)
        override suspend fun disconnectWifi(host: String, port: Int, authToken: String) = OrbitResult.Success(true)
        override suspend fun toggleWifiPower(host: String, port: Int, authToken: String, enabled: Boolean?) = OrbitResult.Success(true)
        override suspend fun toggleBluetoothPower(host: String, port: Int, authToken: String, enabled: Boolean?) = OrbitResult.Success(true)
        override suspend fun fetchBluetoothDevices(host: String, port: Int, authToken: String) = OrbitResult.Success(emptyList<com.pratyush.orbitmobile.domain.model.BluetoothDevice>())
        override suspend fun connectBluetoothDevice(host: String, port: Int, authToken: String, deviceId: String) = OrbitResult.Success(true)
        override suspend fun disconnectBluetoothDevice(host: String, port: Int, authToken: String, deviceId: String?) = OrbitResult.Success(true)
        override suspend fun toggleMicrophone(host: String, port: Int, authToken: String, mute: Boolean?) = OrbitResult.Error(Exception("Not used"))
        override suspend fun toggleCamera(host: String, port: Int, authToken: String, enabled: Boolean?) = OrbitResult.Error(Exception("Not used"))
        override suspend fun setVolume(host: String, port: Int, authToken: String, percentage: Int) = OrbitResult.Error(Exception("Not used"))
        override suspend fun toggleVolumeMute(host: String, port: Int, authToken: String, mute: Boolean?) = OrbitResult.Error(Exception("Not used"))
        override suspend fun fetchBrightness(host: String, port: Int, authToken: String) = OrbitResult.Error(Exception("Not used"))
        override suspend fun setBrightness(host: String, port: Int, authToken: String, percentage: Int) = OrbitResult.Error(Exception("Not used"))
        override fun listenToControlEvents(host: String, port: Int, authToken: String) = kotlinx.coroutines.flow.emptyFlow<com.pratyush.orbitmobile.domain.model.ControlEvent>()
        override suspend fun sendTrackpadEvents(host: String, port: Int, authToken: String, events: List<TrackpadInputEvent>) = OrbitResult.Success(Unit)
        override suspend fun releaseAllTrackpadButtons(host: String, port: Int, authToken: String) = OrbitResult.Success(Unit)

        override suspend fun fetchAiStatus(host: String, port: Int, authToken: String) = OrbitResult.Success(com.pratyush.orbitmobile.domain.model.AcpStatus())
        override suspend fun fetchAiConversations(host: String, port: Int, authToken: String) = OrbitResult.Success(emptyList<com.pratyush.orbitmobile.domain.model.AiConversation>())
        override suspend fun createAiConversation(host: String, port: Int, authToken: String, title: String?, conversationId: String?) = OrbitResult.Error(Exception("Not used"))
        override suspend fun fetchAiConversation(host: String, port: Int, authToken: String, conversationId: String) = OrbitResult.Error(Exception("Not used"))
        override suspend fun deleteAiConversation(host: String, port: Int, authToken: String, conversationId: String) = OrbitResult.Success(Unit)
        override suspend fun streamAiPrompt(host: String, port: Int, authToken: String, conversationId: String, prompt: String, userMessageId: String?, assistantMessageId: String?, onEvent: (com.pratyush.orbitmobile.domain.model.AiStreamEvent) -> Unit) = OrbitResult.Success(Unit)
        override suspend fun resolveAiPermission(host: String, port: Int, authToken: String, requestId: String, optionId: String) = OrbitResult.Success(true)
        override suspend fun setAiPolicy(host: String, port: Int, authToken: String, policy: String) = OrbitResult.Success(Unit)
        override suspend fun cancelAiPrompt(host: String, port: Int, authToken: String, conversationId: String) = OrbitResult.Success(Unit)

        override suspend fun fetchSystemStats(host: String, port: Int, authToken: String): OrbitResult<SystemTelemetry> {
            fetchCount++
            val t = telemetryToReturn ?: return OrbitResult.Error(Exception("No telemetry"))
            return OrbitResult.Success(t)
        }
    }

    @Test
    fun testInitialStateIsOffline() {
        val fakeConn = MockConnectionRepository()
        val fakeRemote = MockRemoteDataSource()
        val repo = SystemMonitorRepositoryImpl(fakeConn, fakeRemote, CoroutineScope(Dispatchers.Unconfined))

        val state = repo.systemMonitorData.value
        assertEquals(TelemetryFreshness.OFFLINE, state.freshness)
        assertNull(state.telemetry)
        assertTrue(state.cpuHistory.isEmpty())
    }

    @Test
    fun testTelemetryArrivalUpdatesLiveStateAndHistory() = runBlocking {
        val fakeConn = MockConnectionRepository()
        val fakeRemote = MockRemoteDataSource(createSampleTelemetry(41.2f))
        val repo = SystemMonitorRepositoryImpl(fakeConn, fakeRemote, CoroutineScope(Dispatchers.IO))

        repo.startMonitoring()
        delay(400L)

        val state = repo.systemMonitorData.value
        assertEquals(TelemetryFreshness.LIVE, state.freshness)
        assertNotNull(state.telemetry)
        assertEquals("AMD Ryzen 7 8840HS", state.telemetry?.cpu?.name)
        assertEquals(41.2f, state.telemetry?.cpu?.utilizationPercent)
        assertTrue(state.cpuHistory.isNotEmpty())
        assertEquals(41.2f, state.cpuHistory.last())

        repo.stopMonitoring()
    }

    @Test
    fun testWifiDualSpeedTracking() = runBlocking {
        val fakeConn = MockConnectionRepository()
        val fakeRemote = MockRemoteDataSource(createSampleTelemetry(dlBps = 21800.0, ulBps = 73600.0))
        val repo = SystemMonitorRepositoryImpl(fakeConn, fakeRemote, CoroutineScope(Dispatchers.IO))

        repo.startMonitoring()
        delay(400L)

        val state = repo.systemMonitorData.value
        assertTrue(state.wifiDownloadHistory.isNotEmpty())
        assertTrue(state.wifiUploadHistory.isNotEmpty())
        assertEquals(21800.0, state.wifiDownloadHistory.last(), 0.001)
        assertEquals(73600.0, state.wifiUploadHistory.last(), 0.001)

        repo.stopMonitoring()
    }

    @Test
    fun testDisconnectMarksOffline() = runBlocking {
        val fakeConn = MockConnectionRepository()
        val fakeRemote = MockRemoteDataSource(createSampleTelemetry())
        val repo = SystemMonitorRepositoryImpl(fakeConn, fakeRemote, CoroutineScope(Dispatchers.IO))

        repo.startMonitoring()
        delay(400L)
        assertEquals(TelemetryFreshness.LIVE, repo.systemMonitorData.value.freshness)

        // Disconnect
        fakeConn.stateFlow.value = ConnectionState.Disconnected
        delay(200L)

        assertEquals(TelemetryFreshness.OFFLINE, repo.systemMonitorData.value.freshness)

        repo.stopMonitoring()
    }
}
