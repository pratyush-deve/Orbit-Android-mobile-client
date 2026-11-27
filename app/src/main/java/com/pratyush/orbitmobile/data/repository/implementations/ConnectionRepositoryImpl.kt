package com.pratyush.orbitmobile.data.repository.implementations

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.data.remote.RemoteDataSource
import com.pratyush.orbitmobile.domain.model.ClipboardItem
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.Device
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository
import com.pratyush.orbitmobile.domain.repository.DeviceRepository
import com.pratyush.orbitmobile.services.OrbitBackgroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import org.json.JSONObject
import java.net.URI
import java.util.UUID

/**
 * Repository orchestrating connection state and protocol commands with ORBIT Windows.
 */
class ConnectionRepositoryImpl(
    private val remoteDataSource: RemoteDataSource,
    private val deviceRepository: DeviceRepository,
    private val context: Context? = null
) : ConnectionRepository {

    companion object {
        private const val TAG_STATE = "ConnectionStateMachine"
        private const val MISSED_HEARTBEAT_THRESHOLD = 3
        private const val HEARTBEAT_INTERVAL_MS = 8000L
        private const val HEARTBEAT_GRACE_PERIOD_MS = 25000L
        private const val RECOVERY_THRESHOLD = 2
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _clipboardHistory = MutableStateFlow<List<ClipboardItem>>(emptyList())
    override val clipboardHistory: StateFlow<List<ClipboardItem>> = _clipboardHistory.asStateFlow()

    @Volatile
    private var _isExplicitlyDisconnected = false

    // Connection Epoch for session invalidation & discarding stale async responses
    private val connectionEpoch = AtomicLong(0L)

    // Dedicated heartbeat monitor state
    private var heartbeatJob: kotlinx.coroutines.Job? = null
    private var consecutiveHeartbeatFailures = 0
    private var consecutiveRecoverySuccesses = 0
    private var lastSuccessfulHeartbeatTime: Long = 0L

    init {
        loadSessionIdentity()
        loadHistoryFromDisk()
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        try {
            val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = android.net.NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    Log.i(TAG_STATE, "Wi-Fi became available. Performing background reconnection probe...")
                    if (!_isExplicitlyDisconnected && _connectionState.value !is ConnectionState.Connected) {
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            attemptReconnect(isBackgroundProbe = true)
                        }
                    }
                }

                override fun onLost(network: android.net.Network) {
                    Log.i(TAG_STATE, "Wi-Fi network lost.")
                    stopHeartbeatMonitor()
                    if (_connectionState.value is ConnectionState.Connected) {
                        val devName = (_connectionState.value as? ConnectionState.Connected)?.deviceName ?: "PC"
                        val newState = ConnectionState.TemporarilyUnavailable(devName, "Wi-Fi disconnected")
                        logStateTransition(
                            prev = _connectionState.value,
                            new = newState,
                            reason = "Wi-Fi lost",
                            epoch = connectionEpoch.get(),
                            source = "NetworkCallback"
                        )
                        _connectionState.value = newState
                    }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG_STATE, "Failed to register network callback: ${e.message}")
        }
    }

    private fun logStateTransition(
        prev: ConnectionState,
        new: ConnectionState,
        reason: String,
        epoch: Long,
        source: String
    ) {
        val prevStr = when (prev) {
            is ConnectionState.Connected -> "Connected(${prev.deviceName})"
            is ConnectionState.Connecting -> "Connecting(${prev.deviceName})"
            is ConnectionState.TemporarilyUnavailable -> "TemporarilyUnavailable(${prev.reason})"
            is ConnectionState.Disconnected -> "Disconnected"
            is ConnectionState.Error -> "Error(${prev.message})"
            is ConnectionState.Discovering -> "Discovering"
        }
        val newStr = when (new) {
            is ConnectionState.Connected -> "Connected(${new.deviceName})"
            is ConnectionState.Connecting -> "Connecting(${new.deviceName})"
            is ConnectionState.TemporarilyUnavailable -> "TemporarilyUnavailable(${new.reason})"
            is ConnectionState.Disconnected -> "Disconnected"
            is ConnectionState.Error -> "Error(${new.message})"
            is ConnectionState.Discovering -> "Discovering"
        }
        Log.i(TAG_STATE, "[ConnectionStateMachine] EPOCH=$epoch PREV=$prevStr NEW=$newStr REASON=\"$reason\" SOURCE=$source")
    }

    private fun startHeartbeatMonitor() {
        heartbeatJob?.cancel()
        val currentEpoch = connectionEpoch.get()
        heartbeatJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            Log.i(TAG_STATE, "Starting dedicated heartbeat monitor (epoch=$currentEpoch, interval=${HEARTBEAT_INTERVAL_MS}ms)")
            while (isActive && currentEpoch == connectionEpoch.get()) {
                kotlinx.coroutines.delay(HEARTBEAT_INTERVAL_MS)
                if (_isExplicitlyDisconnected || _connectionState.value is ConnectionState.Disconnected) {
                    break
                }
                performHeartbeatCheck(currentEpoch)
            }
        }
    }

    private fun stopHeartbeatMonitor() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private suspend fun performHeartbeatCheck(epoch: Long) = withContext(Dispatchers.IO) {
        if (epoch != connectionEpoch.get() || _isExplicitlyDisconnected) return@withContext

        val host = _host ?: run {
            val paired = deviceRepository.getPairedDevices().firstOrNull()?.firstOrNull()
            paired?.ipAddress
        } ?: return@withContext

        val port = _port
        Log.d(TAG_STATE, "[Heartbeat] Sending ping to $host:$port (epoch=$epoch)")
        val pingResult = remoteDataSource.ping(host, port)

        if (epoch != connectionEpoch.get() || _isExplicitlyDisconnected) {
            Log.d(TAG_STATE, "Discarding heartbeat result from stale epoch $epoch (current=${connectionEpoch.get()})")
            return@withContext
        }

        val currentState = _connectionState.value

        when (pingResult) {
            is OrbitResult.Success -> {
                val latency = pingResult.data
                lastSuccessfulHeartbeatTime = System.currentTimeMillis()
                consecutiveHeartbeatFailures = 0
                Log.d(TAG_STATE, "[Heartbeat] Acknowledged by $host:$port (${latency}ms)")

                when (currentState) {
                    is ConnectionState.Connected -> {
                        consecutiveRecoverySuccesses = 0
                        if (abs(currentState.latencyMs - latency) > 10) {
                            _connectionState.value = currentState.copy(latencyMs = latency)
                        }
                    }
                    is ConnectionState.TemporarilyUnavailable, is ConnectionState.Connecting -> {
                        consecutiveRecoverySuccesses++
                        Log.d(TAG_STATE, "[Heartbeat] Recovery check passed ($consecutiveRecoverySuccesses/$RECOVERY_THRESHOLD) latency=${latency}ms")
                        if (consecutiveRecoverySuccesses >= RECOVERY_THRESHOLD) {
                            val devName = when (currentState) {
                                is ConnectionState.TemporarilyUnavailable -> currentState.deviceName
                                is ConnectionState.Connecting -> currentState.deviceName
                                else -> "ORBIT Windows PC"
                            }
                            val newState = ConnectionState.Connected(
                                deviceId = "orbit-$host",
                                deviceName = devName,
                                host = host,
                                port = port,
                                connectionType = "LAN Link",
                                latencyMs = latency,
                                isClipboardSyncEnabled = true
                            )
                            logStateTransition(
                                prev = currentState,
                                new = newState,
                                reason = "Stable recovery confirmed ($RECOVERY_THRESHOLD consecutive successful heartbeats)",
                                epoch = epoch,
                                source = "HeartbeatMonitor"
                            )
                            _connectionState.value = newState
                            consecutiveRecoverySuccesses = 0
                        }
                    }
                    else -> Unit
                }
            }
            is OrbitResult.Error -> {
                consecutiveRecoverySuccesses = 0
                consecutiveHeartbeatFailures++
                val elapsedSinceSuccess = System.currentTimeMillis() - lastSuccessfulHeartbeatTime
                Log.w(
                    TAG_STATE,
                    "[Heartbeat] Missed check ($consecutiveHeartbeatFailures/$MISSED_HEARTBEAT_THRESHOLD). Elapsed: ${elapsedSinceSuccess / 1000}s. Error: ${pingResult.exception.message}"
                )

                if (currentState is ConnectionState.Connected) {
                    if (consecutiveHeartbeatFailures >= MISSED_HEARTBEAT_THRESHOLD && elapsedSinceSuccess >= HEARTBEAT_GRACE_PERIOD_MS) {
                        val devName = currentState.deviceName
                        val reason = "Host unreachable on LAN ($consecutiveHeartbeatFailures missed heartbeats, ${elapsedSinceSuccess / 1000}s elapsed)"
                        val newState = ConnectionState.TemporarilyUnavailable(devName, reason)
                        logStateTransition(
                            prev = currentState,
                            new = newState,
                            reason = reason,
                            epoch = epoch,
                            source = "HeartbeatMonitor"
                        )
                        _connectionState.value = newState
                        // Launch background rediscovery without flipping to Connecting
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            attemptReconnect(isBackgroundProbe = true)
                        }
                    }
                }
            }
            else -> Unit
        }
    }

    private fun loadHistoryFromDisk() {
        try {
            val file = context?.let { java.io.File(it.filesDir, "clipboard_history.json") } ?: return
            if (!file.exists()) return
            val jsonStr = file.readText(Charsets.UTF_8)
            if (jsonStr.isBlank()) return
            val jsonArr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<ClipboardItem>()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val content = obj.optString("content", "")
                if (content.isNotBlank()) {
                    list.add(
                        ClipboardItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            type = obj.optString("type", "Text"),
                            content = content,
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            origin = obj.optString("origin", "Phone"),
                            contentHash = obj.optString("contentHash", ClipboardItem.computeHash(content)),
                            deviceId = obj.optString("deviceId", "")
                        )
                    )
                }
            }
            _clipboardHistory.value = list
            Log.i("ConnectionRepo", "Loaded ${list.size} clipboard items from disk persistence.")
        } catch (e: Exception) {
            Log.w("ConnectionRepo", "Failed to load clipboard history from disk: ${e.message}")
        }
    }

    private fun saveHistoryToDisk(items: List<ClipboardItem>) {
        try {
            val file = context?.let { java.io.File(it.filesDir, "clipboard_history.json") } ?: return
            val jsonArr = org.json.JSONArray()
            for (item in items) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("type", item.type)
                    put("content", item.content)
                    put("timestamp", item.timestamp)
                    put("origin", item.origin)
                    put("contentHash", item.contentHash)
                    put("deviceId", item.deviceId)
                }
                jsonArr.put(obj)
            }
            file.writeText(jsonArr.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w("ConnectionRepo", "Failed to persist clipboard history to disk: ${e.message}")
        }
    }

    private var _authToken: String? = null
    override val activeAuthToken: String? get() = _authToken

    private var _host: String? = null
    override val activeHost: String? get() = _host

    private var _port: Int = 8765
    override val activePort: Int get() = _port

    private val clientDeviceId: String by lazy {
        val prefs = context?.getSharedPreferences("orbit_identity", Context.MODE_PRIVATE)
        val savedId = prefs?.getString("client_device_id", null)
        if (!savedId.isNullOrBlank()) {
            savedId
        } else {
            val newId = UUID.randomUUID().toString()
            prefs?.edit()?.putString("client_device_id", newId)?.apply()
            newId
        }
    }

    private fun loadSessionIdentity() {
        try {
            val prefs = context?.getSharedPreferences("orbit_identity", Context.MODE_PRIVATE) ?: return
            val savedToken = prefs.getString("auth_token", null)
            val savedHost = prefs.getString("last_host", null)
            val savedPort = prefs.getInt("last_port", 8765)
            if (!savedToken.isNullOrBlank()) _authToken = savedToken
            if (!savedHost.isNullOrBlank()) _host = savedHost
            if (savedPort in 1..65535) _port = savedPort
            Log.i(TAG_STATE, "Loaded persisted session identity (host=${_host ?: "none"}, port=$_port, hasToken=${!_authToken.isNullOrBlank()})")
        } catch (e: Exception) {
            Log.w(TAG_STATE, "Failed to load session identity: ${e.message}")
        }
    }

    private fun saveSessionIdentity(host: String, port: Int, authToken: String?) {
        try {
            val prefs = context?.getSharedPreferences("orbit_identity", Context.MODE_PRIVATE) ?: return
            prefs.edit().apply {
                putString("last_host", host)
                putInt("last_port", port)
                if (!authToken.isNullOrBlank()) {
                    putString("auth_token", authToken)
                }
                apply()
            }
        } catch (e: Exception) {
            Log.w(TAG_STATE, "Failed to save session identity: ${e.message}")
        }
    }

    override suspend fun pairWithQr(qrRawPayload: String): OrbitResult<Device> {
        _connectionState.value = ConnectionState.Connecting("ORBIT Windows", "Negotiating QR Pairing...")
        return try {
            val json = JSONObject(qrRawPayload)
            val version = json.optInt("v", json.optInt("Version", 1))
            val sessionId = json.optString("s", json.optString("SessionId", json.optString("sessionId", "")))
            val rawEndpoint = json.optString("ep", json.optString("Endpoint", json.optString("endpoint", "")))
            val nonce = json.optString("n", json.optString("Nonce", json.optString("nonce", "")))
            val fingerprint = json.optString("fp", json.optString("Fingerprint", json.optString("fingerprint", "")))
            val expiresAtUnix = json.optLong("exp", json.optLong("ExpiresAtUnix", 0L))

            if (sessionId.isBlank() || rawEndpoint.isBlank()) {
                val err = "Invalid QR payload format"
                _connectionState.value = ConnectionState.Error(err)
                return OrbitResult.Error(IllegalArgumentException(err))
            }

            if (expiresAtUnix > 0) {
                val nowUnix = System.currentTimeMillis() / 1000
                if (nowUnix > expiresAtUnix) {
                    val err = "QR code has expired. Please refresh the QR code on your PC."
                    _connectionState.value = ConnectionState.Error(err)
                    return OrbitResult.Error(IllegalStateException(err))
                }
            }

            val endpoint = if (rawEndpoint.contains("/api/mobile/pair")) {
                rawEndpoint
            } else {
                "${rawEndpoint.trimEnd('/')}/api/mobile/pair"
            }

            val uri = URI(endpoint)
            val host = uri.host ?: "127.0.0.1"
            val port = if (uri.port > 0) uri.port else 8765

            _host = host
            _port = port

            // Verify Wi-Fi network precondition for LAN connection
            context?.let { ctx ->
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNetwork = cm?.activeNetwork
                val caps = cm?.getNetworkCapabilities(activeNetwork)
                val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                             caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

                if (!isWifi) {
                    val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                    val errMsg = if (isCellular) {
                        "Your phone is currently using cellular data. LAN pairing requires both your phone and PC to be on the same Wi-Fi network. Please turn on Wi-Fi and connect to your local network."
                    } else {
                        "No Wi-Fi connection detected. Please connect your phone to the same Wi-Fi network as your PC ($host) to pair."
                    }
                    Log.w("ConnectionRepo", "Pairing rejected by precondition: $errMsg")
                    _connectionState.value = ConnectionState.Error(errMsg)
                    return OrbitResult.Error(IllegalStateException(errMsg))
                }
            }

            val clientName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
            val clientModel = Build.MODEL
            val osVersion = "Android ${Build.VERSION.RELEASE}"
            val appVersion = "ORBIT Mobile v1.0"

            val pairResult = remoteDataSource.requestPairing(
                endpointUrl = endpoint,
                sessionId = sessionId,
                nonce = nonce,
                clientDeviceId = clientDeviceId,
                clientDeviceName = clientName,
                clientDeviceModel = clientModel,
                osVersion = osVersion,
                appVersion = appVersion
            )

            when (pairResult) {
                is OrbitResult.Success -> {
                    val data = pairResult.data
                    _authToken = data.authToken
                    saveSessionIdentity(host, port, data.authToken)

                    val pingLatency = (remoteDataSource.ping(host, port) as? OrbitResult.Success)?.data ?: 8L

                    val pairedDevice = Device(
                        id = data.serverDeviceId.ifBlank { "orbit-desktop-$host" },
                        name = data.serverDeviceName.ifBlank { "ORBIT Windows PC" },
                        ipAddress = host,
                        port = port,
                        isPaired = true,
                        lastConnectedText = "Just now",
                        osInfo = "Windows 11",
                        isOnline = true
                    )

                    val epoch = connectionEpoch.incrementAndGet()
                    lastSuccessfulHeartbeatTime = System.currentTimeMillis()
                    consecutiveHeartbeatFailures = 0
                    consecutiveRecoverySuccesses = 0

                    _isExplicitlyDisconnected = false
                    deviceRepository.saveDevice(pairedDevice)

                    val newState = ConnectionState.Connected(
                        deviceId = pairedDevice.id,
                        deviceName = pairedDevice.name,
                        host = host,
                        port = port,
                        connectionType = "LAN (Wi-Fi)",
                        latencyMs = pingLatency,
                        isClipboardSyncEnabled = true
                    )
                    logStateTransition(_connectionState.value, newState, "QR pairing successful", epoch, "pairWithQr")
                    _connectionState.value = newState
                    startHeartbeatMonitor()
                    context?.let { OrbitBackgroundService.start(it, pairedDevice.name) }

                    OrbitResult.Success(pairedDevice)
                }
                is OrbitResult.Error -> {
                    val msg = pairResult.exception.message ?: "Pairing declined"
                    _connectionState.value = ConnectionState.Error(msg)
                    OrbitResult.Error(pairResult.exception)
                }
                else -> OrbitResult.Loading
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Failed to parse pairing QR: ${e.localizedMessage}")
            OrbitResult.Error(e)
        }
    }

    override suspend fun connectToDevice(device: Device): OrbitResult<Unit> {
        _connectionState.value = ConnectionState.Connecting(device.name, device.ipAddress)
        return try {
            val pingResult = remoteDataSource.ping(device.ipAddress, device.port)
            when (pingResult) {
                is OrbitResult.Success -> {
                    val latency = pingResult.data
                    _host = device.ipAddress
                    _port = device.port

                    val clientName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
                    val tok = _authToken ?: "anonymous"
                    val reconnectRes = remoteDataSource.reconnect(device.ipAddress, device.port, tok, clientDeviceId, clientName)
                    if (reconnectRes is OrbitResult.Success) {
                        _authToken = reconnectRes.data.authToken
                    }
                    saveSessionIdentity(device.ipAddress, device.port, _authToken)

                    val epoch = connectionEpoch.incrementAndGet()
                    lastSuccessfulHeartbeatTime = System.currentTimeMillis()
                    consecutiveHeartbeatFailures = 0
                    consecutiveRecoverySuccesses = 0

                    _isExplicitlyDisconnected = false
                    val newState = ConnectionState.Connected(
                        deviceId = device.id,
                        deviceName = device.name,
                        host = device.ipAddress,
                        port = device.port,
                        connectionType = "LAN Direct Link",
                        latencyMs = latency,
                        isClipboardSyncEnabled = true
                    )
                    logStateTransition(_connectionState.value, newState, "Device connected", epoch, "connectToDevice")
                    _connectionState.value = newState
                    startHeartbeatMonitor()
                    context?.let { OrbitBackgroundService.start(it, device.name) }
                    OrbitResult.Success(Unit)
                }
                is OrbitResult.Error -> {
                    _connectionState.value = ConnectionState.TemporarilyUnavailable(device.name, "Host unreachable: ${device.ipAddress}:${device.port}")
                    OrbitResult.Error(pingResult.exception)
                }
                else -> OrbitResult.Loading
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Failed to connect: ${e.localizedMessage}")
            OrbitResult.Error(e)
        }
    }

    override suspend fun connectManual(host: String, port: Int): OrbitResult<Unit> {
        _connectionState.value = ConnectionState.Connecting("ORBIT PC ($host)", host)
        return try {
            val pingResult = remoteDataSource.ping(host, port)
            when (pingResult) {
                is OrbitResult.Success -> {
                    val latency = pingResult.data
                    _host = host
                    _port = port

                    val infoResult = remoteDataSource.fetchRemoteDeviceInfo(host, port)
                    val devName = (infoResult as? OrbitResult.Success)?.data?.name ?: "ORBIT Windows PC"

                    val device = Device(
                        id = "manual-$host",
                        name = devName,
                        ipAddress = host,
                        port = port,
                        isPaired = true,
                        lastConnectedText = "Just now",
                        osInfo = "Windows 11",
                        isOnline = true
                    )
                    deviceRepository.saveDevice(device)

                    val epoch = connectionEpoch.incrementAndGet()
                    lastSuccessfulHeartbeatTime = System.currentTimeMillis()
                    consecutiveHeartbeatFailures = 0
                    consecutiveRecoverySuccesses = 0

                    _isExplicitlyDisconnected = false
                    val newState = ConnectionState.Connected(
                        deviceId = device.id,
                        deviceName = device.name,
                        host = host,
                        port = port,
                        connectionType = "Direct LAN Link",
                        latencyMs = latency,
                        isClipboardSyncEnabled = true
                    )
                    logStateTransition(_connectionState.value, newState, "Manual connection verified", epoch, "connectManual")
                    _connectionState.value = newState
                    startHeartbeatMonitor()
                    context?.let { OrbitBackgroundService.start(it, device.name) }
                    OrbitResult.Success(Unit)
                }
                is OrbitResult.Error -> {
                    _connectionState.value = ConnectionState.TemporarilyUnavailable("ORBIT PC ($host)", "Host unreachable: $host:$port")
                    OrbitResult.Error(pingResult.exception)
                }
                else -> OrbitResult.Loading
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Connection timed out: ${e.localizedMessage}")
            OrbitResult.Error(e)
        }
    }

    override suspend fun disconnect(): OrbitResult<Unit> {
        val epoch = connectionEpoch.incrementAndGet()
        _isExplicitlyDisconnected = true
        stopHeartbeatMonitor()
        context?.let { OrbitBackgroundService.stop(it) }

        // Immediately update state so UI updates without waiting for network round-trip
        logStateTransition(_connectionState.value, ConnectionState.Disconnected, "User requested disconnect", epoch, "disconnect")
        _connectionState.value = ConnectionState.Disconnected

        val h = _host
        val p = _port
        val tok = _authToken
        if (h != null && tok != null) {
            try {
                withContext(Dispatchers.IO) {
                    remoteDataSource.disconnect(h, p, tok)
                }
            } catch (e: Exception) {
                Log.w(TAG_STATE, "Error delivering disconnect notification to Windows: ${e.message}")
            }
        }
        return OrbitResult.Success(Unit)
    }

    override suspend fun sendPing(): OrbitResult<Long> {
        return when (val state = _connectionState.value) {
            is ConnectionState.Connected -> remoteDataSource.ping(state.host, state.port)
            else -> OrbitResult.Error(IllegalStateException("Not connected"))
        }
    }

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
    ): OrbitResult<Unit> {
        val h = _host ?: return OrbitResult.Error(IllegalStateException("Not connected"))
        val tok = _authToken ?: "anonymous"
        // Dispatches telemetry payload. TELEMETRY FAILURES NEVER MUTATE _connectionState!
        return remoteDataSource.sendTelemetry(
            host = h,
            port = _port,
            authToken = tok,
            clientDeviceId = clientDeviceId,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            batteryStatusText = batteryStatusText,
            batteryHealth = batteryHealth,
            batteryTemperature = batteryTemperature,
            chargeSource = chargeSource,
            wifiConnected = wifiConnected,
            wifiSsid = wifiSsid,
            wifiFrequency = wifiFrequency,
            wifiLinkSpeed = wifiLinkSpeed,
            bluetoothEnabled = bluetoothEnabled,
            bluetoothConnected = bluetoothConnected,
            bluetoothStatusText = bluetoothStatusText,
            usedStorageGb = usedStorageGb,
            totalStorageGb = totalStorageGb,
            manufacturer = manufacturer
        )
    }

    override suspend fun sendClipboard(
        type: String,
        textContent: String?,
        urlContent: String?,
        imageBase64: String?,
        imageFileName: String?
    ): OrbitResult<Unit> {
        val h = _host ?: return OrbitResult.Error(IllegalStateException("Not connected"))
        val tok = _authToken ?: "anonymous"
        val clientName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        val content = textContent ?: urlContent ?: ""
        if (content.isBlank()) return OrbitResult.Error(IllegalArgumentException("Empty content"))

        val item = ClipboardItem(
            type = type,
            content = content,
            origin = "Phone",
            deviceId = clientDeviceId
        )

        val result = remoteDataSource.sendClipboard(
            host = h,
            port = _port,
            authToken = tok,
            clientDeviceId = clientDeviceId,
            type = type,
            textContent = textContent,
            urlContent = urlContent,
            imageBase64 = imageBase64,
            imageFileName = imageFileName,
            deviceOrigin = clientName,
            id = item.id,
            timestamp = item.timestamp,
            contentHash = item.contentHash
        )
        if (result is OrbitResult.Success) {
            _clipboardHistory.update { list ->
                val filtered = list.filterNot { it.id == item.id || it.contentHash == item.contentHash }
                val updated = (listOf(item) + filtered).take(50)
                saveHistoryToDisk(updated)
                updated
            }
        }
        return result
    }

    override suspend fun updateClipboardSyncState(enabled: Boolean): OrbitResult<Unit> {
        val h = _host ?: return OrbitResult.Error(IllegalStateException("Not connected"))
        val tok = _authToken ?: "anonymous"
        return remoteDataSource.updateClipboardSyncState(h, _port, tok, enabled)
    }

    override suspend fun clearClipboardHistory(): OrbitResult<Unit> {
        _clipboardHistory.value = emptyList()
        saveHistoryToDisk(emptyList())
        return OrbitResult.Success(Unit)
    }

    override fun receiveIncomingClipboardFromWindows(
        id: String,
        type: String,
        content: String,
        timestamp: Long,
        origin: String
    ) {
        if (content.isBlank()) return
        val hash = ClipboardItem.computeHash(content)
        val item = ClipboardItem(
            id = if (id.isNotBlank()) id else java.util.UUID.randomUUID().toString(),
            type = type,
            content = content,
            timestamp = if (timestamp > 0) timestamp else System.currentTimeMillis(),
            origin = origin,
            contentHash = hash
        )

        _clipboardHistory.update { list ->
            val filtered = list.filterNot {
                it.id == item.id || (it.contentHash == item.contentHash && Math.abs(it.timestamp - item.timestamp) < 3000)
            }
            val updated = (listOf(item) + filtered).take(50)
            saveHistoryToDisk(updated)
            updated
        }
        Log.i("ConnectionRepo", "Added and persisted clipboard item from $origin: ${item.preview}")
    }

    override suspend fun refreshClipboardHistoryFromPc(): OrbitResult<Unit> {
        val h = _host ?: return OrbitResult.Error(IllegalStateException("Not connected"))
        val tok = _authToken ?: return OrbitResult.Error(IllegalStateException("No auth token"))
        val result = remoteDataSource.fetchClipboardHistory(h, _port, tok)
        if (result is OrbitResult.Success) {
            val pcItems = result.data
            if (pcItems.isNotEmpty()) {
                _clipboardHistory.update { current ->
                    val combined = pcItems + current
                    val deduplicated = mutableListOf<ClipboardItem>()
                    val seenIds = mutableSetOf<String>()
                    val seenHashes = mutableSetOf<String>()
                    for (item in combined.sortedByDescending { it.timestamp }) {
                        if (item.id.isNotBlank() && !seenIds.add(item.id)) continue
                        if (item.contentHash.isNotBlank() && !seenHashes.add(item.contentHash)) continue
                        deduplicated.add(item)
                        if (deduplicated.size >= 50) break
                    }
                    saveHistoryToDisk(deduplicated)
                    deduplicated
                }
            }
            return OrbitResult.Success(Unit)
        } else if (result is OrbitResult.Error) {
            return OrbitResult.Error(result.exception)
        }
        return OrbitResult.Success(Unit)
    }

    private val reconnectMutex = Mutex()

    override suspend fun attemptReconnect(): OrbitResult<Unit> {
        _isExplicitlyDisconnected = false
        return attemptReconnect(isBackgroundProbe = false)
    }

    suspend fun attemptReconnect(isBackgroundProbe: Boolean): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        if (isBackgroundProbe) {
            if (!reconnectMutex.tryLock()) {
                Log.d(TAG_STATE, "Reconnect coordinator already active, skipping duplicate background probe.")
                return@withContext OrbitResult.Success(Unit)
            }
        } else {
            reconnectMutex.lock()
        }
        val epoch = connectionEpoch.get()
        try {
            if (isBackgroundProbe && _isExplicitlyDisconnected) {
                Log.d(TAG_STATE, "Skipping background reconnect probe: device was explicitly disconnected.")
                return@withContext OrbitResult.Success(Unit)
            }
            if (!isBackgroundProbe) {
                _isExplicitlyDisconnected = false
            }

            // If already connected and this is a background probe, do NOT disturb the connection
            val currentState = _connectionState.value
            if (currentState is ConnectionState.Connected && isBackgroundProbe) {
                return@withContext OrbitResult.Success(Unit)
            }

            // If already Connected and user explicitly tapped Reconnect, verify health first
            if (currentState is ConnectionState.Connected && !isBackgroundProbe) {
                val h = _host ?: currentState.host
                val p = _port
                Log.i(TAG_STATE, "Manual reconnect: verifying existing connection to $h:$p...")
                val pingCheck = remoteDataSource.ping(h, p)
                if (pingCheck is OrbitResult.Success) {
                    val clientName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
                    val reconnectRes = remoteDataSource.reconnect(h, p, _authToken ?: "anonymous", clientDeviceId, clientName)
                    if (reconnectRes is OrbitResult.Success) {
                        _authToken = reconnectRes.data.authToken
                        saveSessionIdentity(h, p, _authToken)
                    }
                    lastSuccessfulHeartbeatTime = System.currentTimeMillis()
                    consecutiveHeartbeatFailures = 0
                    consecutiveRecoverySuccesses = 0
                    _connectionState.value = currentState.copy(latencyMs = pingCheck.data)
                    startHeartbeatMonitor()
                    context?.let { OrbitBackgroundService.requestImmediateTelemetry(it) }
                    Log.i(TAG_STATE, "Manual reconnect: existing connection verified (${pingCheck.data}ms)")
                    return@withContext OrbitResult.Success(Unit)
                } else {
                    Log.w(TAG_STATE, "Manual reconnect: existing connection is stale, re-establishing...")
                    stopHeartbeatMonitor()
                    _connectionState.value = ConnectionState.Connecting(currentState.deviceName, "Re-establishing connection...")
                }
            }

            val pairedDevices = deviceRepository.getPairedDevices().firstOrNull() ?: emptyList()
            val targetDevice = pairedDevices.firstOrNull() ?: run {
                return@withContext OrbitResult.Error(IllegalStateException("No paired devices to reconnect to"))
            }

            val retryDelays = listOf(1000L, 2000L, 4000L, 8000L, 16000L)
            val clientName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

            for (delayMs in retryDelays) {
                if (epoch != connectionEpoch.get() || (isBackgroundProbe && _isExplicitlyDisconnected)) {
                    return@withContext OrbitResult.Success(Unit)
                }
                if (_connectionState.value is ConnectionState.Connected) {
                    return@withContext OrbitResult.Success(Unit)
                }

                // Only set Connecting if NOT already Connected (Requirement 1)
                if (_connectionState.value !is ConnectionState.Connected && !isBackgroundProbe) {
                    _connectionState.value = ConnectionState.Connecting(targetDevice.name, "Reconnecting (${delayMs / 1000}s retry)...")
                }

                val tok = _authToken ?: "anonymous"
                val host = _host ?: targetDevice.ipAddress
                val port = _port

                var reconnectResult = remoteDataSource.reconnect(host, port, tok, clientDeviceId, clientName)
                if (reconnectResult is OrbitResult.Success && epoch == connectionEpoch.get()) {
                    val pingCheck = remoteDataSource.ping(host, port)
                    if (pingCheck is OrbitResult.Success) {
                        _authToken = reconnectResult.data.authToken
                        _host = host
                        _port = port
                        saveSessionIdentity(host, port, _authToken)
                        _isExplicitlyDisconnected = false
                        lastSuccessfulHeartbeatTime = System.currentTimeMillis()
                        consecutiveHeartbeatFailures = 0
                        consecutiveRecoverySuccesses = 0

                        val newState = ConnectionState.Connected(
                            deviceId = reconnectResult.data.serverDeviceId.ifBlank { targetDevice.id },
                            deviceName = reconnectResult.data.serverDeviceName.ifBlank { targetDevice.name },
                            host = host,
                            port = port,
                            connectionType = "LAN (Auto-Reconnected)",
                            latencyMs = pingCheck.data,
                            isClipboardSyncEnabled = true
                        )
                        logStateTransition(_connectionState.value, newState, "Reconnection and ping verified", epoch, "ReconnectCoordinator")
                        _connectionState.value = newState
                        startHeartbeatMonitor()
                        context?.let { ctx ->
                            OrbitBackgroundService.requestImmediateTelemetry(ctx)
                        }
                        return@withContext OrbitResult.Success(Unit)
                    }
                }

                val probedEndpoint = discoverViaUdp()
                if (probedEndpoint != null && epoch == connectionEpoch.get()) {
                    val (probedHost, probedPort) = probedEndpoint
                    reconnectResult = remoteDataSource.reconnect(probedHost, probedPort, tok, clientDeviceId, clientName)
                    if (reconnectResult is OrbitResult.Success && epoch == connectionEpoch.get()) {
                        val pingCheck = remoteDataSource.ping(probedHost, probedPort)
                        if (pingCheck is OrbitResult.Success) {
                            _authToken = reconnectResult.data.authToken
                            _host = probedHost
                            _port = probedPort
                            saveSessionIdentity(probedHost, probedPort, _authToken)
                            _isExplicitlyDisconnected = false
                            lastSuccessfulHeartbeatTime = System.currentTimeMillis()
                            consecutiveHeartbeatFailures = 0
                            consecutiveRecoverySuccesses = 0

                            val newState = ConnectionState.Connected(
                                deviceId = reconnectResult.data.serverDeviceId.ifBlank { targetDevice.id },
                                deviceName = reconnectResult.data.serverDeviceName.ifBlank { targetDevice.name },
                                host = probedHost,
                                port = probedPort,
                                connectionType = "LAN (Discovered)",
                                latencyMs = pingCheck.data,
                                isClipboardSyncEnabled = true
                            )
                            logStateTransition(_connectionState.value, newState, "UDP Rediscovery and ping verified", epoch, "ReconnectCoordinator")
                            _connectionState.value = newState
                            startHeartbeatMonitor()
                            context?.let { ctx ->
                                OrbitBackgroundService.requestImmediateTelemetry(ctx)
                            }
                            return@withContext OrbitResult.Success(Unit)
                        }
                    }
                }

                delay(delayMs)
            }

            if (epoch == connectionEpoch.get() && _connectionState.value !is ConnectionState.Connected) {
                val newState = if (isBackgroundProbe) {
                    ConnectionState.TemporarilyUnavailable(targetDevice.name, "Laptop unreachable on local network")
                } else {
                    ConnectionState.Disconnected
                }
                logStateTransition(_connectionState.value, newState, "All reconnect attempts failed", epoch, "ReconnectCoordinator")
                _connectionState.value = newState
            }
            OrbitResult.Error(Exception("Auto-reconnection timed out after maximum attempts."))
        } finally {
            reconnectMutex.unlock()
        }
    }

    private fun discoverViaUdp(): Pair<String, Int>? {
        return try {
            val socket = java.net.DatagramSocket().apply {
                broadcast = true
                soTimeout = 1500
            }
            val probeBytes = "ORBIT_DISCOVERY_PROBE".toByteArray(Charsets.UTF_8)
            val packet = java.net.DatagramPacket(
                probeBytes,
                probeBytes.size,
                java.net.InetAddress.getByName("255.255.255.255"),
                8766
            )
            socket.send(packet)

            val buf = ByteArray(1024)
            val responsePacket = java.net.DatagramPacket(buf, buf.size)
            socket.receive(responsePacket)
            socket.close()

            val msg = String(responsePacket.data, 0, responsePacket.length, Charsets.UTF_8)
            if (msg.startsWith("ORBIT_DISCOVERY_RESP")) {
                val parts = msg.split("|")
                if (parts.size >= 4) {
                    val ip = parts[2].trim()
                    val port = parts[3].trim().toIntOrNull() ?: 8765
                    Pair(ip, port)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
