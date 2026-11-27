package com.pratyush.orbitmobile.data.remote

import android.util.Log
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.domain.model.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class PairingResultData(
    val status: String,
    val serverDeviceId: String,
    val serverDeviceName: String,
    val authToken: String
)

/**
 * Interface contract for remote LAN communication with ORBIT Windows companion.
 */
interface RemoteDataSource {
    suspend fun ping(host: String, port: Int): OrbitResult<Long>
    suspend fun fetchRemoteDeviceInfo(host: String, port: Int): OrbitResult<Device>
    suspend fun requestPairing(
        endpointUrl: String,
        sessionId: String,
        nonce: String,
        clientDeviceId: String,
        clientDeviceName: String,
        clientDeviceModel: String,
        osVersion: String,
        appVersion: String
    ): OrbitResult<PairingResultData>
    suspend fun sendTelemetry(
        host: String,
        port: Int,
        authToken: String,
        clientDeviceId: String,
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
        host: String,
        port: Int,
        authToken: String,
        clientDeviceId: String,
        type: String,
        textContent: String?,
        urlContent: String?,
        imageBase64: String?,
        imageFileName: String?,
        deviceOrigin: String,
        id: String? = null,
        timestamp: Long? = null,
        contentHash: String? = null
    ): OrbitResult<Unit>
    suspend fun updateClipboardSyncState(
        host: String,
        port: Int,
        authToken: String,
        enabled: Boolean
    ): OrbitResult<Unit>
    suspend fun fetchClipboardHistory(host: String, port: Int, authToken: String): OrbitResult<List<com.pratyush.orbitmobile.domain.model.ClipboardItem>>
    suspend fun disconnect(host: String, port: Int, authToken: String): OrbitResult<Unit>
    suspend fun reconnect(
        host: String,
        port: Int,
        authToken: String,
        clientDeviceId: String,
        clientDeviceName: String
    ): OrbitResult<PairingResultData>

    // Laptop Remote Controls
    suspend fun fetchControlsStatus(host: String, port: Int, authToken: String): OrbitResult<com.pratyush.orbitmobile.domain.model.LaptopControlsStatus>
    suspend fun fetchWifiNetworks(host: String, port: Int, authToken: String): OrbitResult<List<com.pratyush.orbitmobile.domain.model.WifiNetworkItem>>
    suspend fun connectWifi(host: String, port: Int, authToken: String, ssid: String): OrbitResult<Boolean>
    suspend fun disconnectWifi(host: String, port: Int, authToken: String): OrbitResult<Boolean>
    suspend fun toggleWifiPower(host: String, port: Int, authToken: String, enabled: Boolean? = null): OrbitResult<Boolean>
    suspend fun toggleBluetoothPower(host: String, port: Int, authToken: String, enabled: Boolean? = null): OrbitResult<Boolean>
    suspend fun fetchBluetoothDevices(host: String, port: Int, authToken: String): OrbitResult<List<com.pratyush.orbitmobile.domain.model.BluetoothDevice>>
    suspend fun connectBluetoothDevice(host: String, port: Int, authToken: String, deviceId: String): OrbitResult<Boolean>
    suspend fun disconnectBluetoothDevice(host: String, port: Int, authToken: String, deviceId: String? = null): OrbitResult<Boolean>
    suspend fun toggleMicrophone(host: String, port: Int, authToken: String, mute: Boolean? = null): OrbitResult<com.pratyush.orbitmobile.domain.model.MicrophoneControlStatus>
    suspend fun toggleCamera(host: String, port: Int, authToken: String, enabled: Boolean? = null): OrbitResult<com.pratyush.orbitmobile.domain.model.CameraControlStatus>
    suspend fun setVolume(host: String, port: Int, authToken: String, percentage: Int): OrbitResult<com.pratyush.orbitmobile.domain.model.VolumeControlStatus>
    suspend fun toggleVolumeMute(host: String, port: Int, authToken: String, mute: Boolean? = null): OrbitResult<com.pratyush.orbitmobile.domain.model.VolumeControlStatus>
    suspend fun fetchBrightness(host: String, port: Int, authToken: String): OrbitResult<com.pratyush.orbitmobile.domain.model.BrightnessControlStatus>
    suspend fun setBrightness(host: String, port: Int, authToken: String, percentage: Int): OrbitResult<com.pratyush.orbitmobile.domain.model.BrightnessControlStatus>
    fun listenToControlEvents(host: String, port: Int, authToken: String): Flow<com.pratyush.orbitmobile.domain.model.ControlEvent>

    // Trackpad Input
    suspend fun sendTrackpadEvents(
        host: String,
        port: Int,
        authToken: String,
        events: List<com.pratyush.orbitmobile.domain.model.TrackpadInputEvent>
    ): OrbitResult<Unit>

    suspend fun releaseAllTrackpadButtons(
        host: String,
        port: Int,
        authToken: String
    ): OrbitResult<Unit>

    // System Monitor Telemetry
    suspend fun fetchSystemStats(
        host: String,
        port: Int,
        authToken: String
    ): OrbitResult<com.pratyush.orbitmobile.domain.model.SystemTelemetry>

    // Antigravity AI Endpoints
    suspend fun fetchAiStatus(host: String, port: Int, authToken: String): OrbitResult<com.pratyush.orbitmobile.domain.model.AcpStatus>
    suspend fun fetchAiConversations(host: String, port: Int, authToken: String): OrbitResult<List<com.pratyush.orbitmobile.domain.model.AiConversation>>
    suspend fun createAiConversation(host: String, port: Int, authToken: String, title: String?, conversationId: String? = null): OrbitResult<com.pratyush.orbitmobile.domain.model.AiConversation>
    suspend fun fetchAiConversation(host: String, port: Int, authToken: String, conversationId: String): OrbitResult<Pair<com.pratyush.orbitmobile.domain.model.AiConversation, List<com.pratyush.orbitmobile.domain.model.AiMessage>>>
    suspend fun deleteAiConversation(host: String, port: Int, authToken: String, conversationId: String): OrbitResult<Unit>
    suspend fun streamAiPrompt(host: String, port: Int, authToken: String, conversationId: String, prompt: String, userMessageId: String?, assistantMessageId: String?, onEvent: (com.pratyush.orbitmobile.domain.model.AiStreamEvent) -> Unit): OrbitResult<Unit>
    suspend fun resolveAiPermission(host: String, port: Int, authToken: String, requestId: String, optionId: String): OrbitResult<Boolean>
    suspend fun setAiPolicy(host: String, port: Int, authToken: String, policy: String): OrbitResult<Unit>
    suspend fun cancelAiPrompt(host: String, port: Int, authToken: String, conversationId: String): OrbitResult<Unit>

    // File Transfer
    suspend fun initFileTransfer(
        host: String,
        port: Int,
        authToken: String,
        transferId: String,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        totalChunks: Int,
        chunkSize: Int,
        sha256: String
    ): OrbitResult<Unit> = OrbitResult.Success(Unit)

    suspend fun sendFileChunk(
        host: String,
        port: Int,
        authToken: String,
        transferId: String,
        chunkIndex: Int,
        chunkSha256: String,
        chunkData: ByteArray,
        offset: Int,
        length: Int
    ): OrbitResult<Unit> = OrbitResult.Success(Unit)

    suspend fun completeFileTransfer(
        host: String,
        port: Int,
        authToken: String,
        transferId: String,
        sha256: String
    ): OrbitResult<Unit> = OrbitResult.Success(Unit)

    suspend fun cancelFileTransfer(
        host: String,
        port: Int,
        authToken: String,
        transferId: String
    ): OrbitResult<Unit> = OrbitResult.Success(Unit)

    suspend fun fetchReceivedFiles(
        host: String,
        port: Int,
        authToken: String
    ): OrbitResult<List<com.pratyush.orbitmobile.domain.model.ReceivedRemoteFile>> =
        OrbitResult.Success(emptyList())
}

/**
 * Production implementation of [RemoteDataSource] communicating over LAN HTTP.
 */
class DefaultRemoteDataSource : RemoteDataSource {

