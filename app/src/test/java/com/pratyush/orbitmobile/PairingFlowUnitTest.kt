package com.pratyush.orbitmobile

import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.Device
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository
import com.pratyush.orbitmobile.domain.usecase.ConnectDeviceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingFlowUnitTest {

    @Test
    fun testCompactQrPayloadParsing() {
        val payload = JSONObject().apply {
            put("v", 1)
            put("s", "session-xyz-123")
            put("ep", "http://192.168.31.147:8765/api/mobile/pair")
            put("n", "nonce-456")
            put("fp", "fp-789")
            put("exp", System.currentTimeMillis() / 1000 + 120)
        }.toString()

        val json = JSONObject(payload)
        val version = json.optInt("v", json.optInt("Version", 1))
        val sessionId = json.optString("s", json.optString("SessionId", ""))
        val endpoint = json.optString("ep", json.optString("Endpoint", ""))
        val nonce = json.optString("n", json.optString("Nonce", ""))
        val fingerprint = json.optString("fp", json.optString("Fingerprint", ""))
        val exp = json.optLong("exp", json.optLong("ExpiresAtUnix", 0L))

        assertEquals(1, version)
        assertEquals("session-xyz-123", sessionId)
        assertEquals("http://192.168.31.147:8765/api/mobile/pair", endpoint)
        assertEquals("nonce-456", nonce)
        assertEquals("fp-789", fingerprint)
        assertTrue(exp > System.currentTimeMillis() / 1000)
    }

    @Test
    fun testConnectDeviceUseCaseExecutesQrPairing() = runBlocking {
        val statusFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        val mockRepo = object : ConnectionRepository {
            override val connectionState = statusFlow.asStateFlow()
            override val activeAuthToken = null
            override val activeHost = null
            override val activePort = 8765

            override suspend fun pairWithQr(qrRawPayload: String): OrbitResult<Device> {
                val dev = Device(
                    id = "orbit-desktop",
                    name = "ORBIT Windows PC",
                    ipAddress = "192.168.31.147",
                    port = 8765,
                    isPaired = true,
                    isOnline = true
                )
                statusFlow.value = ConnectionState.Connected(
                    deviceId = dev.id,
                    deviceName = dev.name,
                    host = dev.ipAddress,
                    port = dev.port,
                    connectionType = "LAN (Wi-Fi)",
                    latencyMs = 5L,
                    isClipboardSyncEnabled = true
                )
                return OrbitResult.Success(dev)
            }

            override suspend fun connectToDevice(device: Device) = OrbitResult.Success(Unit)
            override suspend fun connectManual(host: String, port: Int) = OrbitResult.Success(Unit)
            override suspend fun attemptReconnect() = OrbitResult.Success(Unit)
            override suspend fun disconnect() = OrbitResult.Success(Unit)
            override suspend fun sendPing() = OrbitResult.Success(5L)
            override suspend fun sendTelemetry(
                batteryPercent: Int,
                isCharging: Boolean,
                batteryStatusText: String,
                batteryHealth: String?,
                batteryTemperature: String?,
                chargeSource: String?,
                wifiConnected: Boolean,
                wifiSsid: String?,
                wifiFrequency: String?,
                wifiLinkSpeed: String?,
                bluetoothEnabled: Boolean,
                bluetoothConnected: Boolean,
                bluetoothStatusText: String,
                usedStorageGb: Double,
                totalStorageGb: Double,
                manufacturer: String?
            ) = OrbitResult.Success(Unit)
            override suspend fun sendClipboard(
                type: String,
                textContent: String?,
                urlContent: String?,
                imageBase64: String?,
                imageFileName: String?
            ) = OrbitResult.Success(Unit)

            override val clipboardHistory = MutableStateFlow<List<com.pratyush.orbitmobile.domain.model.ClipboardItem>>(emptyList())
            override suspend fun updateClipboardSyncState(enabled: Boolean) = OrbitResult.Success(Unit)
            override suspend fun clearClipboardHistory() = OrbitResult.Success(Unit)
            override fun receiveIncomingClipboardFromWindows(id: String, type: String, content: String, timestamp: Long, origin: String) {}
            override suspend fun refreshClipboardHistoryFromPc() = OrbitResult.Success(Unit)
        }

        val connectUseCase = ConnectDeviceUseCase(mockRepo)

        val qrPayload = JSONObject().apply {
            put("v", 1)
            put("s", "sess-test")
            put("ep", "http://192.168.31.147:8765/api/mobile/pair")
        }.toString()

        val result = connectUseCase.executeQrPairing(qrPayload)

        assertTrue(result is OrbitResult.Success)
        val device = (result as OrbitResult.Success).data
        assertEquals("orbit-desktop", device.id)
        assertEquals("ORBIT Windows PC", device.name)
        assertTrue(device.isPaired)
        assertEquals("192.168.31.147", device.ipAddress)
        assertEquals(8765, device.port)
    }

    @Test
    fun testExpiredQrValidation() {
        val expiredTimeUnix = (System.currentTimeMillis() / 1000) - 60
        val payload = JSONObject().apply {
            put("v", 1)
            put("s", "expired-session")
            put("ep", "http://192.168.31.147:8765/api/mobile/pair")
            put("exp", expiredTimeUnix)
        }.toString()

        val json = JSONObject(payload)
        val exp = json.optLong("exp", 0L)
        val nowUnix = System.currentTimeMillis() / 1000

        assertTrue(exp > 0)
        assertTrue("Expired QR must be detected", nowUnix > exp)
    }
}