    override suspend fun ping(host: String, port: Int): OrbitResult<Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val url = URL("http://$host:$port/api/mobile/ping")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"

            val responseCode = conn.responseCode
            val latency = System.currentTimeMillis() - start
            conn.disconnect()

            if (responseCode == 200) {
                OrbitResult.Success(latency.coerceAtLeast(1L))
            } else {
                OrbitResult.Error(Exception("HTTP error $responseCode"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun fetchRemoteDeviceInfo(host: String, port: Int): OrbitResult<Device> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/ping")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val body = reader.readText()
                reader.close()
                conn.disconnect()

                val json = JSONObject(body)
                val serverName = json.optString("serverName", "ORBIT Windows PC")
                val device = Device(
                    id = "orbit-$host",
                    name = serverName,
                    ipAddress = host,
                    port = port,
                    isPaired = true,
                    isOnline = true,
                    osInfo = "Windows 11"
                )
                OrbitResult.Success(device)
            } else {
                conn.disconnect()
                OrbitResult.Error(Exception("Device info failed with code ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    companion object {
        private const val TAG = "OrbitRemoteDS"
    }

    override suspend fun requestPairing(
        endpointUrl: String,
        sessionId: String,
        nonce: String,
        clientDeviceId: String,
        clientDeviceName: String,
        clientDeviceModel: String,
        osVersion: String,
        appVersion: String
    ): OrbitResult<PairingResultData> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Requesting pairing: endpoint=$endpointUrl device=$clientDeviceName ($clientDeviceId)")
            val url = URL(endpointUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 65000 // Await user confirmation on PC
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")

            val payload = JSONObject().apply {
                put("sessionId", sessionId)
                put("nonce", nonce)
                put("clientDeviceId", clientDeviceId)
                put("clientDeviceName", clientDeviceName)
                put("clientDeviceModel", clientDeviceModel)
                put("osVersion", osVersion)
                put("appVersion", appVersion)
                put("receiverPort", 8768)
            }

            val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            val responseCode = conn.responseCode
            Log.i(TAG, "Pairing HTTP response code: $responseCode from $endpointUrl")

            val responseStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val reader = BufferedReader(InputStreamReader(responseStream))
            val responseBody = reader.readText()
            reader.close()
            conn.disconnect()

            if (responseCode == 200) {
                val json = JSONObject(responseBody)
                val status = json.optString("status", "")
                val serverDeviceId = json.optString("serverDeviceId", "")
                val serverDeviceName = json.optString("serverDeviceName", "ORBIT Windows PC")
                val authToken = json.optString("authToken", "")

                Log.i(TAG, "Pairing approved by Windows companion ($serverDeviceName)")
                OrbitResult.Success(
                    PairingResultData(
                        status = status,
                        serverDeviceId = serverDeviceId,
                        serverDeviceName = serverDeviceName,
                        authToken = authToken
                    )
                )
            } else {
                val errorMsg = try {
                    val json = JSONObject(responseBody)
                    val err = json.optString("error", "").ifBlank {
                        json.optString("message", "")
                    }
                    err.ifBlank { "Pairing rejected (code $responseCode)" }
                } catch (e: Exception) {
                    "Pairing rejected (code $responseCode)"
                }
                Log.w(TAG, "Pairing rejected by PC: $errorMsg (code $responseCode)")
                OrbitResult.Error(Exception(errorMsg))
            }
        } catch (e: Exception) {
            val host = try { URL(endpointUrl).host } catch (_: Exception) { "PC" }
            val port = try { URL(endpointUrl).port } catch (_: Exception) { 8765 }
            val diagnosticMsg = when {
                e.message?.contains("Cleartext HTTP traffic", ignoreCase = true) == true -> {
                    Log.e(TAG, "Cleartext HTTP traffic rejected by Android OS: ${e.message}")
                    "Android OS blocked cleartext HTTP traffic. Ensure debug network security configuration is enabled."
                }
                e is java.net.ConnectException -> {
                    Log.e(TAG, "Connection refused connecting to $endpointUrl: ${e.message}")
                    "Could not connect to ORBIT on your PC ($host:$port). Verify ORBIT is running on Windows and both devices are connected to the same Wi-Fi network."
                }
                e is java.net.SocketTimeoutException -> {
                    Log.e(TAG, "Socket timeout connecting to $endpointUrl: ${e.message}")
                    "Connection timed out waiting for ORBIT on your PC ($host). Check your firewall and Wi-Fi connection."
                }
                e is java.net.NoRouteToHostException -> {
                    Log.e(TAG, "No route to host $endpointUrl: ${e.message}")
                    "No network route to $host. Make sure your phone is connected to the same Wi-Fi network as your PC."
                }
                e is java.net.UnknownHostException -> {
                    Log.e(TAG, "Unknown host $endpointUrl: ${e.message}")
                    "Unable to resolve host: $host."
                }
                else -> {
                    Log.e(TAG, "Pairing network exception connecting to $endpointUrl: ${e.message}", e)
                    e.localizedMessage ?: "Network connection failed"
                }
            }
            OrbitResult.Error(Exception(diagnosticMsg, e))
        }
    }

    override suspend fun sendTelemetry(
        host: String,
        port: Int,
        authToken: String,
        clientDeviceId: String,
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
    ): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/telemetry")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $authToken")
            conn.setRequestProperty("Connection", "close")

            val payload = JSONObject().apply {
                put("authToken", authToken)
                put("clientDeviceId", clientDeviceId)
                put("batteryPercent", batteryPercent)
                put("isCharging", isCharging)
                put("batteryStatusText", batteryStatusText)
                if (batteryHealth != null) put("batteryHealth", batteryHealth)
                if (batteryTemperature != null) put("batteryTemperature", batteryTemperature)
                put("chargeSource", chargeSource ?: (if (isCharging) "Charger" else "None"))
                put("wifiConnected", wifiConnected)
                put("wifiSsid", wifiSsid ?: "")
                put("wifiFrequency", wifiFrequency ?: "")
                if (wifiLinkSpeed != null) put("wifiLinkSpeed", wifiLinkSpeed)
                put("bluetoothEnabled", bluetoothEnabled)
                put("bluetoothConnected", bluetoothConnected)
                put("bluetoothStatusText", bluetoothStatusText)
                put("usedStorageGb", usedStorageGb)
                put("totalStorageGb", totalStorageGb)
                if (manufacturer != null) put("manufacturer", manufacturer)
                put("receiverPort", 8768)
            }

            val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            val code = conn.responseCode
            val responseStream = if (code in 200..299) conn.inputStream else conn.errorStream
            try {
                responseStream?.use { it.readBytes() }
            } catch (_: Exception) {}
            conn.disconnect()

            if (code == 200) {
                OrbitResult.Success(Unit)
            } else {
                OrbitResult.Error(Exception("Telemetry failed with code $code"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun sendClipboard(
        host: String,
        port: Int,
        authToken: String,
        clientDeviceId: String,
        type: String,
        textContent: String?,
        urlContent: String?,
        imageBase64: String?,
        imageFileName: String?,
        deviceOrigin: String,
        id: String?,
        timestamp: Long?,
        contentHash: String?
    ): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/clipboard")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $authToken")

            val payload = JSONObject().apply {
                put("authToken", authToken)
                put("clientDeviceId", clientDeviceId)
                put("id", id ?: java.util.UUID.randomUUID().toString())
                put("timestamp", timestamp ?: System.currentTimeMillis())
                put("contentHash", contentHash ?: "")
                put("type", type)
                put("textContent", textContent ?: "")
                put("urlContent", urlContent ?: "")
                put("imageBase64", imageBase64 ?: "")
                put("imageFileName", imageFileName ?: "")
                put("deviceOrigin", deviceOrigin)
            }

            val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            val code = conn.responseCode
            val responseStream = if (code in 200..299) conn.inputStream else conn.errorStream
            try {
                responseStream?.use { it.readBytes() }
            } catch (_: Exception) {}
            conn.disconnect()

            if (code == 200) {
                OrbitResult.Success(Unit)
            } else {
                OrbitResult.Error(Exception("Clipboard send failed with code $code"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun updateClipboardSyncState(
        host: String,
        port: Int,
        authToken: String,
        enabled: Boolean
    ): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/clipboard/sync-state")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $authToken")

            val payload = JSONObject().apply {
                put("authToken", authToken)
                put("enabled", enabled)
            }

            val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            val code = conn.responseCode
            val responseStream = if (code in 200..299) conn.inputStream else conn.errorStream
            try {
                responseStream?.use { it.readBytes() }
            } catch (_: Exception) {}
            conn.disconnect()

            if (code == 200) {
                OrbitResult.Success(Unit)
            } else {
                OrbitResult.Error(Exception("Clipboard sync state update failed: $code"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun fetchClipboardHistory(
        host: String,
        port: Int,
        authToken: String
    ): OrbitResult<List<com.pratyush.orbitmobile.domain.model.ClipboardItem>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/clipboard/history")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $authToken")

            val code = conn.responseCode
            if (code == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val respText = reader.use { it.readText() }
                conn.disconnect()

                val json = JSONObject(respText)
                val itemsArr = json.optJSONArray("items") ?: org.json.JSONArray()
                val list = mutableListOf<com.pratyush.orbitmobile.domain.model.ClipboardItem>()
                for (i in 0 until itemsArr.length()) {
                    val obj = itemsArr.getJSONObject(i)
                    val content = obj.optString("content", "")
                    if (content.isNotBlank()) {
                        list.add(
                            com.pratyush.orbitmobile.domain.model.ClipboardItem(
                                id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                                type = obj.optString("type", "Text"),
                                content = content,
                                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                                origin = obj.optString("origin", "Windows")
                            )
                        )
                    }
                }
                OrbitResult.Success(list)
            } else {
                conn.disconnect()
                OrbitResult.Error(Exception("Failed to fetch clipboard history: HTTP $code"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun disconnect(host: String, port: Int, authToken: String): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/disconnect")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $authToken")

            val code = conn.responseCode
            conn.disconnect()
            OrbitResult.Success(Unit)
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun fetchControlsStatus(
        host: String,
        port: Int,
        authToken: String
    ): OrbitResult<com.pratyush.orbitmobile.domain.model.LaptopControlsStatus> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/controls/status")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $authToken")

            val code = conn.responseCode
            if (code == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val resp = reader.readText()
                reader.close()
                conn.disconnect()

                val json = JSONObject(resp)
                val wifiObj = json.optJSONObject("wifi")
                val btObj = json.optJSONObject("bluetooth")
                val micObj = json.optJSONObject("microphone")
                val camObj = json.optJSONObject("camera")
                val volObj = json.optJSONObject("volume")
                val brightObj = json.optJSONObject("brightness")
                val wifiSignal = if (wifiObj?.has("signal") == true && !wifiObj.isNull("signal")) wifiObj.optInt("signal") else null

                val status = com.pratyush.orbitmobile.domain.model.LaptopControlsStatus(
                    wifi = com.pratyush.orbitmobile.domain.model.WifiControlStatus(
                        isAvailable = wifiObj?.optBoolean("isAvailable", true) ?: true,
                        isEnabled = wifiObj?.optBoolean("isEnabled", true) ?: true,
                        isConnected = wifiObj?.optBoolean("isConnected", false) ?: false,
                        ssid = wifiObj?.takeIf { it.has("ssid") && !it.isNull("ssid") }?.getString("ssid"),
                        signal = wifiSignal,
                        detail = wifiObj?.takeIf { it.has("detail") && !it.isNull("detail") }?.getString("detail")
                    ),
                    bluetooth = com.pratyush.orbitmobile.domain.model.BluetoothControlStatus(
                        isAvailable = btObj?.optBoolean("isAvailable", true) ?: true,
                        isEnabled = btObj?.optBoolean("isEnabled", true) ?: true,
                        statusLabel = btObj?.optString("statusLabel", "No Devices") ?: "No Devices",
                        detail = btObj?.takeIf { it.has("detail") && !it.isNull("detail") }?.getString("detail")
                    ),
                    microphone = com.pratyush.orbitmobile.domain.model.MicrophoneControlStatus(
                        isAvailable = micObj?.optBoolean("isAvailable", true) ?: true,
                        isEnabled = micObj?.optBoolean("isEnabled", true) ?: true,
                        isMuted = micObj?.optBoolean("isMuted", false) ?: false,
                        statusLabel = micObj?.optString("statusLabel", "ON") ?: "ON"
                    ),
                    camera = com.pratyush.orbitmobile.domain.model.CameraControlStatus(
                        isAvailable = camObj?.optBoolean("isAvailable", true) ?: true,
                        isEnabled = camObj?.optBoolean("isEnabled", true) ?: true,
                        statusLabel = camObj?.optString("statusLabel", "Allowed") ?: "Allowed",
                        detail = camObj?.takeIf { it.has("detail") && !it.isNull("detail") }?.getString("detail")
                    ),
                    volume = com.pratyush.orbitmobile.domain.model.VolumeControlStatus(
                        isAvailable = volObj?.optBoolean("isAvailable", true) ?: true,
                        percentage = volObj?.optInt("percentage", 50) ?: 50,
                        isMuted = volObj?.optBoolean("isMuted", false) ?: false
                    ),
                    brightness = com.pratyush.orbitmobile.domain.model.BrightnessControlStatus(
                        isAvailable = brightObj?.optBoolean("isAvailable", true) ?: true,
                        percentage = brightObj?.optInt("percentage", 100) ?: 100,
                        statusLabel = brightObj?.optString("statusLabel", "100%") ?: "100%",
                        detail = brightObj?.takeIf { it.has("detail") && !it.isNull("detail") }?.getString("detail")
                    )
                )
                OrbitResult.Success(status)
            } else {
                conn.disconnect()
                OrbitResult.Error(Exception("Failed to fetch controls status: HTTP $code"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun fetchWifiNetworks(
        host: String,
        port: Int,
        authToken: String
    ): OrbitResult<List<com.pratyush.orbitmobile.domain.model.WifiNetworkItem>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/controls/wifi/networks")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $authToken")

            val code = conn.responseCode
            if (code == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val resp = reader.readText()
                reader.close()
                conn.disconnect()

                val json = JSONObject(resp)
                val arr = json.optJSONArray("networks") ?: org.json.JSONArray()
                val list = mutableListOf<com.pratyush.orbitmobile.domain.model.WifiNetworkItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        com.pratyush.orbitmobile.domain.model.WifiNetworkItem(
                            ssid = obj.optString("ssid", "Unknown"),
                            signal = obj.optInt("signal", 50),
                            authentication = obj.optString("authentication", ""),
                            isConnected = obj.optBoolean("isConnected", false)
                        )
                    )
                }
                OrbitResult.Success(list)
            } else {
                conn.disconnect()
                OrbitResult.Error(Exception("Failed to scan Wi-Fi networks: HTTP $code"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun connectWifi(
        host: String,
        port: Int,
        authToken: String,
        ssid: String
    ): OrbitResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/controls/wifi/connect")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $authToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("ssid", ssid)
            }.toString()

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload)
            writer.flush()
            writer.close()

            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) {
                OrbitResult.Success(true)
            } else {
                OrbitResult.Error(Exception("Wi-Fi connect failed: HTTP $code"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun disconnectWifi(
        host: String,
        port: Int,
        authToken: String
    ): OrbitResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/controls/wifi/disconnect")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $authToken")

            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) {
                OrbitResult.Success(true)
            } else {
                OrbitResult.Error(Exception("Wi-Fi disconnect failed: HTTP $code"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun toggleWifiPower(
        host: String,
        port: Int,
        authToken: String,
        enabled: Boolean?
    ): OrbitResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/controls/wifi/power")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $authToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val payload = JSONObject().apply {
                if (enabled != null) {
                    put("enabled", enabled)
                }
            }.toString()

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload)
            writer.flush()
            writer.close()

            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) {
                OrbitResult.Success(true)
            } else {
                OrbitResult.Error(Exception("Wi-Fi toggle failed: HTTP $code"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun toggleBluetoothPower(
        host: String,
        port: Int,
        authToken: String,
        enabled: Boolean?
    ): OrbitResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/controls/bluetooth/power")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $authToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val payload = JSONObject().apply {
                if (enabled != null) {
                    put("enabled", enabled)
                }
            }.toString()

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload)
            writer.flush()
            writer.close()

            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) {
                OrbitResult.Success(true)
            } else {
                OrbitResult.Error(Exception("Bluetooth power toggle failed: HTTP $code"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun fetchBluetoothDevices(
        host: String,
        port: Int,
        authToken: String
    ): OrbitResult<List<com.pratyush.orbitmobile.domain.model.BluetoothDevice>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/controls/bluetooth/devices")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $authToken")

            val code = conn.responseCode
            if (code == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val resp = reader.readText()
                reader.close()
                conn.disconnect()

                val json = JSONObject(resp)
                val arr = json.optJSONArray("devices") ?: org.json.JSONArray()
                val list = mutableListOf<com.pratyush.orbitmobile.domain.model.BluetoothDevice>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        com.pratyush.orbitmobile.domain.model.BluetoothDevice(
                            id = obj.optString("id", ""),
                            name = obj.optString("name", "Unknown Device"),
                            isPaired = obj.optBoolean("isPaired", false),
                            isConnected = obj.optBoolean("isConnected", false),
                            deviceType = obj.takeIf { it.has("deviceType") && !it.isNull("deviceType") }?.getString("deviceType")
                        )
                    )
                }
                OrbitResult.Success(list)
            } else {
                conn.disconnect()
                OrbitResult.Error(Exception("Failed to fetch Bluetooth devices: HTTP $code"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun connectBluetoothDevice(
        host: String,
        port: Int,
        authToken: String,
        deviceId: String
    ): OrbitResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/controls/bluetooth/connect")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $authToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("deviceId", deviceId)
            }.toString()

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload)
            writer.flush()
            writer.close()

            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) {
                OrbitResult.Success(true)
            } else {
                OrbitResult.Error(Exception("Bluetooth connect failed: HTTP $code"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun disconnectBluetoothDevice(
        host: String,
        port: Int,
        authToken: String,
        deviceId: String?
    ): OrbitResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/controls/bluetooth/disconnect")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $authToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val payload = JSONObject().apply {
                if (deviceId != null) {
                    put("deviceId", deviceId)
                }
            }.toString()

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload)
            writer.flush()
            writer.close()

            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) {
                OrbitResult.Success(true)
            } else {
                OrbitResult.Error(Exception("Bluetooth disconnect failed: HTTP $code"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun toggleMicrophone(
        host: String,
        port: Int,
        authToken: String,
        mute: Boolean?
    ): OrbitResult<com.pratyush.orbitmobile.domain.model.MicrophoneControlStatus> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/controls/microphone/toggle")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $authToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val payload = JSONObject().apply {
                if (mute != null) {
                    put("mute", mute)
                }
            }.toString()

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload)
            writer.flush()
            writer.close()

            val code = conn.responseCode
            if (code == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val resp = reader.readText()
                reader.close()
                conn.disconnect()

                val json = JSONObject(resp)
                val status = com.pratyush.orbitmobile.domain.model.MicrophoneControlStatus(
                    isAvailable = true,
                    isEnabled = json.optBoolean("isEnabled", true),
                    isMuted = json.optBoolean("isMuted", false),
                    statusLabel = json.optString("statusLabel", "ON")
                )
                OrbitResult.Success(status)
            } else {
                conn.disconnect()
                OrbitResult.Error(Exception("Failed to toggle microphone: HTTP $code"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun toggleCamera(
        host: String,
        port: Int,
        authToken: String,
        enabled: Boolean?
    ): OrbitResult<com.pratyush.orbitmobile.domain.model.CameraControlStatus> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/controls/camera/toggle")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $authToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val payload = JSONObject().apply {
                if (enabled != null) {
                    put("enabled", enabled)
                }
            }.toString()

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload)
            writer.flush()
            writer.close()

            val code = conn.responseCode
            if (code == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val resp = reader.readText()
                reader.close()
                conn.disconnect()

                val json = JSONObject(resp)
                val status = com.pratyush.orbitmobile.domain.model.CameraControlStatus(
                    isAvailable = true,
                    isEnabled = json.optBoolean("isEnabled", true),
                    statusLabel = json.optString("statusLabel", "Allowed"),
                    detail = json.takeIf { it.has("detail") && !it.isNull("detail") }?.getString("detail")
                )
                OrbitResult.Success(status)
            } else {
                conn.disconnect()
                OrbitResult.Error(Exception("Failed to toggle camera: HTTP $code"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun setVolume(
        host: String,
        port: Int,
        authToken: String,
        percentage: Int
    ): OrbitResult<com.pratyush.orbitmobile.domain.model.VolumeControlStatus> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/controls/volume")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $authToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("percentage", percentage)
            }.toString()

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload)
            writer.flush()
            writer.close()

            val code = conn.responseCode
            if (code == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val resp = reader.readText()
                reader.close()
                conn.disconnect()

                val json = JSONObject(resp)
                val status = com.pratyush.orbitmobile.domain.model.VolumeControlStatus(
                    isAvailable = true,
                    percentage = json.optInt("percentage", percentage),
                    isMuted = json.optBoolean("isMuted", false)
                )
                OrbitResult.Success(status)
            } else {
                conn.disconnect()
                OrbitResult.Error(Exception("Failed to set volume: HTTP $code"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun toggleVolumeMute(
        host: String,
        port: Int,
        authToken: String,
        mute: Boolean?
    ): OrbitResult<com.pratyush.orbitmobile.domain.model.VolumeControlStatus> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/controls/volume/mute")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $authToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val payload = JSONObject().apply {
                if (mute != null) {
                    put("mute", mute)
                }
            }.toString()

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload)
            writer.flush()
            writer.close()

            val code = conn.responseCode
            if (code == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val resp = reader.readText()
                reader.close()
                conn.disconnect()

                val json = JSONObject(resp)
                val status = com.pratyush.orbitmobile.domain.model.VolumeControlStatus(
                    isAvailable = true,
                    percentage = json.optInt("percentage", 50),
                    isMuted = json.optBoolean("isMuted", false)
                )
                OrbitResult.Success(status)
            } else {
                conn.disconnect()
                OrbitResult.Error(Exception("Failed to toggle volume mute: HTTP $code"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun reconnect(
        host: String,
        port: Int,
        authToken: String,
        clientDeviceId: String,
        clientDeviceName: String
    ): OrbitResult<PairingResultData> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/reconnect")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $authToken")
            conn.setRequestProperty("Connection", "close")

            val payload = JSONObject().apply {
                put("clientDeviceId", clientDeviceId)
                put("clientDeviceName", clientDeviceName)
                put("authToken", authToken)
                put("receiverPort", 8768)
            }

            val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            val responseCode = conn.responseCode
            val responseStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val reader = BufferedReader(InputStreamReader(responseStream))
            val responseBody = reader.readText()
            reader.close()
            conn.disconnect()

            if (responseCode == 200) {
                val json = JSONObject(responseBody)
                OrbitResult.Success(
                    PairingResultData(
                        status = json.optString("status", "reconnected"),
                        serverDeviceId = json.optString("serverDeviceId", ""),
                        serverDeviceName = json.optString("serverDeviceName", "ORBIT Windows PC"),
                        authToken = json.optString("authToken", authToken)
                    )
                )
            } else {
                OrbitResult.Error(Exception("Reconnect failed with HTTP $responseCode: $responseBody"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun fetchBrightness(
        host: String,
        port: Int,
        authToken: String
    ): OrbitResult<com.pratyush.orbitmobile.domain.model.BrightnessControlStatus> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/controls/brightness")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $authToken")

            val code = conn.responseCode
            if (code == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val resp = reader.readText()
                reader.close()
                conn.disconnect()

                val json = JSONObject(resp)
                OrbitResult.Success(
                    com.pratyush.orbitmobile.domain.model.BrightnessControlStatus(
                        isAvailable = json.optBoolean("isAvailable", true),
                        percentage = json.optInt("percentage", 100),
                        statusLabel = json.optString("statusLabel", "100%"),
                        detail = json.optString("detail", null)
                    )
                )
            } else {
                conn.disconnect()
                OrbitResult.Error(Exception("Fetch brightness failed: HTTP $code"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun setBrightness(
        host: String,
        port: Int,
        authToken: String,
        percentage: Int
    ): OrbitResult<com.pratyush.orbitmobile.domain.model.BrightnessControlStatus> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/mobile/controls/brightness")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $authToken")

            val payload = JSONObject().apply {
                put("percentage", percentage)
            }
            val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            val code = conn.responseCode
            if (code == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val resp = reader.readText()
                reader.close()
                conn.disconnect()

                val json = JSONObject(resp)
                OrbitResult.Success(
                    com.pratyush.orbitmobile.domain.model.BrightnessControlStatus(
                        isAvailable = json.optBoolean("isAvailable", true),
                        percentage = json.optInt("percentage", percentage),
                        statusLabel = json.optString("statusLabel", "$percentage%"),
                        detail = json.optString("detail", null)
                    )
                )
            } else {
                conn.disconnect()
                OrbitResult.Error(Exception("Set brightness failed: HTTP $code"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override fun listenToControlEvents(
        host: String,
        port: Int,
        authToken: String
    ): Flow<com.pratyush.orbitmobile.domain.model.ControlEvent> = flow {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/mobile/controls/events")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 0 // Infinite read timeout for SSE stream
                requestMethod = "GET"
                setRequestProperty("Accept", "text/event-stream")
                setRequestProperty("Authorization", "Bearer $authToken")
            }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                var currentEvent: String? = null
                val dataBuilder = StringBuilder()

                while (currentCoroutineContext().isActive) {
                    val line = reader.readLine() ?: break
                    if (line.startsWith("event:")) {
                        currentEvent = line.substring(6).trim()
                    } else if (line.startsWith("data:")) {
                        if (dataBuilder.isNotEmpty()) dataBuilder.append("\n")
                        dataBuilder.append(line.substring(5).trim())
                    } else if (line.isEmpty()) {
                        // Dispatch event
                        if (dataBuilder.isNotEmpty()) {
                            val dataStr = dataBuilder.toString()
                            try {
                                val json = JSONObject(dataStr)
                                when (currentEvent) {
                                    "controls_snapshot" -> {
                                        val wifiObj = json.optJSONObject("wifi")
                                        val btObj = json.optJSONObject("bluetooth")
                                        val micObj = json.optJSONObject("microphone")
                                        val camObj = json.optJSONObject("camera")
                                        val volObj = json.optJSONObject("volume")
                                        val brightObj = json.optJSONObject("brightness")
                                        val wifiSignal = if (wifiObj?.has("signal") == true && !wifiObj.isNull("signal")) wifiObj.optInt("signal") else null

                                        val snapshot = com.pratyush.orbitmobile.domain.model.LaptopControlsStatus(
                                            wifi = com.pratyush.orbitmobile.domain.model.WifiControlStatus(
                                                isAvailable = wifiObj?.optBoolean("isAvailable", true) ?: true,
                                                isEnabled = wifiObj?.optBoolean("isEnabled", true) ?: true,
                                                isConnected = wifiObj?.optBoolean("isConnected", false) ?: false,
                                                ssid = wifiObj?.takeIf { it.has("ssid") && !it.isNull("ssid") }?.getString("ssid"),
                                                signal = wifiSignal,
                                                detail = wifiObj?.takeIf { it.has("detail") && !it.isNull("detail") }?.getString("detail")
                                            ),
                                            bluetooth = com.pratyush.orbitmobile.domain.model.BluetoothControlStatus(
                                                isAvailable = btObj?.optBoolean("isAvailable", true) ?: true,
                                                isEnabled = btObj?.optBoolean("isEnabled", true) ?: true,
                                                statusLabel = btObj?.optString("statusLabel", "No Devices") ?: "No Devices",
                                                detail = btObj?.takeIf { it.has("detail") && !it.isNull("detail") }?.getString("detail")
                                            ),
                                            microphone = com.pratyush.orbitmobile.domain.model.MicrophoneControlStatus(
                                                isAvailable = micObj?.optBoolean("isAvailable", true) ?: true,
                                                isEnabled = micObj?.optBoolean("isEnabled", true) ?: true,
                                                isMuted = micObj?.optBoolean("isMuted", false) ?: false,
                                                statusLabel = micObj?.optString("statusLabel", "ON") ?: "ON"
                                            ),
                                            camera = com.pratyush.orbitmobile.domain.model.CameraControlStatus(
                                                isAvailable = camObj?.optBoolean("isAvailable", true) ?: true,
                                                isEnabled = camObj?.optBoolean("isEnabled", true) ?: true,
                                                statusLabel = camObj?.optString("statusLabel", "Allowed") ?: "Allowed",
                                                detail = camObj?.takeIf { it.has("detail") && !it.isNull("detail") }?.getString("detail")
                                            ),
                                            volume = com.pratyush.orbitmobile.domain.model.VolumeControlStatus(
                                                isAvailable = volObj?.optBoolean("isAvailable", true) ?: true,
                                                percentage = volObj?.optInt("percentage", 50) ?: 50,
                                                isMuted = volObj?.optBoolean("isMuted", false) ?: false
                                            ),
                                            brightness = com.pratyush.orbitmobile.domain.model.BrightnessControlStatus(
                                                isAvailable = brightObj?.optBoolean("isAvailable", true) ?: true,
                                                percentage = brightObj?.optInt("percentage", 100) ?: 100,
                                                statusLabel = brightObj?.optString("statusLabel", "100%") ?: "100%",
                                                detail = brightObj?.takeIf { it.has("detail") && !it.isNull("detail") }?.getString("detail")
                                            )
                                        )
                                        emit(com.pratyush.orbitmobile.domain.model.ControlEvent.Snapshot(snapshot))
                                    }
                                    "control_update" -> {
                                        val type = json.optString("type", "")
                                        when (type) {
                                            "volume" -> {
                                                emit(
                                                    com.pratyush.orbitmobile.domain.model.ControlEvent.VolumeUpdate(
                                                        percentage = json.optInt("percentage", 0),
                                                        isMuted = json.optBoolean("isMuted", false)
                                                    )
                                                )
                                            }
                                            "brightness" -> {
                                                emit(
                                                    com.pratyush.orbitmobile.domain.model.ControlEvent.BrightnessUpdate(
                                                        percentage = json.optInt("percentage", 100),
                                                        isAvailable = json.optBoolean("isAvailable", true),
                                                        statusLabel = json.optString("statusLabel", "100%")
                                                    )
                                                )
                                            }
                                            else -> {
                                                emit(
                                                    com.pratyush.orbitmobile.domain.model.ControlEvent.GenericControlUpdate(
                                                        type = type,
                                                        isAvailable = json.optBoolean("isAvailable", true),
                                                        isEnabled = json.optBoolean("isEnabled", true),
                                                        statusLabel = json.optString("statusLabel", ""),
                                                        detail = json.optString("detail", null)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("RemoteDataSource", "Error parsing SSE data: ${e.message}")
                            }
                            dataBuilder.setLength(0)
                        }
                        currentEvent = null
                    }
                }
                reader.close()
            }
        } catch (e: Exception) {
            Log.d("RemoteDataSource", "SSE stream ended or error: ${e.message}")
        } finally {
            conn?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun sendTrackpadEvents(
        host: String,
        port: Int,
        authToken: String,
        events: List<com.pratyush.orbitmobile.domain.model.TrackpadInputEvent>
    ): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        if (events.isEmpty()) return@withContext OrbitResult.Success(Unit)

        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/mobile/trackpad/input")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 1500
                readTimeout = 2000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $authToken")
            }

            val payload = if (events.size == 1) {
                when (val ev = events[0]) {
                    is com.pratyush.orbitmobile.domain.model.TrackpadInputEvent.Move -> JSONObject().apply {
                        put("action", "move")
                        put("dx", ev.dx)
                        put("dy", ev.dy)
                    }
                    is com.pratyush.orbitmobile.domain.model.TrackpadInputEvent.Button -> JSONObject().apply {
                        put("action", "button")
                        put("button", ev.button.wireName)
                        put("state", ev.state.wireName)
                    }
                    is com.pratyush.orbitmobile.domain.model.TrackpadInputEvent.Scroll -> JSONObject().apply {
                        put("action", "scroll")
                        put("dx", ev.dx)
                        put("dy", ev.dy)
                    }
                    is com.pratyush.orbitmobile.domain.model.TrackpadInputEvent.Zoom -> JSONObject().apply {
                        put("action", "zoom")
                        put("delta", ev.delta)
                    }
                    is com.pratyush.orbitmobile.domain.model.TrackpadInputEvent.ReleaseAll -> JSONObject().apply {
                        put("action", "release_all")
                    }
                }
            } else {
                val jsonArr = org.json.JSONArray()
                for (ev in events) {
                    val obj = when (ev) {
                        is com.pratyush.orbitmobile.domain.model.TrackpadInputEvent.Move -> JSONObject().apply {
                            put("action", "move")
                            put("dx", ev.dx)
                            put("dy", ev.dy)
                        }
                        is com.pratyush.orbitmobile.domain.model.TrackpadInputEvent.Button -> JSONObject().apply {
                            put("action", "button")
                            put("button", ev.button.wireName)
                            put("state", ev.state.wireName)
                        }
                        is com.pratyush.orbitmobile.domain.model.TrackpadInputEvent.Scroll -> JSONObject().apply {
                            put("action", "scroll")
                            put("dx", ev.dx)
                            put("dy", ev.dy)
                        }
                        is com.pratyush.orbitmobile.domain.model.TrackpadInputEvent.Zoom -> JSONObject().apply {
                            put("action", "zoom")
                            put("delta", ev.delta)
                        }
                        is com.pratyush.orbitmobile.domain.model.TrackpadInputEvent.ReleaseAll -> JSONObject().apply {
                            put("action", "release_all")
                        }
                    }
                    jsonArr.put(obj)
                }
                JSONObject().apply {
                    put("events", jsonArr)
                }
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                OrbitResult.Success(Unit)
            } else {
                OrbitResult.Error(Exception("Trackpad input rejected by server with HTTP $responseCode"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun releaseAllTrackpadButtons(
        host: String,
        port: Int,
        authToken: String
    ): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/mobile/trackpad/release")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 1500
                readTimeout = 2000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $authToken")
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write("{}") }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                OrbitResult.Success(Unit)
            } else {
                OrbitResult.Error(Exception("Trackpad release rejected with HTTP $responseCode"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun fetchSystemStats(
        host: String,
        port: Int,
        authToken: String
    ): OrbitResult<com.pratyush.orbitmobile.domain.model.SystemTelemetry> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/mobile/system/stats")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2500
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $authToken")
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)

                // 1. CPU
                val cpuObj = root.optJSONObject("cpu") ?: JSONObject()
                val cpuName = cpuObj.optString("name", "Processor")
                val cpuUtil = cpuObj.optDouble("utilization", 0.0).toFloat()
                val cpuCores = cpuObj.optInt("cores", 8)
                val cpuThreads = cpuObj.optInt("threads", 16)
                val cpuTemp = if (cpuObj.has("temperature") && !cpuObj.isNull("temperature")) {
                    cpuObj.optDouble("temperature").toFloat()
                } else null
                val cpu = com.pratyush.orbitmobile.domain.model.CpuStats(
                    name = cpuName,
                    utilizationPercent = cpuUtil,
                    cores = cpuCores,
                    threads = cpuThreads,
                    temperatureCelsius = cpuTemp
                )

                // 2. RAM
                val ramObj = root.optJSONObject("ram") ?: JSONObject()
                val ramSpec = ramObj.optString("spec", "RAM")
                val ramUtil = ramObj.optDouble("utilization", 0.0).toFloat()
                val ramUsed = ramObj.optDouble("usedGb", 0.0).toFloat()
                val ramTotal = ramObj.optDouble("totalGb", 0.0).toFloat()
                val ramAvail = ramObj.optDouble("availableGb", 0.0).toFloat()
                val ram = com.pratyush.orbitmobile.domain.model.RamStats(
                    spec = ramSpec,
                    utilizationPercent = ramUtil,
                    usedGb = ramUsed,
                    totalGb = ramTotal,
                    availableGb = ramAvail
                )

                // 3. Wi-Fi
                val wifiObj = root.optJSONObject("wifi") ?: JSONObject()
                val wifiName = wifiObj.optString("name", "LAN Direct Link")
                val dlBps = wifiObj.optDouble("downloadSpeedBps", 0.0)
                val ulBps = wifiObj.optDouble("uploadSpeedBps", 0.0)
                val signalDbm = if (wifiObj.has("signalDbm") && !wifiObj.isNull("signalDbm")) {
                    wifiObj.optInt("signalDbm")
                } else null
                val latencyMs = if (wifiObj.has("latencyMs") && !wifiObj.isNull("latencyMs")) {
                    wifiObj.optInt("latencyMs")
                } else null
                val wifi = com.pratyush.orbitmobile.domain.model.WifiStats(
                    name = wifiName,
                    downloadSpeedBps = dlBps,
                    uploadSpeedBps = ulBps,
                    signalDbm = signalDbm,
                    latencyMs = latencyMs
                )

                // 4. GPU
                val gpuObj = root.optJSONObject("gpu") ?: JSONObject()
                val gpuName = gpuObj.optString("name", "Graphics")
                val gpuUtil = gpuObj.optDouble("utilization", 0.0).toFloat()
                val vramMb = if (gpuObj.has("vramMb") && !gpuObj.isNull("vramMb")) {
                    gpuObj.optLong("vramMb")
                } else null
                val gpuTemp = if (gpuObj.has("temperature") && !gpuObj.isNull("temperature")) {
                    gpuObj.optDouble("temperature").toFloat()
                } else null
                val gpu = com.pratyush.orbitmobile.domain.model.GpuStats(
                    name = gpuName,
                    utilizationPercent = gpuUtil,
                    vramMb = vramMb,
                    temperatureCelsius = gpuTemp
                )

                val timestamp = root.optLong("timestampMs", System.currentTimeMillis())

                OrbitResult.Success(
                    com.pratyush.orbitmobile.domain.model.SystemTelemetry(
                        cpu = cpu,
                        ram = ram,
                        wifi = wifi,
                        gpu = gpu,
                        timestampMs = timestamp
                    )
                )
            } else {
                OrbitResult.Error(Exception("Failed to fetch system stats: HTTP $responseCode"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun fetchAiStatus(
        host: String,
        port: Int,
        authToken: String
    ): OrbitResult<com.pratyush.orbitmobile.domain.model.AcpStatus> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/mobile/ai/status")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("X-Orbit-Token", authToken)
            }

            val code = conn.responseCode
            if (code == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val status = com.pratyush.orbitmobile.domain.model.AcpStatus(
                    isAvailable = json.optBoolean("IsAvailable", false),
                    state = json.optString("State", "unavailable"),
                    backend = json.optString("Backend", "antigravity-acp"),
                    message = if (json.has("Message") && !json.isNull("Message")) json.optString("Message") else null,
                    permissionMode = json.optString("PermissionMode", "AutoApproveSafe")
                )
                OrbitResult.Success(status)
            } else {
                OrbitResult.Error(Exception("HTTP $code from AI status"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun fetchAiConversations(
        host: String,
        port: Int,
        authToken: String
    ): OrbitResult<List<com.pratyush.orbitmobile.domain.model.AiConversation>> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/mobile/ai/conversations")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("X-Orbit-Token", authToken)
            }

            val code = conn.responseCode
            if (code == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = org.json.JSONArray(responseText)
                val list = ArrayList<com.pratyush.orbitmobile.domain.model.AiConversation>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        com.pratyush.orbitmobile.domain.model.AiConversation(
                            id = obj.getString("Id"),
                            title = obj.getString("Title"),
                            backendId = obj.optString("BackendId", "antigravity-acp"),
                            workspacePath = if (obj.has("WorkspacePath") && !obj.isNull("WorkspacePath")) obj.getString("WorkspacePath") else null,
                            createdAtUtc = System.currentTimeMillis(),
                            updatedAtUtc = System.currentTimeMillis(),
                            isPinned = obj.optBoolean("IsPinned", false),
                            isArchived = obj.optBoolean("IsArchived", false),
                            origin = obj.optString("Origin", "Mobile"),
                            isSynced = true
                        )
                    )
                }
                OrbitResult.Success(list)
            } else {
                OrbitResult.Error(Exception("HTTP $code from AI conversations"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun createAiConversation(
        host: String,
        port: Int,
        authToken: String,
        title: String?,
        conversationId: String?
    ): OrbitResult<com.pratyush.orbitmobile.domain.model.AiConversation> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/mobile/ai/conversations/new")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("X-Orbit-Token", authToken)
            }

            val body = JSONObject().apply {
                if (title != null) put("title", title)
                if (conversationId != null) put("id", conversationId)
            }
            conn.outputStream.bufferedWriter().use { it.write(body.toString()) }

            val code = conn.responseCode
            if (code == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(responseText)
                val conv = com.pratyush.orbitmobile.domain.model.AiConversation(
                    id = obj.getString("Id"),
                    title = obj.getString("Title"),
                    backendId = obj.optString("BackendId", "antigravity-acp"),
                    workspacePath = if (obj.has("WorkspacePath") && !obj.isNull("WorkspacePath")) obj.getString("WorkspacePath") else null,
                    createdAtUtc = System.currentTimeMillis(),
                    updatedAtUtc = System.currentTimeMillis(),
                    isPinned = obj.optBoolean("IsPinned", false),
                    isArchived = obj.optBoolean("IsArchived", false),
                    origin = obj.optString("Origin", "Mobile"),
                    isSynced = true
                )
                OrbitResult.Success(conv)
            } else {
                OrbitResult.Error(Exception("HTTP $code from create AI conversation"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun fetchAiConversation(
        host: String,
        port: Int,
        authToken: String,
        conversationId: String
    ): OrbitResult<Pair<com.pratyush.orbitmobile.domain.model.AiConversation, List<com.pratyush.orbitmobile.domain.model.AiMessage>>> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/mobile/ai/conversations/$conversationId")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("X-Orbit-Token", authToken)
            }

            val code = conn.responseCode
            if (code == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(responseText)
                val cObj = root.getJSONObject("conversation")
                val conv = com.pratyush.orbitmobile.domain.model.AiConversation(
                    id = cObj.getString("Id"),
                    title = cObj.getString("Title"),
                    backendId = cObj.optString("BackendId", "antigravity-acp"),
                    workspacePath = if (cObj.has("WorkspacePath") && !cObj.isNull("WorkspacePath")) cObj.getString("WorkspacePath") else null,
                    createdAtUtc = System.currentTimeMillis(),
                    updatedAtUtc = System.currentTimeMillis(),
                    isPinned = cObj.optBoolean("IsPinned", false),
                    isArchived = cObj.optBoolean("IsArchived", false),
                    origin = cObj.optString("Origin", "Mobile"),
                    isSynced = true
                )

                val mArr = root.getJSONArray("messages")
                val messages = ArrayList<com.pratyush.orbitmobile.domain.model.AiMessage>(mArr.length())
                for (i in 0 until mArr.length()) {
                    val mObj = mArr.getJSONObject(i)
                    val toolsList = ArrayList<com.pratyush.orbitmobile.domain.model.AiToolActivity>()
                    if (mObj.has("ToolActivities") && !mObj.isNull("ToolActivities")) {
                        val tArr = mObj.getJSONArray("ToolActivities")
                        for (j in 0 until tArr.length()) {
                            val tObj = tArr.getJSONObject(j)
                            val rowId = tObj.getString("Id")
                            val callId = if (tObj.has("ToolCallId") && !tObj.isNull("ToolCallId")) {
                                tObj.getString("ToolCallId").ifBlank { rowId }
                            } else rowId

                            toolsList.add(
                                com.pratyush.orbitmobile.domain.model.AiToolActivity(
                                    id = rowId,
                                    messageId = tObj.getString("MessageId"),
                                    toolCallId = callId,
                                    toolName = tObj.getString("ToolName"),
                                    status = tObj.getString("Status"),
                                    detail = if (tObj.has("ArgumentsSummary") && !tObj.isNull("ArgumentsSummary")) tObj.getString("ArgumentsSummary") else null,
                                    timestampUtc = System.currentTimeMillis()
                                )
                            )
                        }
                    }

                    messages.add(
                        com.pratyush.orbitmobile.domain.model.AiMessage(
                            id = mObj.getString("Id"),
                            conversationId = mObj.getString("ConversationId"),
                            role = mObj.getString("Role"),
                            content = mObj.getString("Content"),
                            timestampUtc = System.currentTimeMillis(),
                            generationStatus = mObj.optString("GenerationStatus", "completed"),
                            isSynced = true,
                            toolActivities = toolsList
                        )
                    )
                }

                OrbitResult.Success(Pair(conv, messages))
            } else {
                OrbitResult.Error(Exception("HTTP $code from fetch conversation"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun deleteAiConversation(
        host: String,
        port: Int,
        authToken: String,
        conversationId: String
    ): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/mobile/ai/conversations/$conversationId")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("X-Orbit-Token", authToken)
            }
            val code = conn.responseCode
            if (code == 200) OrbitResult.Success(Unit)
            else OrbitResult.Error(Exception("HTTP $code deleting conversation"))
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun streamAiPrompt(
        host: String,
        port: Int,
        authToken: String,
        conversationId: String,
        prompt: String,
        userMessageId: String?,
        assistantMessageId: String?,
        onEvent: (com.pratyush.orbitmobile.domain.model.AiStreamEvent) -> Unit
    ): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/mobile/ai/prompt")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 6000
                readTimeout = 90000 // Antigravity reasoning can take up to 90 seconds
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "text/event-stream")
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("X-Orbit-Token", authToken)
            }

            val body = JSONObject().apply {
                put("conversationId", conversationId)
                put("prompt", prompt)
                if (userMessageId != null) put("userMessageId", userMessageId)
                if (assistantMessageId != null) put("assistantMessageId", assistantMessageId)
            }
            conn.outputStream.bufferedWriter().use { it.write(body.toString()) }

            val code = conn.responseCode
            if (code == 200) {
                var currentEventType = "message"
                val reader = conn.inputStream.bufferedReader()
                var line: String? = reader.readLine()
                while (line != null) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("event:")) {
                        currentEventType = trimmed.substring(6).trim()
                    } else if (trimmed.startsWith("data:")) {
                        val dataStr = trimmed.substring(5).trim()
                        if (dataStr.isNotEmpty()) {
                            try {
                                val dObj = JSONObject(dataStr)
                                val evType = dObj.optString("Type", currentEventType)
                                val cId = dObj.optString("ConversationId", conversationId)
                                val mId = if (dObj.has("MessageId") && !dObj.isNull("MessageId")) dObj.getString("MessageId") else null
                                val txt = if (dObj.has("Text") && !dObj.isNull("Text")) dObj.getString("Text") else null
                                val tName = if (dObj.has("ToolName") && !dObj.isNull("ToolName")) dObj.getString("ToolName") else null
                                val tStatus = if (dObj.has("ToolStatus") && !dObj.isNull("ToolStatus")) dObj.getString("ToolStatus") else null
                                val tDetail = if (dObj.has("ToolDetail") && !dObj.isNull("ToolDetail")) dObj.getString("ToolDetail") else null
                                val tCallId = when {
                                    dObj.has("ToolCallId") && !dObj.isNull("ToolCallId") -> dObj.getString("ToolCallId")
                                    dObj.has("toolCallId") && !dObj.isNull("toolCallId") -> dObj.getString("toolCallId")
                                    else -> null
                                } ?: run {
                                    if (!tDetail.isNullOrBlank()) {
                                        try {
                                            val tdObj = JSONObject(tDetail)
                                            when {
                                                tdObj.has("toolCallId") -> tdObj.getString("toolCallId")
                                                tdObj.has("id") -> tdObj.getString("id")
                                                tdObj.has("callId") -> tdObj.getString("callId")
                                                else -> null
                                            }
                                        } catch (_: Exception) { null }
                                    } else null
                                }
                                val tTitle = when {
                                    dObj.has("ToolTitle") && !dObj.isNull("ToolTitle") -> dObj.getString("ToolTitle")
                                    dObj.has("toolTitle") && !dObj.isNull("toolTitle") -> dObj.getString("toolTitle")
                                    else -> null
                                }
                                val rId = if (dObj.has("RequestId") && !dObj.isNull("RequestId")) dObj.getString("RequestId") else null
                                val desc = if (dObj.has("Description") && !dObj.isNull("Description")) dObj.getString("Description") else null
                                val err = if (dObj.has("Error") && !dObj.isNull("Error")) dObj.getString("Error") else null

                                val optionsList = ArrayList<com.pratyush.orbitmobile.domain.model.AiPermissionOption>()
                                if (dObj.has("Options") && !dObj.isNull("Options")) {
                                    val optArr = dObj.getJSONArray("Options")
                                    for (k in 0 until optArr.length()) {
                                        val o = optArr.getJSONObject(k)
                                        optionsList.add(
                                            com.pratyush.orbitmobile.domain.model.AiPermissionOption(
                                                optionId = o.getString("optionId"),
                                                name = o.getString("name"),
                                                kind = if (o.has("kind") && !o.isNull("kind")) o.getString("kind") else null
                                            )
                                        )
                                    }
                                }

                                onEvent(
                                    com.pratyush.orbitmobile.domain.model.AiStreamEvent(
                                        type = evType,
                                        conversationId = cId,
                                        messageId = mId,
                                        text = txt,
                                        toolCallId = tCallId,
                                        toolTitle = tTitle,
                                        toolName = tName,
                                        toolStatus = tStatus,
                                        toolDetail = tDetail,
                                        requestId = rId,
                                        description = desc,
                                        options = optionsList,
                                        error = err
                                    )
                                )
                            } catch (parseEx: Exception) {
                                android.util.Log.w("RemoteDataSource", "Failed to parse SSE JSON: $dataStr")
                            }
                        }
                    }
                    line = reader.readLine()
                }
                OrbitResult.Success(Unit)
            } else {
                OrbitResult.Error(Exception("HTTP $code streaming prompt"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun resolveAiPermission(
        host: String,
        port: Int,
        authToken: String,
        requestId: String,
        optionId: String
    ): OrbitResult<Boolean> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/mobile/ai/permission/resolve")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("X-Orbit-Token", authToken)
            }

            val body = JSONObject().apply {
                put("requestId", requestId)
                put("optionId", optionId)
            }
            conn.outputStream.bufferedWriter().use { it.write(body.toString()) }

            val code = conn.responseCode
            if (code == 200) {
                OrbitResult.Success(true)
            } else {
                OrbitResult.Error(Exception("HTTP $code resolving permission"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun setAiPolicy(
        host: String,
        port: Int,
        authToken: String,
        policy: String
    ): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/mobile/ai/policy")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("X-Orbit-Token", authToken)
            }

            val body = JSONObject().apply {
                put("policy", policy)
            }
            conn.outputStream.bufferedWriter().use { it.write(body.toString()) }

            val code = conn.responseCode
            if (code == 200) OrbitResult.Success(Unit)
            else OrbitResult.Error(Exception("HTTP $code setting AI policy"))
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun cancelAiPrompt(
        host: String,
        port: Int,
        authToken: String,
        conversationId: String
    ): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/mobile/ai/cancel")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("X-Orbit-Token", authToken)
            }

            val body = JSONObject().apply {
                put("conversationId", conversationId)
            }
            conn.outputStream.bufferedWriter().use { it.write(body.toString()) }

            val code = conn.responseCode
            if (code == 200) OrbitResult.Success(Unit)
            else OrbitResult.Error(Exception("HTTP $code cancelling prompt"))
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun initFileTransfer(
        host: String,
        port: Int,
        authToken: String,
        transferId: String,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        totalChunks: Int,
        chunkSize: Int,
        sha256: String
    ): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/mobile/files/init")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 6000
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("X-Orbit-Token", authToken)
            }

            val payload = JSONObject().apply {
                put("transferId", transferId)
                put("fileName", fileName)
                put("fileSize", fileSize)
                put("mimeType", mimeType)
                put("totalChunks", totalChunks)
                put("chunkSize", chunkSize)
                put("sha256", sha256)
            }

            conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }

            val code = conn.responseCode
            if (code in 200..299) {
                OrbitResult.Success(Unit)
            } else {
                val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                OrbitResult.Error(Exception("Failed to init transfer ($code): $errBody"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun sendFileChunk(
        host: String,
        port: Int,
        authToken: String,
        transferId: String,
        chunkIndex: Int,
        chunkSha256: String,
        chunkData: ByteArray,
        offset: Int,
        length: Int
    ): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/mobile/files/chunk")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 30000
                setFixedLengthStreamingMode(length)
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("X-Orbit-Token", authToken)
                setRequestProperty("X-Orbit-Transfer-Id", transferId)
                setRequestProperty("X-Orbit-Chunk-Index", chunkIndex.toString())
                setRequestProperty("X-Orbit-Chunk-Sha256", chunkSha256)
            }

            conn.outputStream.use { os ->
                os.write(chunkData, offset, length)
                os.flush()
            }

            val code = conn.responseCode
            if (code in 200..299) {
                OrbitResult.Success(Unit)
            } else {
                val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                OrbitResult.Error(Exception("Failed chunk $chunkIndex ($code): $errBody"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun completeFileTransfer(
        host: String,
        port: Int,
        authToken: String,
        transferId: String,
        sha256: String
    ): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/mobile/files/complete")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 6000
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("X-Orbit-Token", authToken)
            }

            val payload = JSONObject().apply {
                put("transferId", transferId)
                put("sha256", sha256)
            }

            conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }

            val code = conn.responseCode
            if (code in 200..299) {
                OrbitResult.Success(Unit)
            } else {
                val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                OrbitResult.Error(Exception("Failed complete transfer ($code): $errBody"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun cancelFileTransfer(
        host: String,
        port: Int,
        authToken: String,
        transferId: String
    ): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/mobile/files/cancel")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 4000
                readTimeout = 5000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("X-Orbit-Token", authToken)
            }

            val payload = JSONObject().apply {
                put("transferId", transferId)
            }

            conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }

            val code = conn.responseCode
            if (code in 200..299) {
                OrbitResult.Success(Unit)
            } else {
                OrbitResult.Error(Exception("Failed cancel transfer ($code)"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun fetchReceivedFiles(
        host: String,
        port: Int,
        authToken: String
    ): OrbitResult<List<com.pratyush.orbitmobile.domain.model.ReceivedRemoteFile>> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/mobile/files")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("X-Orbit-Token", authToken)
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val array = org.json.JSONArray(body)
                val list = mutableListOf<com.pratyush.orbitmobile.domain.model.ReceivedRemoteFile>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        com.pratyush.orbitmobile.domain.model.ReceivedRemoteFile(
                            id = obj.optString("id", ""),
                            fileName = obj.optString("fileName", ""),
                            fileSize = obj.optLong("fileSize", 0L),
                            mimeType = obj.optString("mimeType", ""),
                            receivedAtUtc = obj.optLong("receivedAtUtc", 0L),
                            temporaryPath = obj.optString("temporaryPath", "")
                        )
                    )
                }
                OrbitResult.Success(list)
            } else {
                OrbitResult.Error(Exception("Failed to fetch received files ($code)"))
            }
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }
}
